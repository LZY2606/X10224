package org.materials.dsc.domain;

/** 气氛：名称与流量原样存档。 */
public record Atmosphere(String name, String rawFlow, Quantity flow, String diagnostic) {

    public java.util.List<String> diagnostics() {
        return diagnostic == null
                ? java.util.List.of()
                : java.util.List.of("atmosphere: " + diagnostic);
    }
}
