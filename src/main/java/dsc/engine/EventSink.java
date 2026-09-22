package dsc.engine;

import dsc.model.DomainEvent;

import java.util.Map;

public interface EventSink {
    DomainEvent emit(String type, Map<String, Object> payload);

    DomainEvent emit(String type, Map<String, Object> payload, String derivedFromEventId);

    long nextSeq();

    String branch();
}
