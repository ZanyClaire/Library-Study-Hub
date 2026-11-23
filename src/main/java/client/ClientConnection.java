package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import common.AppConfig;
import common.Protocol;

/**
 * Client networking (no GUI code). Connects, performs the HELLO handshake,
 * then reads server messages on a background thread and reports them to a Listener.
 */
public class ClientConnection {

    /** Called from the reader thread - the GUI must switch to the EDT itself. */
    public interface Listener {
        void onMessage(String[] message);

        void onDisconnected(String reason);
    }

    private final Listener listener;
    private Socket socket;
    private PrintWriter out;
    private volatile boolean closing;

    public ClientConnection(Listener listener) {
        this.listener = listener;
    }

    /**
     * Blocking connect + handshake. Call it outside the Swing thread.
     * @return the nickname accepted by the server
     * @throws IOException with a user-friendly message on failure
     */
    public String connect(String host, int port, String nickname) throws IOException {
        socket = new Socket();
        BufferedReader in;
        try {
            socket.connect(new InetSocketAddress(host, port), AppConfig.connectTimeoutMs());
            socket.setSoTimeout(AppConfig.connectTimeoutMs()); // only for the handshake
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
        } catch (UnknownHostException e) {
            closeQuietly();
            throw new IOException("Unknown server address: " + host);
        } catch (ConnectException e) {
            closeQuietly();
            throw new IOException("Server unavailable at " + host + ":" + port + ". Is the server started?");
        } catch (SocketTimeoutException e) {
            closeQuietly();
            throw new IOException("Connection timed out. Check the IP address, port and firewall.");
        } catch (IllegalArgumentException e) {
            closeQuietly();
            throw new IOException("Invalid address or port.");
        }

        send(Protocol.HELLO, nickname);
        String reply;
        try {
            reply = in.readLine();
        } catch (SocketTimeoutException e) {
            closeQuietly();
            throw new IOException("The server did not answer. Is it a Library Study Hub server?");
        }
        if (reply == null) {
            closeQuietly();
            throw new IOException("The server closed the connection.");
        }
        String[] msg = Protocol.decode(reply);
        if (Protocol.REJECT.equals(msg[0])) {
            closeQuietly();
            throw new IOException(Protocol.field(msg, 1));
        }
        if (!Protocol.WELCOME.equals(msg[0])) {
            closeQuietly();
            throw new IOException("Unexpected server reply.");
        }
        socket.setSoTimeout(0);
        Thread reader = new Thread(() -> readLoop(in), "client-reader");
        reader.setDaemon(true);
        reader.start();
        return Protocol.field(msg, 1);
    }

    private void readLoop(BufferedReader in) {
        String reason = "Connection to the server was lost.";
        try {
            String line;
            while ((line = in.readLine()) != null) {
                String[] msg = Protocol.decode(line);
                if (Protocol.BYE.equals(msg[0])) {
                    reason = Protocol.field(msg, 1);
                    break;
                }
                listener.onMessage(msg);
            }
        } catch (IOException e) {
            // lost connection
        }
        if (closing) {
            reason = "Disconnected.";
        }
        closeQuietly();
        listener.onDisconnected(reason);
    }

    /** Thread-safe: a whole line is written at once, never mixed with another send. */
    public synchronized void send(String type, Object... fields) {
        if (out != null) {
            out.println(Protocol.encode(type, fields));
        }
    }

    /** Clean exit: tells the server with /exit, the reader thread then finishes. */
    public void disconnect() {
        closing = true;
        send(Protocol.CMD, "/exit");
        if (out != null && out.checkError()) {
            closeQuietly();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private void closeQuietly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // already closed
        }
    }
}
