package ca.bc.gov.nrs.frep.struct.v1.frep;

/**
 * Read-only computed values shown on the FREP211 Stratum Summary screen but not stored on the
 * stratum row: {@code nar} (Net Area Reforested, from the site) and {@code plotsCompleted} (count of
 * the stratum's plots). Mirrors the legacy {@code FREP_211_BIOSTRATUM.get} {@code p_nar} /
 * {@code p_plots_completed} out-params.
 */
public record StratumComputed(String nar, String plotsCompleted) {}
