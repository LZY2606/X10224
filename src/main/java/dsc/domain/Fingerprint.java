package dsc.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 派生指纹：由输入依赖（节点 id -> 版本摘要）与规则版本构成。
 * 依赖未变则结果仍有效；任何直接来源改动都沿依赖图传播失效。
 */
public record Fingerprint(String ruleVersion, List<String> deps, String digest) {

    public static Fingerprint of(String ruleVersion, List<String> depHashes) {
        List<String> sorted = new ArrayList<>(depHashes);
        java.util.Collections.sort(sorted);
        String joined = ruleVersion + "|" + String.join(";", sorted);
        return new Fingerprint(ruleVersion, sorted, sha256(joined));
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
