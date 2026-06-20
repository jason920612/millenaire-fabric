package org.millenaire.building;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import org.millenaire.content.block.PointType;
import org.millenaire.content.building.BuildingPlan;
import org.millenaire.world.BuildingProject;

/**
 * Builds a {@link ResManager} for a placed building by scanning its decoded schematic for special
 * points (intent doc 03/01 §5.3): {@code craftingPos}, {@code mainChest}, {@code sellingPos},
 * {@code sleepingPos}, … Their world positions are computed from the building's origin/orientation so
 * goals can ask "where do I stand" rather than hard-coding coordinates. Cached per building origin.
 */
public final class BuildingResManagers {

	private static final Map<BlockPos, ResManager> CACHE = new ConcurrentHashMap<>();

	private BuildingResManagers() {
	}

	public static ResManager forBuilding(BuildingPlan plan, BuildingProject project) {
		return CACHE.computeIfAbsent(project.origin(),
				o -> extract(plan, project.level(), o, project.orientation()));
	}

	static ResManager extract(BuildingPlan plan, int levelIndex, BlockPos origin, int orientation) {
		ResManager rm = new ResManager();
		if (plan.levels().isEmpty()) {
			return rm;
		}
		BuildingPlan.Level lvl = plan.levels().get(Math.min(levelIndex, plan.levels().size() - 1));
		PointType[][][] grid = lvl.grid();
		int width = plan.width();
		int length = plan.length();
		for (int floor = 0; floor < lvl.nbfloors(); floor++) {
			for (int z = 0; z < length; z++) {
				for (int x = 0; x < width; x++) {
					PointType pt = grid[floor][z][x];
					if (pt == null || pt.isEmpty() || !pt.isSpecial()) {
						continue;
					}
					int[] off = rotate(x, z, width, length, orientation);
					rm.add(pt.name(), origin.offset(off[0], floor + plan.startLevel(), off[1]));
				}
			}
		}
		return rm;
	}

	/** Same footprint rotation as construction placement. */
	private static int[] rotate(int x, int z, int width, int length, int orientation) {
		return switch (orientation & 3) {
			case 1 -> new int[]{length - 1 - z, x};
			case 2 -> new int[]{width - 1 - x, length - 1 - z};
			case 3 -> new int[]{z, width - 1 - x};
			default -> new int[]{x, z};
		};
	}
}
