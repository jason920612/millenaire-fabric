package org.millenaire.entity.ai.combat;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.entity.MillVillagerEntity;
import org.millenaire.entity.ai.VillagerGoal;
import org.millenaire.world.TownHall;

/**
 * Combat goals (intent doc 01 §7): combat <b>pre-empts</b> ordinary routine and is driven top-down by
 * the village's {@code underAttack} state, with roles chosen by villager tags (raider / helpinattacks /
 * civilian). Skeletons only — TODO: target selection (raider vs defender), defendingPos rallying,
 * bow vs melee by {@code archer} tag, {@code defensive} leash range, and clearing on raid end.
 */
public final class CombatGoals {

	private CombatGoals() {
	}

	/** {@code helpinattacks} villagers rally to defend when the village is under attack. */
	public static final class DefendVillage implements VillagerGoal {
		@Override
		public String key() {
			return "defendvillage";
		}

		@Override
		public boolean isPossible(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
			return false; // TODO: townHall.isUnderAttack() && villager has helpinattacks tag
		}

		@Override
		public int priority(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
			return 1000; // combat pre-empts everything
		}

		@Override
		public boolean isFinished(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
			return true; // TODO: finished when the village is no longer under attack
		}
	}

	/** Raiders attack the village's defenders. */
	public static final class RaidVillage implements VillagerGoal {
		@Override
		public String key() {
			return "raidvillage";
		}

		@Override
		public boolean isPossible(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
			return false; // TODO: villager has raider tag and a target exists
		}

		@Override
		public int priority(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
			return 1000;
		}

		@Override
		public boolean isFinished(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
			return true;
		}
	}

	/** Civilians hide when the village is under attack. */
	public static final class Hide implements VillagerGoal {
		@Override
		public String key() {
			return "hide";
		}

		@Override
		public boolean isPossible(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
			return false; // TODO: townHall.isUnderAttack() && villager is a civilian
		}

		@Override
		public int priority(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
			return 900;
		}

		@Override
		public boolean isFinished(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
			return true;
		}
	}
}
