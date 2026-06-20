package org.millenaire.world;

/**
 * Per-village terrain cognition cache (intent doc 02 §2.4): a snapshot of the land around a village
 * centre (topGround / constructionHeight / canBuild / water / tree / danger / buildingLoc / path …).
 * <b>Not persisted</b> — recomputed from world blocks (refresh ~every 1000 ticks). It also defines the
 * village area bounds (mapStartX/width/length) shared by siting, pathing and the map. TODO: implement;
 * note the chicken-and-egg with chunk loading on thaw (doc 02 §8.9).
 */
public final class MillWorldInfo {

	private MillWorldInfo() {
	}

	// TODO: compute(level, centre, radius), isBuildable(x,z), surfaceHeight(x,z), bounds()
}
