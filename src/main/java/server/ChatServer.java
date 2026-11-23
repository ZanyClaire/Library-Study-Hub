package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import common.AppConfig;
import common.Protocol;

/**
 * Server core (no GUI code here). Owns all shared state:
 *  - connected students (all equal, monitored by the Librarian Server Console)
 *  - Feature 1: per-student study session timers (require an approved seat, synced live across all clients)
 *  - Feature 2: study seats requested by students and approved/denied by the Librarian (Server)
 * Every state change happens inside synchronized(this) so clients always see
 * one consistent order of updates.
 */
public class ChatServer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String LIBRARIAN_NAME = "Librarian";

    private final int port;
    private final ServerListener listener;
    private final Map<String, ClientHandler> clients = new LinkedHashMap<>(); // key = lower-case nick
    private final Seat[] seats;

    private ServerSocket serverSocket;
    private ExecutorService clientPool;
    private ScheduledExecutorService ticker;
    private volatile boolean running;

    // ---- per-student session timer state ----
    private static final class StudentTimer {
        String state = "IDLE";   // IDLE, RUNNING, PAUSED, FINISHED
        int remaining;
        int total;
        String label = "";
    }

    private final Map<ClientHandler, StudentTimer> timers = new LinkedHashMap<>();

    public ChatServer(int port, int seatCount, ServerListener listener) {
        this.port = port;
        this.listener = listener;
        int count = Math.max(1, seatCount);
        this.seats = new Seat[count];
        for (int i = 0; i < count; i++) {
            seats[i] = new Seat();
        }
    }

    // =====================================================================
    // Start / stop
    // =====================================================================

    /** Opens the port (throws if it is in use) and starts accepting clients. */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        clientPool = Executors.newCachedThreadPool();
        ticker = Executors.newSingleThreadScheduledExecutor();
        ticker.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop, "accept-thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
        listener.log("Server started on port " + port + " with " + seats.length + " study seats.");
        notifySeats();
        notifyStudents();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                listener.log("Connection from " + socket.getRemoteSocketAddress());
                clientPool.execute(new ClientHandler(socket, this));
            } catch (SocketException e) {
                break; // server socket closed by stop()
            } catch (IOException e) {
                listener.log("Accept error: " + e.getMessage());
            }
        }
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        synchronized (this) {
            broadcast(Protocol.encode(Protocol.BYE, "Server is shutting down."));
            for (ClientHandler c : new ArrayList<>(clients.values())) {
                c.close();
            }
            clients.clear();
            timers.clear();
            for (Seat s : seats) {
                s.free();
            }
        }
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        ticker.shutdownNow();
        clientPool.shutdownNow();
        listener.log("Server stopped.");
        listener.studentsChanged(List.of());
        listener.seatsChanged(new Seat.Status[0], new String[0]);
        listener.serverStopped();
    }

    public boolean isRunning() {
        return running;
    }

    // =====================================================================
    // Users
    // =====================================================================

    /** @return null when accepted, otherwise the reason for rejection. */
    synchronized String register(ClientHandler client, String nick) {
        String error = validateNickname(nick);
        if (error != null) {
            return error;
        }
        clients.put(nick.toLowerCase(), client);
        timers.put(client, new StudentTimer());
        client.setNickname(nick);
        client.send(Protocol.encode(Protocol.WELCOME, nick));
        // give the newcomer the current seat map
        client.send(seatsMessage());
        broadcastSystem(nick + " joined the study hub.");
        broadcastUsers();
        broadcastTimers();
        listener.log(nick + " registered from " + client.getAddress());
        return null;
    }

    synchronized void unregister(ClientHandler client) {
        String nick = client.getNickname();
        if (nick == null || clients.get(nick.toLowerCase()) != client) {
            return;
        }
        clients.remove(nick.toLowerCase());
        timers.remove(client);
        boolean seatsChanged = false;
        for (Seat s : seats) {
            if (s.getStatus() != Seat.Status.FREE && s.getHolder().equals(nick)) {
                s.free();
                seatsChanged = true;
            }
        }
        broadcastSystem(nick + " left the study hub.");
        if (seatsChanged) {
            broadcastSeats();
        }
        broadcastUsers();
        broadcastTimers();
        listener.log(nick + " disconnected.");
    }

    private String validateNickname(String nick) {
        if (nick == null || nick.isBlank()) {
            return "Nickname cannot be blank.";
        }
        if (!nick.matches(AppConfig.nicknamePattern())) {
            return "Invalid nickname. Use " + AppConfig.nicknameRule() + ".";
        }
        if (nick.equalsIgnoreCase(LIBRARIAN_NAME)) {
            return "Nickname 'Librarian' is reserved for the server console.";
        }
        if (clients.containsKey(nick.toLowerCase())) {
            return "Nickname '" + nick + "' is already in use.";
        }
        return null;
    }

    // =====================================================================
    // Message dispatch (called from ClientHandler threads)
    // =====================================================================

    synchronized void handle(ClientHandler c, String[] msg) {
        String type = msg[0];
        switch (type) {
            case Protocol.CHAT -> handleChat(c, Protocol.field(msg, 1));
            case Protocol.CMD -> handleCommand(c, Protocol.field(msg, 1).trim());
            case Protocol.TIMER_START -> timerStart(c, Protocol.field(msg, 1), Protocol.field(msg, 2));
            case Protocol.TIMER_PAUSE -> timerPause(c);
            case Protocol.TIMER_RESUME -> timerResume(c);
            case Protocol.TIMER_RESET -> timerReset(c);
            case Protocol.SEAT_REQUEST, Protocol.SEAT_RELEASE -> handleSeat(c, type, Protocol.field(msg, 1));
            default -> error(c, "Unknown message type: " + type);
        }
    }

    private void handleChat(ClientHandler c, String text) {
        if (text.isBlank()) {
            error(c, "Blank messages are not sent.");
            return;
        }
        if (text.length() > AppConfig.maxMessageLength()) {
            error(c, "Message too long (max " + AppConfig.maxMessageLength() + " characters).");
            return;
        }
        String time = now();
        String clean = text.strip();
        listener.chatMessage(time, c.getNickname(), clean);
        broadcast(Protocol.encode(Protocol.CHAT, time, c.getNickname(), clean));
    }

    /** Librarian sends an announcement to all connected students. Returns null on success. */
    public synchronized String sendAnnouncement(String text) {
        if (text == null || text.isBlank()) {
            return "Announcement cannot be blank.";
        }
        String clean = text.strip();
        if (clean.length() > AppConfig.maxMessageLength()) {
            return "Announcement too long (max " + AppConfig.maxMessageLength() + " characters).";
        }
        String time = now();
        listener.chatMessage(time, LIBRARIAN_NAME, clean);
        listener.log("Librarian announcement: " + clean);
        broadcast(Protocol.encode(Protocol.CHAT, time, LIBRARIAN_NAME, clean));
        return null;
    }

    private void handleCommand(ClientHandler c, String line) {
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";
        switch (cmd) {
            case "/name" -> rename(c, arg);
            case "/exit" -> {
                c.send(Protocol.encode(Protocol.BYE, "Goodbye, " + c.getNickname() + "!"));
                unregister(c);
                c.close();
            }
            case "/help" -> c.send(Protocol.encode(Protocol.INFO, helpText()));
            default -> error(c, "Unknown command '" + parts[0] + "'. Type /help for the list of commands.");
        }
    }

    private void rename(ClientHandler c, String newNick) {
        String oldNick = c.getNickname();
        if (newNick.isEmpty()) {
            error(c, "Usage: /name <newname>");
            return;
        }
        if (newNick.equals(oldNick)) {
            error(c, "That is already your nickname.");
            return;
        }
        boolean caseOnly = newNick.equalsIgnoreCase(oldNick);
        String err = caseOnly ? (newNick.matches(AppConfig.nicknamePattern()) ? null
                : "Invalid nickname. Use " + AppConfig.nicknameRule() + ".") : validateNickname(newNick);
        if (err != null) {
            error(c, err);
            return;
        }
        Map<String, ClientHandler> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ClientHandler> e : clients.entrySet()) {
            copy.put(e.getValue() == c ? newNick.toLowerCase() : e.getKey(), e.getValue());
        }
        clients.clear();
        clients.putAll(copy);
        c.setNickname(newNick);

        boolean seatsChanged = false;
        for (Seat s : seats) {
            if (s.getHolder().equals(oldNick)) {
                s.set(s.getStatus(), newNick);
                seatsChanged = true;
            }
        }
        c.send(Protocol.encode(Protocol.NICK, newNick));
        broadcastSystem(oldNick + " is now known as " + newNick + ".");
        broadcastUsers();
        if (seatsChanged) {
            broadcastSeats();
        }
        broadcastTimers();
        listener.log(oldNick + " renamed to " + newNick);
    }

    private String helpText() {
        return """
                Commands:
                  /name <newname>  change your nickname (%s)
                  /exit            leave the study hub
                  /help            show this help
                Study tools:
                  Study Seats    - pick a free seat and click Request Seat. The Librarian (Server)
                                   approves or denies requests. Use Release Seat when you leave.
                  My Study Timer - once your seat is approved, set your minutes and topic, then
                                   Start. Your timer runs independently while everyone sees live
                                   progress in Student Timers.""".formatted(AppConfig.nicknameRule());
    }

    // =====================================================================
    // Feature 1: per-student study session timer (requires approved seat)
    // =====================================================================

    private boolean hasApprovedSeat(String nick) {
        if (nick == null || nick.isEmpty()) {
            return false;
        }
        for (Seat s : seats) {
            if (s.getStatus() == Seat.Status.TAKEN && s.getHolder().equals(nick)) {
                return true;
            }
        }
        return false;
    }

    private boolean resetTimerForUser(String nick) {
        ClientHandler target = clients.get(nick.toLowerCase());
        if (target == null) {
            return false;
        }
        StudentTimer t = timers.get(target);
        if (t == null || t.state.equals("IDLE")) {
            return false;
        }
        t.state = "IDLE";
        t.remaining = 0;
        t.total = 0;
        t.label = "";
        return true;
    }

    private void timerStart(ClientHandler c, String minutesText, String label) {
        if (!hasApprovedSeat(c.getNickname())) {
            error(c, "You need an approved study seat before starting your timer. Request a seat first.");
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(minutesText.trim());
        } catch (NumberFormatException e) {
            error(c, "Timer minutes must be a whole number.");
            return;
        }
        if (minutes < 1 || minutes > AppConfig.maxTimerMinutes()) {
            error(c, "Timer minutes must be between 1 and " + AppConfig.maxTimerMinutes() + ".");
            return;
        }
        StudentTimer t = timers.get(c);
        if (t == null) {
            return;
        }
        if (t.state.equals("RUNNING") || t.state.equals("PAUSED")) {
            error(c, "Your session is already active. Reset it first.");
            return;
        }
        label = label.strip();
        if (label.isEmpty()) {
            label = "Study session";
        }
        if (label.length() > AppConfig.maxTimerLabelLength()) {
            label = label.substring(0, AppConfig.maxTimerLabelLength());
        }
        t.state = "RUNNING";
        t.total = minutes * 60;
        t.remaining = t.total;
        t.label = label;
        broadcastTimers();
        broadcastSystem(c.getNickname() + " started a " + minutes + "-minute timer: " + label);
    }

    private void timerPause(ClientHandler c) {
        StudentTimer t = timers.get(c);
        if (t == null || !t.state.equals("RUNNING")) {
            error(c, "You have no running timer to pause.");
            return;
        }
        t.state = "PAUSED";
        broadcastTimers();
        broadcastSystem(c.getNickname() + " paused their timer.");
    }

    private void timerResume(ClientHandler c) {
        StudentTimer t = timers.get(c);
        if (t == null || !t.state.equals("PAUSED")) {
            error(c, "Your timer is not paused.");
            return;
        }
        t.state = "RUNNING";
        broadcastTimers();
        broadcastSystem(c.getNickname() + " resumed their timer.");
    }

    private void timerReset(ClientHandler c) {
        StudentTimer t = timers.get(c);
        if (t == null || t.state.equals("IDLE")) {
            error(c, "You have no timer to reset.");
            return;
        }
        t.state = "IDLE";
        t.remaining = 0;
        t.total = 0;
        t.label = "";
        broadcastTimers();
        broadcastSystem(c.getNickname() + " reset their timer.");
    }

    /** Runs every second on the ticker thread. */
    private synchronized void tick() {
        boolean anyRunning = false;
        List<String> finishedMsgs = new ArrayList<>();
        for (Map.Entry<ClientHandler, StudentTimer> e : timers.entrySet()) {
            StudentTimer t = e.getValue();
            if (!t.state.equals("RUNNING")) {
                continue;
            }
            anyRunning = true;
            t.remaining--;
            if (t.remaining <= 0) {
                t.remaining = 0;
                t.state = "FINISHED";
                finishedMsgs.add(e.getKey().getNickname() + "'s session '" + t.label + "' finished!");
            }
        }
        if (anyRunning) {
            broadcastTimers();
            for (String msg : finishedMsgs) {
                broadcastSystem(msg);
            }
        }
    }

    private void broadcastTimers() {
        notifyStudents();
        broadcast(timersMessage());
    }

    private String timersMessage() {
        Object[] fields = new Object[timers.size() * 5];
        int idx = 0;
        for (Map.Entry<ClientHandler, StudentTimer> e : timers.entrySet()) {
            StudentTimer t = e.getValue();
            fields[idx++] = e.getKey().getNickname();
            fields[idx++] = t.state;
            fields[idx++] = t.remaining;
            fields[idx++] = t.total;
            fields[idx++] = t.label;
        }
        return Protocol.encode(Protocol.TIMERS, fields);
    }

    // =====================================================================
    // Feature 2: study seat request (student) and approval (Librarian / Server)
    // =====================================================================

    private void handleSeat(ClientHandler c, String type, String seatText) {
        int index;
        try {
            index = Integer.parseInt(seatText.trim()) - 1;
        } catch (NumberFormatException e) {
            error(c, "Invalid seat number.");
            return;
        }
        if (index < 0 || index >= seats.length) {
            error(c, "Seat number must be between 1 and " + seats.length + ".");
            return;
        }
        Seat seat = seats[index];
        String nick = c.getNickname();
        String seatName = "Seat " + (index + 1);

        switch (type) {
            case Protocol.SEAT_REQUEST -> {
                if (seat.getStatus() != Seat.Status.FREE) {
                    error(c, seatName + " is not free.");
                    return;
                }
                for (Seat s : seats) {
                    if (s.getStatus() != Seat.Status.FREE && s.getHolder().equals(nick)) {
                        error(c, "You already hold or requested a seat. Release it first.");
                        return;
                    }
                }
                seat.set(Seat.Status.PENDING, nick);
                broadcastSystem(nick + " requested " + seatName + ". Waiting for librarian approval.");
            }
            case Protocol.SEAT_RELEASE -> {
                if (seat.getStatus() == Seat.Status.FREE) {
                    error(c, seatName + " is already free.");
                    return;
                }
                if (!seat.getHolder().equals(nick)) {
                    error(c, "Only " + seat.getHolder() + " or the librarian can release " + seatName + ".");
                    return;
                }
                seat.free();
                broadcastSystem(nick + " released " + seatName + ".");
                if (resetTimerForUser(nick)) {
                    broadcastTimers();
                }
            }
            default -> {
                return;
            }
        }
        broadcastSeats();
    }

    /** Librarian approves a pending seat request (1-based seatNumber). Returns null on success. */
    public synchronized String approveSeat(int seatNumber) {
        int index = seatNumber - 1;
        if (index < 0 || index >= seats.length) {
            return "Invalid seat number.";
        }
        Seat seat = seats[index];
        if (seat.getStatus() != Seat.Status.PENDING) {
            return "Seat " + seatNumber + " has no pending request.";
        }
        String requester = seat.getHolder();
        seat.set(Seat.Status.TAKEN, requester);
        broadcastSystem("Librarian approved Seat " + seatNumber + " for " + requester + ".");
        broadcastSeats();
        return null;
    }

    /** Librarian denies a pending seat request (1-based seatNumber). Returns null on success. */
    public synchronized String denySeat(int seatNumber) {
        int index = seatNumber - 1;
        if (index < 0 || index >= seats.length) {
            return "Invalid seat number.";
        }
        Seat seat = seats[index];
        if (seat.getStatus() != Seat.Status.PENDING) {
            return "Seat " + seatNumber + " has no pending request.";
        }
        String requester = seat.getHolder();
        seat.free();
        broadcastSystem("Librarian denied " + requester + "'s request for Seat " + seatNumber + ".");
        broadcastSeats();
        return null;
    }

    /** Librarian releases any pending or taken seat (1-based seatNumber). Returns null on success. */
    public synchronized String releaseSeatByLibrarian(int seatNumber) {
        int index = seatNumber - 1;
        if (index < 0 || index >= seats.length) {
            return "Invalid seat number.";
        }
        Seat seat = seats[index];
        if (seat.getStatus() == Seat.Status.FREE) {
            return "Seat " + seatNumber + " is already free.";
        }
        String holder = seat.getHolder();
        seat.free();
        broadcastSystem("Librarian released Seat " + seatNumber + " (held by " + holder + ").");
        broadcastSeats();
        if (resetTimerForUser(holder)) {
            broadcastTimers();
        }
        return null;
    }

    private void broadcastSeats() {
        notifySeats();
        notifyStudents();
        broadcast(seatsMessage());
    }

    private void notifySeats() {
        Seat.Status[] statuses = new Seat.Status[seats.length];
        String[] holders = new String[seats.length];
        for (int i = 0; i < seats.length; i++) {
            statuses[i] = seats[i].getStatus();
            holders[i] = seats[i].getHolder();
        }
        listener.seatsChanged(statuses, holders);
    }

    private void notifyStudents() {
        List<String[]> rows = new ArrayList<>();
        for (ClientHandler c : clients.values()) {
            String nick = c.getNickname();
            String seatText = "No seat";
            for (int i = 0; i < seats.length; i++) {
                if (seats[i].getStatus() != Seat.Status.FREE && seats[i].getHolder().equals(nick)) {
                    seatText = "Seat " + (i + 1)
                            + (seats[i].getStatus() == Seat.Status.PENDING ? " (Pending)" : " (Approved)");
                    break;
                }
            }
            StudentTimer t = timers.get(c);
            String timerText = "Idle";
            if (t != null) {
                timerText = switch (t.state) {
                    case "RUNNING" -> String.format("%02d:%02d — %s", t.remaining / 60, t.remaining % 60, t.label);
                    case "PAUSED" -> String.format("%02d:%02d [Paused] — %s", t.remaining / 60, t.remaining % 60, t.label);
                    case "FINISHED" -> "Done — " + t.label;
                    default -> "Idle";
                };
            }
            rows.add(new String[]{nick, seatText, timerText});
        }
        listener.studentsChanged(rows);
    }

    private String seatsMessage() {
        Object[] fields = new Object[1 + seats.length * 2];
        fields[0] = seats.length;
        for (int i = 0; i < seats.length; i++) {
            fields[1 + i * 2] = seats[i].getStatus().name();
            fields[2 + i * 2] = seats[i].getHolder();
        }
        return Protocol.encode(Protocol.SEATS, fields);
    }

    // =====================================================================
    // Sending helpers (always called while holding the lock)
    // =====================================================================

    private void broadcast(String line) {
        for (ClientHandler c : clients.values()) {
            c.send(line);
        }
    }

    private void broadcastSystem(String text) {
        broadcast(Protocol.encode(Protocol.SYSTEM, now(), text));
        listener.log(text);
    }

    private void broadcastUsers() {
        List<String> names = new ArrayList<>();
        for (ClientHandler c : clients.values()) {
            names.add(c.getNickname());
        }
        notifyStudents();
        broadcast(Protocol.encode(Protocol.USERS, names.toArray()));
    }

    private void error(ClientHandler c, String text) {
        c.send(Protocol.encode(Protocol.ERROR, text));
    }

    private static String now() {
        return LocalTime.now().format(TIME);
    }
}
