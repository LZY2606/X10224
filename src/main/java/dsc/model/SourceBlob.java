package dsc.model;

import java.util.List;
import java.util.Map;

public record SourceBlob(
        String blobId,
        String fileName,
        String mediaType,
        String sha256,
        String rawText,
        Map<String, String> metadata,
        List<String> columnHeaders,
        List<List<String>> originalRows
) {
}
