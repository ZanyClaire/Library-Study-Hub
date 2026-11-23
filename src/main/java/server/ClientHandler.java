package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import common.Protocol;

/**
 * One thread per connected client. Reads lines from the socket and passes
 * them to ChatServer, which validates and updates the shared state.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private PrintWriter out;
    private volatile String nickname;
    private volatile boolean closed;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getNickname() {
        return nickname;
    }

    void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void run() {
        boolean registered = false;
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);

            // 1) First line must be HELLO|nickname
            String first = in.readLine();
            if (first == null) {
                return;
            }
            String[] hello = Protocol.decode(first);
            if (!Protocol.HELLO.equals(hello[0])) {
                send(Protocol.encode(Protocol.REJECT, "Protocol error: expected HELLO."));
                return;
            }
            String error = server.register(this, Protocol.field(hello, 1).trim());
            if (error != null) {
                send(Protocol.encode(Protocol.REJECT, error));
                return;
            }
            registered = true;

            // 2) Normal message loop
            String line;
            while (!closed && (line = in.readLine()) != null) {
                if (!line.isEmpty()) {
                    server.handle(this, Protocol.decode(line));
                }
            }
        } catch (IOException e) {
            // connection lost (abrupt disconnect) - handled in finally
        } finally {
            if (registered) {
                server.unregister(this);
            }
            close();
        }
    }

    /** Thread-safe send: one whole line at a time, so concurrent sends never mix. */
    public synchronized void send(String line) {
        if (out != null && !closed) {
            out.println(line);
        }
    }

    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
