package common;

import java.util.ArrayList;
import java.util.List;

/**
 * Message format shared by server and client.
 *
 * One message = one text line:  TYPE|field1|field2|...
 * Inside a field:  \  is written as \\ ,  | as \p ,  newline as \n ,  carriage return as \r
 * so user text can never break the message structure.
 */
public final class Protocol {

    // ---------- client -> server ----------
    public static final String HELLO = "HELLO";               // HELLO|nickname
    public static final String CHAT = "CHAT";                 // CHAT|text
    public static final String CMD = "CMD";                   // CMD|/name bob   (/name, /exit, /help)
    public static final String TIMER_START = "TIMER_START";   // TIMER_START|minutes|label
    public static final String TIMER_PAUSE = "TIMER_PAUSE";
    public static final String TIMER_RESUME = "TIMER_RESUME";
    public static final String TIMER_RESET = "TIMER_RESET";
    public static final String SEAT_REQUEST = "SEAT_REQUEST"; // SEAT_REQUEST|seatNo
    public static final String SEAT_RELEASE = "SEAT_RELEASE"; // SEAT_RELEASE|seatNo

    // ---------- server -> client ----------
    public static final String WELCOME = "WELCOME";   // WELCOME|nickname
    public static final String REJECT = "REJECT";     // REJECT|reason (connection is closed after)
    public static final String NICK = "NICK";         // NICK|newNickname (your rename succeeded)
    // CHAT (server -> client): CHAT|time|sender|text
    public static final String SYSTEM = "SYSTEM";     // SYSTEM|time|text
    public static final String INFO = "INFO";         // INFO|text (only to one client, e.g. /help)
    public static final String ERROR = "ERROR";       // ERROR|text
    public static final String USERS = "USERS";       // USERS|nick1|nick2|...
    public static final String TIMERS = "TIMERS";     // TIMERS|nick1|state1|rem1|tot1|label1|...
    public static final String SEATS = "SEATS";       // SEATS|count|status1|holder1|status2|holder2|...
    public static final String BYE = "BYE";           // BYE|text

    private Protocol() {
    }

    /** Builds one protocol line from a type and its fields. */
    public static String encode(String type, Object... fields) {
        StringBuilder sb = new StringBuilder(escape(type));
        for (Object f : fields) {
            sb.append('|').append(escape(f == null ? "" : f.toString()));
        }
        return sb.toString();
    }

    /** Splits one protocol line into [type, field1, field2, ...]. */
    public static String[] decode(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char n = line.charAt(++i);
                switch (n) {
                    case 'p' -> cur.append('|');
                    case 'n' -> cur.append('\n');
                    case 'r' -> cur.append('\r');
                    default -> cur.append(n);
                }
            } else if (c == '|') {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }

    /** Safe field access: returns "" when the field is missing. */
    public static String field(String[] parts, int index) {
        return index < parts.length ? parts[index] : "";
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '|' -> sb.append("\\p");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
