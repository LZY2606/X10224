package com.materials.dsc.store;

import com.materials.dsc.model.Model.Baseline;

/** Thrown when two users edit from the same version; carries the current row
 *  so the conflict is resolvable instead of silently overwritten. */
public class ConflictException extends RuntimeException {
    public final String entityId;
    public final int expectedVersion;
    public final Baseline current;

    public ConflictException(String entityId, int expectedVersion, Baseline current) {
        super("conflict on " + entityId + ": expected version " + expectedVersion
                + " but current is " + (current == null ? "<deleted>" : current.version()));
        this.entityId = entityId;
        this.expectedVersion = expectedVersion;
        this.current = current;
    }
}
