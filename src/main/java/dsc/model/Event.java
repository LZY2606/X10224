package dsc.model;

/** Machine-readable judgment record; the replay log is a sequence of these. */
public final class Event {
    public final long seq;
    public final String id;
    public final String type;
    public final String actor;
    public final String payload;
    public final String at;

    public Event(long seq, String id, String type, String actor, String payload, String at) {
        this.seq = seq;
        this.id = id;
        this.type = type;
        this.actor = actor;
        this.payload = payload;
        this.at = at;
    }
}
