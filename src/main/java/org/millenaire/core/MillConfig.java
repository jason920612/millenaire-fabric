package org.millenaire.core;

/**
 * Cross-subsystem constants and tunables (intent docs 01/02/06). Currently compile-time constants;
 * TODO load the server/world rules from {@code config-server.txt} and client prefs from
 * {@code config.txt} (doc 04 splits client vs server config).
 */
public final class MillConfig {

	private MillConfig() {
	}

	/** Player within this radius of a village centre keeps it active + chunk-forced (doc 02 §3.3). */
	public static final int KEEP_ACTIVE_RADIUS = 200;
	/** Range within which a village can sense raids / send messages (doc 02 §2, §3.4). */
	public static final int BACKGROUND_RADIUS = 2000;

	// Siting (doc 02 §3.1) — TODO confirm exact defaults against config-server.txt.
	public static final int MIN_DISTANCE_BETWEEN_VILLAGES = 250;
	public static final int MIN_DISTANCE_VILLAGE_TO_LONE = 100;
	public static final int MIN_DISTANCE_BETWEEN_LONE = 80;
	public static final int SPAWN_PROTECTION_RADIUS = 0;
	public static final int VILLAGE_RADIUS = 40;
	/** Minimum buildable-block fraction for an area to be village-suitable (doc 02 §3.1). */
	public static final double MINIMUM_USABLE_BLOCK_PERC = 0.70;

	// Goals (doc 01)
	public static final int GETTOOL_PRIORITY = 100; // very high: fetch tools before working (doc 01 §10)

	// Diplomacy / reputation (doc 02 §3.4/3.5, doc 06)
	public static final int RELATION_MIN = -100;
	public static final int RELATION_MAX = 100;
	public static final int MIN_REPUTATION_FOR_TRADE = -1024;
}
