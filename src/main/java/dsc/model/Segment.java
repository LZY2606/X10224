package dsc.model;

/** A monotonic heating or cooling run inside one instrument file. */
public final class Segment {
    public enum Direction { HEATING, COOLING, ISOTHERMAL }

    public final int index;
    public final int cycle;
    public final Direction direction;
    public final int from;
    public final int to;

    public Segment(int index, int cycle, Direction direction, int from, int to) {
        this.index = index;
        this.cycle = cycle;
        this.direction = direction;
        this.from = from;
        this.to = to;
    }

    public int size() {
        return to - from + 1;
    }
}
