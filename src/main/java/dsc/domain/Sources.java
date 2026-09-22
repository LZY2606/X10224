package dsc.domain;

import dsc.domain.Enums.*;
import java.util.ArrayList;
import java.util.List;

/** Versioned, separately-filed direct sources. Old versions remain readable forever. */
public final class Sources {
    private Sources() {}

    public static final class MassVersion {
        public String id, eventId, sampleId, note;
        public Quantity massMg;
        public int seq;
        public MassVersion() {}
    }

    public static final class ScanRateVersion {
        public String id, eventId, traceId, note;
        public Quantity rateKPerMin;
        public int seq;
        public ScanRateVersion() {}
    }

    public static final class AtmosphereVersion {
        public String id, eventId, traceId, description;
        public int seq;
        public AtmosphereVersion() {}
    }

    public static final class CalibrationVersion {
        public String id, eventId, note, referenceStandard;
        public Quantity offsetC;
        public int seq;
        public CalibrationVersion() {}
    }

    public static final class TangentSelection {
        public String id, eventId, traceId, candidateId, note;
        public List<String> anchorRows = new ArrayList<>();
        public int seq;
        public TangentSelection() {}
    }
}
