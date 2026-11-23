package server;

import java.util.List;

/** Callbacks from the server core to whoever displays it (the ServerFrame GUI). */
public interface ServerListener {

    void log(String text);

    void chatMessage(String time, String sender, String text);

    /** Each row is {nickname, seatStatusText, timerStatusText}. */
    void studentsChanged(List<String[]> rows);

    void seatsChanged(Seat.Status[] statuses, String[] holders);

    void serverStopped();
}
