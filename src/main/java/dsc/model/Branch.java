package dsc.model;

public record Branch(
        String name,
        String parent,
        long forkSeq,
        String reason,
        String createdEventId
) {
    public static final String MAIN = "main";
}
