package dsc.store;

import dsc.model.Branch;
import dsc.model.Derivation;
import dsc.model.Event;
import dsc.model.Interval;
import dsc.model.Source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything that becomes visible together when one event is committed. */
public final class CommitUnit {

    public record Mark(String caseKey, String mark, long atSeq) {
    }

    public Event event;
    public final List<Source> sources = new ArrayList<>();
    public final List<Interval> intervals = new ArrayList<>();
    public final Map<Derivation, String> derivations = new LinkedHashMap<>();
    public final List<Branch> branches = new ArrayList<>();
    public Branch pointer;
    public Mark mark;
}
