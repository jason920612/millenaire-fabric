package org.millenaire.entity.ai;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.Millenaire;
import org.millenaire.content.MillContent;
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
		List<VillagerGoal> candidates = candidatesFor(v, townHall);

		// Leisure yields to work (intent doc 01 §2.2.5 / §3.6): if any non-leisure goal is possible,
		// drop all leisure goals from the selection — so villagers only chat/drink/pray/rest when idle.
		boolean workAvailable = false;
		for (VillagerGoal g : candidates) {
			if (!g.isLeisure() && g.isPossible(v, level, townHall)) {
				workAvailable = true;
				break;
			}
		}

		VillagerGoal best = null;
		int bestPriority = Integer.MIN_VALUE;
		for (VillagerGoal g : candidates) {
			if (workAvailable && g.isLeisure()) {
				continue;
			}
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

	/**
	 * The candidate goals for this villager: the hard-coded fallbacks PLUS the data-driven goals from
	 * its villager type's {@code goal=} list (INTENT.md doc 01) — so different professions consider
	 * different goals, sourced from content rather than a global hard-coded set.
	 */
	private static List<VillagerGoal> candidatesFor(MillVillagerEntity v, TownHall townHall) {
		List<VillagerGoal> list = new ArrayList<>();
		VillagerGoals.fallbacks().forEach(list::add);
		MillContent.culture(townHall.culture())
				.flatMap(c -> c.villagerType(v.getVillagerType()))
				.ifPresent(t -> t.goals().forEach(key -> list.add(VillagerGoals.generic(key))));
		return list;
	}
}
