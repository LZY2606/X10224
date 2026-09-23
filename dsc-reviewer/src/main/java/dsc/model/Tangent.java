package dsc.model;

import dsc.qty.Qty;

/**
 * A user-chosen tangent line on one trace, stored as a provenanced source object.
 * Slope/intercept are expressed in trace-native heat-flow per kelvin and heat flow.
 */
public record Tangent(String id, int version, String traceId, String label,
                      Qty slope, Qty intercept, String createdBy, String importedAt) {
}
