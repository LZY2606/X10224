package io.dsc.engine;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dsc.model.Event;

/** Helpers for the machine-readable event payload vocabulary. */
public final class Events {
    private Events() {}

    public static ObjectNode payload(Event ev) { return ev.payload(); }

    public static ArrayNode arrayOf(ObjectNode node, String field) {
        if (node.has(field)) return (ArrayNode) node.get(field);
        return node.putArray(field);
    }
}
