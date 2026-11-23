package server;

/** One study seat. Status is FREE, PENDING (waiting for host approval) or TAKEN. */
public class Seat {

    public enum Status { FREE, PENDING, TAKEN }

    private Status status = Status.FREE;
    private String holder = "";

    public Status getStatus() {
        return status;
    }

    public String getHolder() {
        return holder;
    }

    public void set(Status status, String holder) {
        this.status = status;
        this.holder = holder == null ? "" : holder;
    }

    public void free() {
        set(Status.FREE, "");
    }
}
