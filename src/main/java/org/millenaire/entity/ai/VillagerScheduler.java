package org.millenaire.entity.ai;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.Millenaire;
import org.millenaire.entity.MillVillagerEntity;
import org.millenaire.world.TownHall;

/**
 * The villager "brain" (INTENT.md doc 01): each tick, keep running the current {@code goalKey}; when
 * it finishes or becomes impossible, pick the highest-{@link VillagerGoal#priority} possible goal and
 * switch. One goal at a time, emergent behaviour from gates + priorities — no behaviour tree, no
 * vanilla parallel {@code GoalSelector}.
 *
 * <p>Only called for villagers of <b>active</b> villages (driven from {@code MillWorld} active tick).
 */
public final class VillagerScheduler {

	private VillagerScheduler() {
	}

	public static void tick(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		VillagerGoal current = VillagerGoals.byKey(v.getGoalKey());

		boolean needNew = current == null
				|| !current.isPossible(v, level, townHall)
				|| current.isFinished(v, level, townHall);

		if (needNew) {
			VillagerGoal best = selectBest(v, level, townHall);
			if (best != null && !best.key().equals(v.getGoalKey())) {
				v.setGoalKey(best.key());
				v.setGoalStartTime(level.getGameTime());
				v.setGoalTarget(null);
				best.start(v, level, townHall);
				if (Millenaire.LOG_VILLAGER_GOALS) {
					Millenaire.LOGGER.info("villager {} ({}): goal -> {}",
							v.getId(), v.getName().getString(), best.key());
				}
				current = best;
			} else if (best != null) {
				// same goal selected again: restart it
				v.setGoalStartTime(level.getGameTime());
				best.start(v, level, townHall);
				current = best;
			}
		}

		if (current != null) {
			current.tick(v, level, townHall);
		}
	}

	private static VillagerGoal selectBest(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		VillagerGoal best = null;
		int bestPriority = Integer.MIN_VALUE;
		for (VillagerGoal g : VillagerGoals.all()) {
			if (!g.isPossible(v, level, townHall)) {
				continue;
			}
			int p = g.priority(v, level, townHall);
			if (p > bestPriority) {
				bestPriority = p;
				best = g;
			}
		}
		return best;
	}
}
