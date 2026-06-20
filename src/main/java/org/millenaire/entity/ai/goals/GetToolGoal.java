package org.millenaire.entity.ai.goals;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.core.MillConfig;
import org.millenaire.entity.MillVillagerEntity;
import org.millenaire.entity.ai.VillagerGoal;
import org.millenaire.world.TownHall;

/**
 * Hard-coded "fetch a missing tool" goal (intent doc 01 §2.4 / §10): auto-appended when a villager
 * type declares {@code toolsNeeded}; very high priority so villagers get a tool before working.
 * TODO: check the villager actually lacks a required tool (and has none better), travel to a shop,
 * take the tool from stock.
 */
public final class GetToolGoal implements VillagerGoal {

	public static final String KEY = "gettool";

	@Override
	public String key() {
		return KEY;
	}

	@Override
	public boolean isPossible(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		return false; // TODO: true only when a required tool is missing and obtainable
	}

	@Override
	public int priority(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		return MillConfig.GETTOOL_PRIORITY;
	}

	@Override
	public boolean isFinished(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		return true; // TODO: finished once the tool is in hand
	}
}
