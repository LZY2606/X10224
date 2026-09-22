package dsc.engine;

import dsc.model.DomainEvent;

import java.util.List;

public record EngineOutcome(List<DomainEvent> events, String conflictWithEvent) {
    public boolean conflict() {
        return conflictWithEvent != null;
    }
}
