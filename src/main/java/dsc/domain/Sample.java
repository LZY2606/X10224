package dsc.domain;

import java.util.Objects;

/**
 * The sample whose thermal history the DSC sequences belong to. Identity is
 * stable across export/delete/re-import.
 */
public final class Sample {
    private final String id;
    private final String code;
    private final String material;
    private final String note;

    public Sample(String id, String code, String material, String note) {
        this.id = Objects.requireNonNull(id);
        this.code = code;
        this.material = material;
        this.note = note;
    }

    public String id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String material() {
        return material;
    }

    public String note() {
        return note;
    }
}
