package dsc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dsc.domain.BaselineFit;
import dsc.domain.Diagnostic;
import dsc.domain.Interval;
import dsc.domain.Quantity;
import dsc.domain.TangentSelection;
import dsc.domain.TransitionResult;
import dsc.domain.TransitionType;
import dsc.domain.UncertaintySource;
import dsc.domain.BaselineModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed (de)serialisation for event payloads. All numbers carry their unit in
 * the payload exactly as at ingestion; derived canonical values sit beside the
 * raw text rather than replacing it.
 */
public final class Payloads {

    static final ObjectMapper JSON = new ObjectMapper();

    private Payloads() {
    }

    public record MassPayload(String massId, Quantity mass) {
    }

    public record TangentPayload(TangentSelection tangent) {
    }

    public record IntervalPayload(String segmentId, Interval interval, TransitionType type,
                                  String tangentId, String massId) {
    }

    public record ResultPayload(TransitionResult result) {
    }

    public record BaselinePayload(String candidateId, String segmentId, String tangentId,
                                  BaselineFit fit, String ruleVersion) {
    }

    public static String write(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T read(String s, Class<T> type) {
        try {
            return JSON.readValue(s, type);
        } catch (Exception e) {
            throw new RuntimeException("bad payload: " + s, e);
        }
    }

    public static JsonNode tree(String s) {
        try {
            return JSON.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** TransitionResult is a nested record; Jackson handles it but lists need help. */
    public static TransitionResult result(JsonNode node) {
        JsonNode r = node.has("result") ? node.get("result") : node;
        try {
            return JSON.treeToValue(r, TransitionResult.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static BaselineFit baselineFit(JsonNode node) {
        try {
            JsonNode f = node.has("fit") ? node.get("fit") : node;
            return JSON.treeToValue(f, BaselineFit.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static TangentSelection tangent(JsonNode node) {
        try {
            JsonNode t = node.has("tangent") ? node.get("tangent") : node;
            return JSON.treeToValue(t, TangentSelection.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Quantity quantity(JsonNode node) {
        try {
            return JSON.treeToValue(node, Quantity.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Diagnostic> diagnostics(JsonNode arr) {
        List<Diagnostic> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;
        arr.forEach(n -> {
            try {
                out.add(JSON.treeToValue(n, Diagnostic.class));
            } catch (Exception ignored) {
            }
        });
        return out;
    }

    public static List<UncertaintySource> uncertainty(JsonNode arr) {
        List<UncertaintySource> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;
        arr.forEach(n -> {
            try {
                out.add(JSON.treeToValue(n, UncertaintySource.class));
            } catch (Exception ignored) {
            }
        });
        return out;
    }

    public static BaselineModel baselineModel(String s) {
        return BaselineModel.valueOf(s);
    }
}
