package org.millenaire.content.building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.content.block.LogicalBlockMapping;
import org.millenaire.content.block.PointType;

/**
 * Bridges a decoded {@link BuildingPlan} to real blocks in the world — the first concrete link
 * from L1 (content) to the in-world systems (L2/L3). Places one level's grid via the production
 * {@link ServerLevel#setBlock} path. Orientation/upgrade-merging are L3 concerns; this is the
 * straight placement used to prove the schematic &rarr; world pipeline.
 */
public final class BuildingPlacer {

	private BuildingPlacer() {
	}

	/**
	 * Place {@code plan}'s given level at {@code origin}.
	 * Grid axes: {@code grid[floor][z][x]}; world pos = origin + (x, floor + startLevel, z).
	 *
	 * @return number of blocks placed
	 */
	public static int place(ServerLevel level, BlockPos origin, BuildingPlan plan, int levelIndex) {
		BuildingPlan.Level lvl = plan.levels().get(levelIndex);
		PointType[][][] grid = lvl.grid();
		int placed = 0;
		for (int floor = 0; floor < lvl.nbfloors(); floor++) {
			for (int z = 0; z < plan.length(); z++) {
				for (int x = 0; x < plan.width(); x++) {
					PointType pt = grid[floor][z][x];
					if (pt == null || pt.isEmpty() || pt.isSpecial()) {
						continue;
					}
					BlockState state = LogicalBlockMapping.resolve(pt.name());
					if (state == null || state.isAir()) {
						continue; // unresolved logical block (coupling A coverage gap) — skip for now
					}
					BlockPos pos = origin.offset(x, floor + plan.startLevel(), z);
					level.setBlock(pos, state, Block.UPDATE_ALL);
					placed++;
				}
			}
		}
		return placed;
	}
}
