package dsc.store;

import dsc.model.Event;

public record CommitResult(Event storedEvent) {
}
