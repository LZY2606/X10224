package dsc.engine;

import com.google.gson.Gson;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Codecs {
    private static final Gson GSON = new Gson();

    private Codecs() {
    }

    public static Map<String, Object> toMap(Object record) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = GSON.fromJson(GSON.toJson(record), Map.class);
        return map == null ? new LinkedHashMap<>() : map;
    }

    public static <T> T fromMap(Map<String, Object> map, Class<T> type) {
        return GSON.fromJson(GSON.toJson(map), type);
    }

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }
}
