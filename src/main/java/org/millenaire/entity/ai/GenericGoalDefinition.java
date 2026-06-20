package org.millenaire.entity.ai;

/**
 * A data-driven goal definition (INTENT.md doc 01: {@code millenaire/goals/generic*} — each is a goal
 * instance). For this first slice we capture the shape ({@code key/priority/duration/destinationTag});
 * real values are still placeholders until the {@code goals/} definition files are parsed (issue #4).
 * The point of the slice is that a villager's <b>candidate</b> goals come from its type's content, not
 * a hard-coded global list.
 *
 * @param key            goal key as referenced by villager types ({@code goal=<key>})
 * @param priority       selection priority (higher wins)
 * @param duration       ticks to run before the goal finishes
 * @param destinationTag building tag to travel to ({@code ""} = none; resolution is future work)
 */
public record GenericGoalDefinition(String key, int priority, int duration, String destinationTag) {

	/** Default placeholder definition for a goal key until the real {@code goals/} files are parsed. */
	public static GenericGoalDefinition placeholder(String key) {
		return new GenericGoalDefinition(key, 35, 100, "");
	}
}
