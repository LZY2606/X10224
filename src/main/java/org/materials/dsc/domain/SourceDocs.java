package org.materials.dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The six kinds of input that must be archived separately.  Each is a
 * versioned document with its own identity; no field is inferred from another
 * or from a file name.
 */
public final class SourceDocs {

    private SourceDocs() {
    }

    public static final String INSTRUMENT_OUTPUT = "instrument_output";
    public static final String SAMPLE_MASS = "sample_mass";
    public static final String SCAN_RATE = "scan_rate";
    public static final String ATMOSPHERE = "atmosphere";
    public static final String TEMPERATURE_CALIBRATION = "temperature_calibration";
    public static final String TANGENT_SELECTION = "tangent_selection";

    public static final List<String> ALL = List.of(
            INSTRUMENT_OUTPUT, SAMPLE_MASS, SCAN_RATE, ATMOSPHERE,
            TEMPERATURE_CALIBRATION, TANGENT_SELECTION);

    public abstract static class SourceDoc {
        public String id;
        public String segmentId;
        public String kind;
        public int version = 1;
        public String createdAt;
        public String author;
        public String note = "";
        /** Free-form key/values exactly as imported; never normalized in place. */
        public Map<String, String> raw = new LinkedHashMap<>();

        public abstract Map<String, Object> toMap();

        protected Map<String, Object> baseMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("segmentId", segmentId);
            m.put("kind", kind);
            m.put("version", version);
            m.put("createdAt", createdAt);
            m.put("author", author);
            m.put("note", note);
            m.put("raw", raw);
            return m;
        }
    }

    public static final class InstrumentOutput extends SourceDoc {
        public String fileName;
        public String hfUnit;
        public String tempUnit;
        /** Explicit axis convention, e.g. "exo_up" / "endo_up". */
        public String signConvention;
        public List<RawPoint> points = new ArrayList<>();

        public InstrumentOutput() {
            kind = INSTRUMENT_OUTPUT;
        }

        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> m = baseMap();
            m.put("fileName", fileName);
            m.put("hfUnit", hfUnit);
            m.put("tempUnit", tempUnit);
            m.put("signConvention", signConvention);
            m.put("pointCount", points.size());
            return m;
        }
    }

    public static final class SampleMass extends SourceDoc {
        public String massValue;
        public String massUnit;
        /** Monotonic mass revision; revisions invalidate every mass-normalized derivation. */
        public int massRevision = 1;

        public SampleMass() {
            kind = SAMPLE_MASS;
        }

        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> m = baseMap();
            m.put("massValue", massValue);
            m.put("massUnit", massUnit);
            m.put("massRevision", massRevision);
            return m;
        }
    }

    public static final class ScanRate extends SourceDoc {
        public String rateValue;
        public String rateUnit;

        public ScanRate() {
            kind = SCAN_RATE;
        }

        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> m = baseMap();
            m.put("rateValue", rateValue);
            m.put("rateUnit", rateUnit);
            return m;
        }
    }

    public static final class Atmosphere extends SourceDoc {
        public String gas;
        public String flowValue;
        public String flowUnit;

        public Atmosphere() {
            kind = ATMOSPHERE;
        }

        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> m = baseMap();
            m.put("gas", gas);
            m.put("flowValue", flowValue);
            m.put("flowUnit", flowUnit);
            return m;
        }
    }

    public static final class TemperatureCalibration extends SourceDoc {
        /** Calibration correction: calibratedC = rawC * slope + offset. */
        public String offsetC;
        public String slope;
        public String standard;

        public TemperatureCalibration() {
            kind = TEMPERATURE_CALIBRATION;
        }

        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> m = baseMap();
            m.put("offsetC", offsetC);
            m.put("slope", slope);
            m.put("standard", standard);
            return m;
        }
    }

    /**
     * User-selected tangent: anchor pairs (raw temperature, raw heat flow) for
     * the pre- and post-transition tangents.  Stored independently of the
     * instrument output; changing it only affects its own dependency subtree.
     */
    public static final class TangentAnchor {
        /** reference into the archived instrument output point sequence */
        public int pointIndex;
        /** raw representations copied from the instrument output, never converted in place */
        public String rawTemperature;
        public String rawHeatFlow;

        public TangentAnchor() {
        }

        public TangentAnchor(int pointIndex, String rawTemperature, String rawHeatFlow) {
            this.pointIndex = pointIndex;
            this.rawTemperature = rawTemperature;
            this.rawHeatFlow = rawHeatFlow;
        }
    }

    public static final class TangentSelection extends SourceDoc {
        public String eventId;
        public List<TangentAnchor> preAnchors = new ArrayList<>();
        public List<TangentAnchor> postAnchors = new ArrayList<>();

        public TangentSelection() {
            kind = TANGENT_SELECTION;
        }

        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> m = baseMap();
            m.put("eventId", eventId);
            m.put("preAnchorCount", preAnchors.size());
            m.put("postAnchorCount", postAnchors.size());
            return m;
        }
    }
}
