package dsc;

import dsc.casepack.CasePackLoader;
import dsc.domain.RawPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CasepackParseTest {
    @Test
    void reverseHeatFlowHeaderIsParsedNotGuessedFromName() throws Exception {
        CasePackLoader.CsvData csv = Support.seg("seg1_heating.csv");
        assertEquals("EXO_POSITIVE", csv.declaredSign());
        assertTrue(csv.header().contains("sign=EXO_POSITIVE"));
        assertFalse(csv.points().isEmpty());
        RawPoint meltRegion = csv.points().stream()
                .filter(p -> p.tK() != null && p.tK() > 366.0 && p.tK() < 370.0)
                .findFirst().orElseThrow();
        assertTrue(meltRegion.hfW() < 0, "EXO_POSITIVE 文件内熔融峰应为负向原始热流");
    }

    @Test
    void nonUniformGridPreservesRawRepresentation() throws Exception {
        CasePackLoader.CsvData csv = Support.seg("seg3_heating_overlap.csv");
        List<RawPoint> pts = csv.points();
        double d1 = pts.get(1).tK() - pts.get(0).tK();
        double d2 = pts.get(2).tK() - pts.get(1).tK();
        double d3 = pts.get(3).tK() - pts.get(2).tK();
        assertEquals(0.4, d1, 1e-9);
        assertEquals(0.5, d2, 1e-9);
        assertEquals(0.7, d3, 1e-9);
    }

    @Test
    void saturatedPointIsExcludedButKeptInPlace() throws Exception {
        CasePackLoader.CsvData csv = Support.seg("seg3_heating_overlap.csv");
        RawPoint bad = csv.points().stream().filter(RawPoint::excluded).findFirst().orElseThrow();
        assertEquals("OVERLOAD", bad.hfRaw());
        assertNull(bad.hfW());
        long seq = bad.seq();
        assertEquals(seq, csv.points().get((int) seq - 1).seq(), "点保留在原 seq 位置");
    }

    @Test
    void coolingSegmentIsPresentAndSeparate() throws Exception {
        var pack = Support.pack();
        var seg2 = pack.segments().stream().filter(s -> s.segmentId().equals("seg2")).findFirst().orElseThrow();
        assertEquals("COOLING", seg2.programDirection());
    }
}
