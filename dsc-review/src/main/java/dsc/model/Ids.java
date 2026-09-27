package dsc.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * 稳定身份工具：显示值、事件、候选都拥有不依赖易变 rowid 的身份。
 * 同一逻辑键永远得到同一 UUID（name-based UUIDv3），导出/删除库/再导入后保持不变。
 */
public final class Ids {
    private Ids() {}

    public static UUID stable(String key) {
        try {
            byte[] name = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[16];
            System.arraycopy(name, 0, out, 0, 16);
            out[6] &= 0x0f;
            out[6] |= 0x30;
            out[8] &= 0x3f;
            out[8] |= 0x80;
            long msb = 0, lsb = 0;
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (out[i] & 0xffL);
                lsb = (lsb << 8) | (out[i + 8] & 0xffL);
            }
            return new UUID(msb, lsb);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sid(String key) {
        return stable(key).toString();
    }

    public static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
