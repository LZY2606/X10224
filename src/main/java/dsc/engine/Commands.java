package dsc.engine;

import java.util.Map;

public final class Commands {
    private Commands() {
    }

    public record Cmd(String type, String actor, String clientVersion, Map<String, Object> args) {
    }

    public static Cmd of(String type, String actor, String clientVersion, Map<String, Object> args) {
        return new Cmd(type, actor, clientVersion, args);
    }

    public static final String OPEN_PROJECT = "open-project";
    public static final String ARCHIVE_AND_IMPORT = "archive-and-import";
    public static final String SET_MASS = "set-mass";
    public static final String SET_CALIBRATION = "set-calibration";
    public static final String SET_ATMOSPHERE = "set-atmosphere";
    public static final String PICK_TANGENT = "pick-tangent";
    public static final String DRAW_INTERVAL = "draw-interval";
    public static final String ADJUST_INTERVAL = "adjust-interval";
    public static final String MAKE_DECISION = "make-decision";
    public static final String FORCE_RECOMPUTE = "force-recompute";
}
