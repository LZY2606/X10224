package dsc.model;

/** Outcome state carried by a journal event, including deliberately rejected decisions. */
public enum DecisionState {
    ACCEPTED, REJECTED, PENDING, INFORMATIONAL
}
