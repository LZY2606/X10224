package dsc.store;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public final class Json {
    public static final Gson GSON = new Gson();
    private static final Type MAP = new TypeToken<Map<String, String>>() {}.getType();
    private static final Type STRLIST = new TypeToken<List<String>>() {}.getType();

    private Json() {}

    public static String map(Map<String, String> map) { return GSON.toJson(map == null ? Map.of() : map); }
    public static Map<String, String> readMap(String s) {
        if (s == null || s.isBlank()) return Map.of();
        return GSON.fromJson(s, MAP);
    }

    public static String list(List<String> list) { return GSON.toJson(list == null ? List.of() : list); }
    public static List<String> readList(String s) {
        if (s == null || s.isBlank()) return List.of();
        return GSON.fromJson(s, STRLIST);
    }

    public static String obj(Object o) { return GSON.toJson(o); }
    public static <T> T read(String s, Class<T> type) { return GSON.fromJson(s, type); }

    public record Point(double x, double y) {}

    public static String points(double[] xs, double[] ys) {
        var pts = new java.util.ArrayList<Point>(xs.length);
        for (int i = 0; i < xs.length; i++) pts.add(new Point(xs[i], ys[i]));
        return GSON.toJson(pts);
    }

    public static List<Point> readPoints(String s) {
        Type t = new TypeToken<List<Point>>() {}.getType();
        return GSON.fromJson(s, t);
    }
}
