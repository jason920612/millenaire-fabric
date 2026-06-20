package org.millenaire.progression;

/**
 * A quest definition (intent doc 05). Parsed from {@code millenaire/quests/**}: quest-level header gates
 * ({@code minreputation}/{@code chanceperhour}/{@code maxsimultaneous}/tag gates), {@code definevillager}
 * roles with spatial relations (samevillage/samehouse/nearbyvillage/anyvillage), and ordered steps
 * (duration / required·reward goods / reputation / tag set·clear). World quest lines chain via player
 * tags; descriptions expand variables ({@code $name}, {@code $key_villagename$}, direction/distance) so
 * villagers "speak to you and point the way". TODO: parser + runtime ({@link QuestInstance}).
 */
public final class Quest {

	private Quest() {
	}

	// TODO: static Quest parse(Path); header gates; List<QuestStep> steps; definevillager roles
}
