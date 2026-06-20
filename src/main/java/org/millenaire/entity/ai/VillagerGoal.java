package org.millenaire.entity.ai;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.entity.MillVillagerEntity;
import org.millenaire.world.TownHall;

/**
 * A Millénaire villager goal (INTENT.md doc 01): a <b>stateless singleton</b> — all per-villager
 * state lives on the {@link MillVillagerEntity} (its {@code goalKey}, target and start time). The
 * scheduler runs exactly one goal per villager at a time, choosing the highest {@link #priority}
 * among the {@link #isPossible} goals. This is deliberately NOT vanilla's parallel
 * {@code GoalSelector} model.
 */
public interface VillagerGoal {

	String key();

	boolean isPossible(MillVillagerEntity v, ServerLevel level, TownHall townHall);

	/** Higher wins. Implementations add a small random jitter to avoid per-tick flapping. */
	int priority(MillVillagerEntity v, ServerLevel level, TownHall townHall);

	/** Called once when this goal becomes the villager's current goal. */
	default void start(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
	}

	/** Called each tick while current. */
	default void tick(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
	}

	/** When true, the scheduler is free to pick a new goal. */
	boolean isFinished(MillVillagerEntity v, ServerLevel level, TownHall townHall);
}
