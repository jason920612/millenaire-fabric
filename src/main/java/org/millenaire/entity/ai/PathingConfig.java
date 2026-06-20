package org.millenaire.entity.ai;

/**
 * Per-goal pathfinding settings (intent doc 01 §6). The original carried a custom A-star / JPS
 * {@code AStarConfig}; here we keep the <i>semantics</i> (search range / width / whether the path may
 * open doors or break blocks) to map onto a 26.2 {@code PathNavigation} + custom {@code NodeEvaluator}.
 * TODO: wire these into the actual navigation when goals issue movement.
 */
public record PathingConfig(int searchRange, int width, boolean canOpenDoors, boolean canBreakBlocks) {

	/** Default tight search (most goals). */
	public static final PathingConfig TIGHT = new PathingConfig(64, 1, true, false);
	/** Wider search (herding animals, chopping whole trees). */
	public static final PathingConfig WIDE = new PathingConfig(128, 3, true, false);
	/** Construction: precise, may place/break blocks to reach the site. */
	public static final PathingConfig BUILDING = new PathingConfig(96, 1, true, true);
}
