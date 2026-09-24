package dsc.service;

/** Injectable clock so replay and tests are deterministic. */
public interface Clock {
    long now();

    Clock SYSTEM = System::currentTimeMillis;
}
