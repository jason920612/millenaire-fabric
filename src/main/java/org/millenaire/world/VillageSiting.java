package org.millenaire.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Village site selection (intent doc 02 §3.1). The original ran during worldgen per chunk; on 26.2
 * we do "post-gen near the player" (coupling B) so we can read already-generated terrain.
 *
 * <p>Intent to rebuild: biome whitelist + per-type {@code max} + {@code weight} weighted-random +
 * global tag gates; min distances (village↔village / village↔lone / lone↔lone) checked against the
 * {@link MillWorldData} lightweight index; spawn-protection; area suitability (&gt;70% buildable);
 * key buildings must fit; degrade to a lone building if a village won't fit; optional hamlets.
 */
public final class VillageSiting {

	private VillageSiting() {
	}

	/** Whether {@code centre}'s surroundings are buildable enough for a village (doc 02 §3.1.6). */
	public static boolean isAppropriateArea(ServerLevel level, BlockPos centre) {
		return false; // TODO: build MillWorldInfo, require MINIMUM_USABLE_BLOCK_PERC buildable
	}

	// TODO: chooseCultureAndType(level, centre) -> weighted pick of a valid VillageType
	// TODO: passesDistanceRules(world, centre)
	// TODO: tryGenerate(level, centre) -> Optional<TownHall>  (places centre + start buildings)
	// TODO: generateHamlets(...)
}
