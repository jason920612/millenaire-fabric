package org.millenaire.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.Millenaire;

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
			}
			if (t.isActive()) {
				active++;
				// Active behaviour: drive gradual construction (L3). NPC/trade ticks attach here next.
				Construction.tick(overworld, t, data);
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
