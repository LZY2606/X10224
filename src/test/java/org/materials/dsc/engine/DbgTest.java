package org.materials.dsc.engine;

import org.junit.jupiter.api.Test;
import org.materials.dsc.domain.*;
import org.materials.dsc.io.CasePackLoader;
import org.materials.dsc.store.EventStore;

class DbgTest {
    @Test
    void dbg() {
        EngineService engine = new EngineService(EventStore.memory());
        CaseState st = CasePackLoader.seedMainCase(engine);
        System.out.println("segments: " + st.segments.size());
        for (Segment seg : st.segments.values()) {
            System.out.println("SEG " + seg.id() + " " + seg.direction() + " idx "
                    + seg.startIndex() + ".." + seg.endIndex()
                    + " T " + seg.tStartCelsius() + ".." + seg.tEndCelsius());
        }
        System.out.println("transitions: " + st.transitions.size());
        for (Transition t : st.transitions.values()) {
            System.out.println("TR " + t.id() + " " + t.status() + " sign=" + t.thermalSign()
                    + " lo=" + t.loIndex() + " pk=" + t.peakIndex() + " hi=" + t.hiIndex());
        }
        InstrumentOutput out = st.outputs.values().iterator().next();
        for (Diagnostic d : out.diagnostics()) {
            System.out.println("OUT " + d.code() + " " + d.message());
        }
    }
}
