package dsc.core;

/** Endo/exo plotting convention. Never inferred from file names. */
public enum Direction {
    ENDO_UP,
    ENDO_DOWN;

    public Direction opposite() {
        return this == ENDO_UP ? ENDO_DOWN : ENDO_UP;
    }
}
