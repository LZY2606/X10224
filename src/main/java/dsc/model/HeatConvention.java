package dsc.model;

/**
 * Sign convention of the heat-flow axis. This MUST come from instrument
 * metadata (file header), never from the file name.
 */
public enum HeatConvention { ENDO_UP, EXO_UP, UNKNOWN }
