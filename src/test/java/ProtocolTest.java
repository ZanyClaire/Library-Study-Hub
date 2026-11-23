import common.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import server.ChatServer;
import server.Seat;
import server.ServerListener;

/**
 * Automated multi-client integration test covering all ITAS1 minimum test cases:
 *  - 3 concurrent clients, group chat broadcast & frame escaping
 *  - Duplicate, blank, and reserved ('Librarian') nickname validation
 *  - /name, /help, /exit, unknown commands, and blank message rejection
 *  - Seat-gated per-student independent timers & Student Monitor table sync
 *  - Study seat request, Librarian approval/denial, announcements & live chat monitor
 *  - Abrupt client disconnect recovery and clean server shutdown
 */
public class ProtocolTest {

    static class TestClient {
        final Socket socket;
        final BufferedReader in;
        final PrintWriter out;
        final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        TestClient(int port, String nick) throws Exception {
            socket = new Socket("localhost", port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println(Protocol.encode(Protocol.HELLO, nick));
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        queue.add(line);
                    }
                } catch (IOException ignored) {
                    // closed
                }
                queue.add("<EOF>");
            });
            reader.setDaemon(true);
            reader.start();
        }

        void send(String type, Object... fields) {
            out.println(Protocol.encode(type, fields));
        }

        String waitFor(String prefix) throws Exception {
            long end = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < end) {
                String line = queue.poll(200, TimeUnit.MILLISECONDS);
                if (line != null && line.startsWith(prefix)) {
                    return line;
                }
            }
            throw new AssertionError("Timed out waiting for prefix: " + prefix);
        }

        String waitForContains(String part) throws Exception {
            long end = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < end) {
                String line = queue.poll(200, TimeUnit.MILLISECONDS);
                if (line != null && line.contains(part)) {
                    return line;
                }
            }
            throw new AssertionError("Timed out waiting for substring: " + part);
        }
    }

    private static int passed = 0;

    private static void ok(String description) {
        passed++;
        System.out.println("PASS " + description);
    }

    public static void main(String[] args) throws Exception {
        int port = 5987;
        List<String> monitoredChats = Collections.synchronizedList(new ArrayList<>());
        List<String[]> lastMonitorRows = Collections.synchronizedList(new ArrayList<>());

        ChatServer server = new ChatServer(port, 4, new ServerListener() {
            @Override
            public void log(String text) { }

            @Override
            public void chatMessage(String time, String sender, String text) {
                monitoredChats.add(sender + ": " + text);
            }

            @Override
            public void studentsChanged(List<String[]> rows) {
                synchronized (lastMonitorRows) {
                    lastMonitorRows.clear();
                    lastMonitorRows.addAll(rows);
                }
            }

            @Override
            public void seatsChanged(Seat.Status[] statuses, String[] holders) { }

            @Override
            public void serverStopped() { }
        });

        try {
            server.start();

            TestClient lib = new TestClient(port, "Librarian");
            lib.waitFor("REJECT");
            ok("reserved Librarian nickname rejected");

            TestClient alice = new TestClient(port, "alice");
            alice.waitFor("WELCOME");
            ok("alice joins");

            TestClient bob = new TestClient(port, "bob");
            bob.waitFor("WELCOME");
            TestClient cara = new TestClient(port, "cara");
            cara.waitFor("WELCOME");
            ok("three concurrent clients");

            String users = alice.waitFor("USERS|alice|bob|cara");
            ok("online list updated: " + users);

            TestClient dup = new TestClient(port, "Bob");
            dup.waitFor("REJECT");
            ok("duplicate nickname rejected");

            TestClient blankNick = new TestClient(port, " ");
            blankNick.waitFor("REJECT");
            ok("blank nickname rejected");

            bob.send(Protocol.CHAT, "hello | all\nline2");
            for (TestClient c : List.of(alice, bob, cara)) {
                String msg = c.waitFor("CHAT|");
                if (!Protocol.decode(msg)[3].equals("hello | all\nline2")) {
                    throw new AssertionError("Escaping mismatch: " + msg);
                }
            }
            if (!monitoredChats.contains("bob: hello | all\nline2")) {
                throw new AssertionError("Server did not monitor student chat");
            }
            ok("broadcast to all incl. sender, escaping & server chat monitor work");

            if (server.sendAnnouncement("   ") == null) {
                throw new AssertionError("Blank announcement should be rejected");
            }
            if (server.sendAnnouncement("Quiet hours start now.") != null) {
                throw new AssertionError("Librarian announcement failed");
            }
            for (TestClient c : List.of(alice, bob, cara)) {
                c.waitForContains("|Librarian|Quiet hours start now.");
            }
            ok("librarian announcement broadcast to all students");

            bob.send(Protocol.CHAT, "   ");
            bob.waitFor("ERROR|Blank");
            ok("blank message rejected");

            bob.send(Protocol.CMD, "/name alice");
            bob.waitFor("ERROR|Nickname 'alice'");
            ok("duplicate rename rejected");

            bob.send(Protocol.CMD, "/name bobby");
            bob.waitFor("NICK|bobby");
            cara.waitFor("USERS|alice|bobby|cara");
            ok("valid rename updates all clients");

            bob.send(Protocol.CMD, "/help");
            bob.waitFor("INFO|");
            ok("/help command");

            bob.send(Protocol.CMD, "/foo");
            bob.waitFor("ERROR|Unknown command");
            ok("unknown command rejected");

            // Seat-gated timers
            cara.send(Protocol.TIMER_START, 25, "Calculus");
            cara.waitFor("ERROR|You need an approved study seat");
            ok("timer blocked when student has no seat");

            bob.send(Protocol.SEAT_REQUEST, 2);
            cara.waitFor("SEATS|4|FREE||PENDING|bobby");
            ok("seat request visible to all");

            bob.send(Protocol.TIMER_START, 20, "Physics");
            bob.waitFor("ERROR|You need an approved study seat");
            ok("timer blocked while seat is still PENDING");

            cara.send(Protocol.SEAT_REQUEST, 2);
            cara.waitFor("ERROR|Seat 2 is not free");
            ok("seat conflict rejected");

            if (server.approveSeat(2) != null) {
                throw new AssertionError("approveSeat(2) failed");
            }
            cara.waitFor("SEATS|4|FREE||TAKEN|bobby");
            ok("librarian approves seat 2");

            bob.send(Protocol.SEAT_REQUEST, 3);
            bob.waitFor("ERROR|You already hold");
            ok("one seat per user enforced");

            cara.send(Protocol.SEAT_REQUEST, 9);
            cara.waitFor("ERROR|Seat number");
            ok("invalid seat number rejected");

            cara.send(Protocol.SEAT_REQUEST, 1);
            alice.waitFor("SEATS|4|PENDING|cara");
            if (server.denySeat(1) != null) {
                throw new AssertionError("denySeat(1) failed");
            }
            cara.waitFor("SEATS|4|FREE||TAKEN|bobby");
            ok("librarian denies seat 1");

            cara.send(Protocol.SEAT_RELEASE, 2);
            cara.waitFor("ERROR|Only bobby");
            ok("other student cannot release bobby's seat");

            // Approve seats 1 and 3 for alice and cara
            alice.send(Protocol.SEAT_REQUEST, 1);
            alice.waitFor("SEATS|4|PENDING|alice");
            cara.send(Protocol.SEAT_REQUEST, 3);
            cara.waitFor("SEATS|4|PENDING|alice|TAKEN|bobby|PENDING|cara");
            if (server.approveSeat(1) != null || server.approveSeat(3) != null) {
                throw new AssertionError("approveSeat 1/3 failed");
            }
            cara.waitFor("SEATS|4|TAKEN|alice|TAKEN|bobby|TAKEN|cara");

            // Independent per-student timers
            cara.send(Protocol.TIMER_START, 25, "Calculus");
            alice.waitFor("TIMERS|alice|IDLE|0|0||bobby|IDLE|0|0||cara|RUNNING|1500|1500|Calculus");
            alice.send(Protocol.TIMER_START, 15, "History");
            bob.waitFor("TIMERS|alice|RUNNING|900|900|History|bobby|IDLE|0|0||cara|RUNNING|1500|1500|Calculus");
            synchronized (lastMonitorRows) {
                if (lastMonitorRows.size() != 3 || !lastMonitorRows.get(0)[1].equals("Seat 1 (Approved)")
                        || !lastMonitorRows.get(0)[2].contains("History")) {
                    throw new AssertionError("Student Monitor table rows mismatch");
                }
            }
            ok("independent timers per student & live Student Monitor table synced");

            cara.send(Protocol.TIMER_START, 5, "x");
            cara.waitFor("ERROR|Your session is already active");
            ok("double timer start rejected");

            bob.send(Protocol.TIMER_PAUSE);
            bob.waitFor("ERROR|You have no running timer");
            ok("cannot pause another student's timer");

            cara.send(Protocol.TIMER_PAUSE);
            alice.waitFor("TIMERS|alice|RUNNING|");
            ok("cara pauses own timer while alice keeps running");

            cara.send(Protocol.SEAT_RELEASE, 3);
            bob.waitForContains("|cara|IDLE|0|0|");
            ok("releasing seat automatically resets active timer");

            alice.send(Protocol.TIMER_RESET);
            bob.send(Protocol.TIMER_START, 999, "x");
            bob.waitFor("ERROR|Timer minutes");
            ok("invalid timer minutes rejected");

            // Abrupt disconnect recovery
            alice.socket.close();
            cara.waitFor("USERS|bobby|cara");
            ok("abrupt disconnect handled");

            cara.send(Protocol.CHAT, "still here");
            bob.waitFor("CHAT|");
            ok("chat continues after disconnect");

            // Clean /exit frees seat
            bob.send(Protocol.CMD, "/exit");
            bob.waitFor("BYE");
            cara.waitFor("SEATS|4|FREE||FREE||FREE||FREE|");
            cara.waitFor("USERS|cara");
            ok("/exit frees seat and updates online list");

            server.stop();
            cara.waitFor("<EOF>");
            ok("server stop closes clients");
            System.out.println("ALL " + passed + " CHECKS PASSED");
        } finally {
            server.stop();
        }
        System.exit(0);
    }
}
