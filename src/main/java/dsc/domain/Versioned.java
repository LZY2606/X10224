package dsc.domain;

/**
 * A source value stored independently per revision. Every change to a direct
 * source (mass, scan rate, atmosphere, temperature calibration, tangent)
 * creates a new version; derivations reference exact versions.
 */
public record Versioned<T>(int version, T value, String origin, String comment) {

    public T value() {
        return value;
    }
}
