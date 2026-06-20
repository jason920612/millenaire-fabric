package org.millenaire.entity.ai.goals;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.entity.MillVillagerEntity;
import org.millenaire.entity.ai.VillagerGoal;
import org.millenaire.world.TownHall;

/**
 * Hard-coded sleep goal (intent doc 01 §2.4: auto-appended to every villager type; the only goal
 * normally possible at night). TODO: travel to the home building's sleepingPos (ResManager), lie down,
 * run the once-per-night action; use 26.2 bed occupancy / pose.
 */
public final class SleepGoal implements VillagerGoal {

	public static final String KEY = "sleep";

	@Override
	public String key() {
		return KEY;
	}

	@Override
	public boolean isPossible(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		return level.isDarkOutside(); // TODO: also require a free bed at home
	}

	@Override
	public int priority(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		return 60;
	}

	@Override
	public boolean isFinished(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		return level.isBrightOutside();
	}
}
