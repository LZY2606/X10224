package dsc.store;

/** Raised by the crash test hook; the process discards the uncommitted connection. */
public class SimulatedCrash extends RuntimeException {
    public SimulatedCrash(String message) {
        super(message);
    }
}
