package org.millenaire.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.millenaire.Millenaire;
import org.millenaire.entity.MillVillagerEntity;
import org.millenaire.entity.ai.VillagerScheduler;

/**
 * Runtime facade over {@link MillWorldData}: the per-village active/inactive proximity state machine
 * (INTENT.md doc 02). A village within {@link #KEEP_ACTIVE_RADIUS} of a player is "active"; outside,
 * it's inactive. {@link TownHall#setActive} is the single transition entry point future systems hook
 * into (start/stop heavy simulation, chunk forcing, construction tick, NPC scheduler).
 */
public final class MillWorld {

	public static final int KEEP_ACTIVE_RADIUS = 200;
	private static final int REPORT_INTERVAL = 100; // ~5s

	/** Test-only: force every village active (so headless construction runs without an online player). */
	public static boolean forceActiveForTest = false;

	private static long lastReport = -REPORT_INTERVAL;

	private MillWorld() {
	}

	public static void tick(ServerLevel overworld) {
		MillWorldData data = MillWorldData.get(overworld);
		if (data.townHallCount() == 0) {
			return;
		}
		long time = overworld.getGameTime();
		boolean report = time - lastReport >= REPORT_INTERVAL;
		if (report) {
			lastReport = time;
		}

		double radiusSq = (double) KEEP_ACTIVE_RADIUS * KEEP_ACTIVE_RADIUS;
		int active = 0;
		for (TownHall t : data.townHalls()) {
			boolean shouldBeActive = forceActiveForTest || nearestPlayerDistSq(overworld, t.centre()) <= radiusSq;
			if (t.setActive(shouldBeActive)) {
				Millenaire.LOGGER.info("MillWorld: village '{}' {} (id={})",
						t.name(), shouldBeActive ? "ACTIVATED" : "deactivated", t.id());
				setVillageChunksForced(overworld, t.centre(), shouldBeActive);
			}
			if (t.isActive()) {
				active++;
				// Keep the village's chunks loaded so it actually ticks while active (chunk forcing).
				setVillageChunksForced(overworld, t.centre(), true);
				// Active behaviour: drive gradual construction (L3) and the villager scheduler (L4).
				Construction.tick(overworld, t, data);
				// The TownHall OWNS its villagers by UUID — tick exactly those, and repair any whose entity
				// is gone (e.g. lost across a reload), respawning with the same UUID so membership stays consistent.
				int missing = 0;
				for (VillagerMember m : t.villagers()) {
					Entity e = overworld.getEntity(m.id());
					if (e instanceof MillVillagerEntity villager && villager.isAlive()) {
						VillagerScheduler.tick(villager, overworld, t);
					} else if (overworld.isLoaded(m.home())) {
						// home chunk is loaded but the entity isn't anywhere -> it's gone -> repair (same UUID).
						MillVillagerEntity villager = MillVillagerEntity.spawn(overworld, m.id(), m.name(), m.type(), m.home(), forceActiveForTest);
						VillagerScheduler.tick(villager, overworld, t);
						Millenaire.LOGGER.info("MillWorld: repaired missing villager '{}' ({}) at {} in '{}'",
								m.name(), m.id(), m.home(), t.name());
					} else {
						missing++; // home chunk not loaded yet; resolves once the forced chunk loads
					}
				}
				if (report && missing > 0) {
					Millenaire.LOGGER.info("MillWorld: village '{}' has {}/{} villager(s) not yet loaded",
							t.name(), missing, t.villagers().size());
				}
			}
		}
		if (report) {
			Millenaire.LOGGER.info("MillWorld tick: {}/{} village(s) active (a player within {}m)",
					active, data.townHallCount(), KEEP_ACTIVE_RADIUS);
		}

		// Test-only checkpoint: flush construction progress frequently so a mid-build reload can be verified.
		if (forceActiveForTest && time % 20 == 0) {
			overworld.getServer().saveEverything(true, false, false);
		}
	}

	/** Chunk radius (in chunks) force-loaded around an active village's centre. */
	private static final int FORCE_CHUNK_RADIUS = 2;

	/**
	 * On server start, release any chunks left force-loaded by a previous session (#6). {@code active}
	 * is runtime-only, so a server stopped while a village was active would otherwise leave its chunks
	 * forced with no {@code true->false} transition to release them. We deterministically unforce every
	 * known village's region; the active tick re-forces the ones that should be active again.
	 */
	public static void releaseAllForcedChunksOnStart(ServerLevel overworld) {
		MillWorldData data = MillWorldData.get(overworld);
		for (TownHall t : data.townHalls()) {
			t.setActive(false);
			setVillageChunksForced(overworld, t.centre(), false);
		}
		if (data.townHallCount() > 0) {
			Millenaire.LOGGER.info("MillWorld: released leftover forced chunks for {} village(s) on startup",
					data.townHallCount());
		}
	}

	private static void setVillageChunksForced(ServerLevel level, BlockPos centre, boolean forced) {
		int ccx = centre.getX() >> 4;
		int ccz = centre.getZ() >> 4;
		for (int dx = -FORCE_CHUNK_RADIUS; dx <= FORCE_CHUNK_RADIUS; dx++) {
			for (int dz = -FORCE_CHUNK_RADIUS; dz <= FORCE_CHUNK_RADIUS; dz++) {
				level.setChunkForced(ccx + dx, ccz + dz, forced);
			}
		}
	}

	private static double nearestPlayerDistSq(ServerLevel level, BlockPos centre) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer p : level.players()) {
			double d = p.distanceToSqr(centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5);
			if (d < best) {
				best = d;
			}
		}
		return best;
	}
}
