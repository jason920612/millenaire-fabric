package org.millenaire.world;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.Millenaire;
import org.millenaire.content.MillContent;
import org.millenaire.content.block.LogicalBlockMapping;
import org.millenaire.content.block.PointType;
import org.millenaire.content.building.BuildingPlan;

/**
 * L3 gradual construction (INTENT.md doc 03): an <b>active</b> Town Hall builds its buildings block
 * by block, one building at a time, in two passes (main structure, then {@code secondStep} blocks
 * like doors/torches/beds). Progress lives in {@link BuildingProject} (persistent cursor), so a
 * reload resumes exactly where it stopped — never a one-shot paste.
 *
 * <p>Resource gating is a minimal stub for now (always available); the villager-driven resource
 * economy replaces it later.
 */
public final class Construction {

	private static final int BLOCKS_PER_TICK = 16;

	private Construction() {
	}

	/** Advance construction for one active town hall (one building per tick). */
	public static void tick(ServerLevel level, TownHall townHall, MillWorldData world) {
		for (BuildingProject project : townHall.buildings()) {
			if (project.isDone() || project.isBlocked()) {
				continue;
			}
			advance(level, townHall.culture(), project);
			world.markChanged();
			return; // build one building at a time, like the original
		}
	}

	private static void advance(ServerLevel level, String culture, BuildingProject project) {
		Optional<BuildingPlan> planOpt = MillContent.building(culture, project.key() + "_" + project.variant());
		if (planOpt.isEmpty()) {
			// Surface the data error instead of silently marking the building "done" with nothing built.
			Millenaire.LOGGER.warn("Construction BLOCKED: plan '{}_{}' not found (culture '{}') — check content path / saved key",
					project.key(), project.variant(), culture);
			project.setBlocked(true);
			return;
		}
		BuildingPlan plan = planOpt.get();
		int levelIndex = Math.min(project.level(), plan.levels().size() - 1);
		BuildingPlan.Level lvl = plan.levels().get(levelIndex);
		PointType[][][] grid = lvl.grid();
		int floors = lvl.nbfloors();
		int length = plan.length();
		int width = plan.width();
		int total = floors * length * width;

		int perTick = MillWorld.forceActiveForTest ? 2 : BLOCKS_PER_TICK; // slower under test for a catchable reload window
		int placed = 0;
		while (placed < perTick) {
			if (project.cursor() >= total) {
				if (project.pass() == 0) {
					project.setPass(1);
					project.setCursor(0);
					continue;
				}
				project.setDone(true);
				Millenaire.LOGGER.info("Construction: '{}_{}' COMPLETE at {} (orientation {})",
						project.key(), project.variant(), project.origin(), project.orientation());
				break;
			}
			int idx = project.cursor();
			project.setCursor(idx + 1);

			int floor = idx / (length * width);
			int rem = idx % (length * width);
			int z = rem / width;
			int x = rem % width;
			PointType pt = grid[floor][z][x];
			if (pt == null || pt.isEmpty() || pt.isSpecial()) {
				continue;
			}
			boolean second = pt.secondStep();
			if ((project.pass() == 0) == second) {
				continue; // pass 0 skips secondStep blocks; pass 1 places only them
			}
			BlockState state = LogicalBlockMapping.resolve(pt.name());
			if (state == null || state.isAir()) {
				continue;
			}
			if (!hasResources(state)) {
				project.setCursor(idx); // not enough resources: pause here, retry next tick
				break;
			}
			int[] off = rotate(x, z, width, length, project.orientation());
			BlockPos pos = project.origin().offset(off[0], floor + plan.startLevel(), off[1]);
			level.setBlock(pos, state, Block.UPDATE_ALL);
			placed++;
		}
	}

	/** Minimal resource-gating stub (always available). Replaced by villager-driven economy later. */
	private static boolean hasResources(BlockState state) {
		return true;
	}

	/** Rotate a footprint cell (x,z) by orientation 0..3 (90° steps), anchored near the origin. */
	private static int[] rotate(int x, int z, int width, int length, int orientation) {
		return switch (orientation & 3) {
			case 1 -> new int[]{length - 1 - z, x};
			case 2 -> new int[]{width - 1 - x, length - 1 - z};
			case 3 -> new int[]{z, width - 1 - x};
			default -> new int[]{x, z};
		};
	}
}
