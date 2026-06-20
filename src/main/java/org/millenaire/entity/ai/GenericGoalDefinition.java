package org.millenaire.entity.ai;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.millenaire.content.Dsl;
import org.millenaire.content.economy.GoodStack;

/**
 * A data-driven goal definition parsed from {@code millenaire/goals/**} (INTENT.md doc 01 — each file
 * is a goal instance). Carries the real {@code priority} and {@code duration} (the file's milliseconds
 * converted to ticks) and a destination ({@code townhall} / a building {@code tag} / {@code ""} = the
 * villager's house). Full behaviour (crafting in/out, soil, harvest…) is resolved later (issue #3).
 *
 * @param key            goal key as referenced by villager types ({@code goal=<key>}), lowercased
 * @param priority       selection priority (higher wins)
 * @param duration       ticks to run before the goal finishes
 * @param destinationTag {@code "townhall"}, a building {@code buildingTag}, or {@code ""} for the house
 * @param requiredTag    extra tag the destination building must also have ({@code ""} = none) — models
 *                       the upgrade/unlock gating (e.g. {@code makecalva} needs a {@code cider} building)
 * @param inputs         goods consumed per craft action (genericcrafting/cooking)
 * @param outputs        goods produced per craft action
 * @param limitGood      good whose count in the building/townhall caps production ({@code ""} = no cap)
 * @param limitMax       max count of {@code limitGood} before the goal stops
 */
public record GenericGoalDefinition(String key, int priority, int duration, String destinationTag, String requiredTag,
		List<GoodStack> inputs, List<GoodStack> outputs, String limitGood, int limitMax) {

	public static final String TOWNHALL = "townhall";

	public boolean isCrafting() {
		return !outputs.isEmpty();
	}

	/** Default placeholder for a goal key with no {@code goals/} definition (e.g. hard-coded keys). */
	public static GenericGoalDefinition placeholder(String key) {
		return new GenericGoalDefinition(key.toLowerCase(Locale.ROOT), 35, 100, "", "",
				List.of(), List.of(), "", Integer.MAX_VALUE);
	}

	/** Parse one {@code goals/**.txt} definition file. */
	public static GenericGoalDefinition parse(Path file) throws IOException {
		String key = file.getFileName().toString().replaceFirst("\\.txt$", "").toLowerCase(Locale.ROOT);
		Dsl.Record r = Dsl.parseEqualsFile(Dsl.readLines(file));
		int priority = r.firstInt("priority", 30);
		int durationMs = r.firstInt("duration", -1);
		int durationTicks = durationMs > 0 ? Math.max(20, durationMs / 50) : 100; // 50ms per tick
		// Destination: townhallgoal=true -> the centre; otherwise the building with `buildingTag`
		// (filtered by `requiredTag`); empty -> the villager's house. (`tag` is not a real field.)
		String buildingTag = r.first("buildingtag").orElse("").toLowerCase(Locale.ROOT);
		String requiredTag = r.first("requiredtag").orElse("").toLowerCase(Locale.ROOT);
		String destination = r.firstBool("townhallgoal", false) ? TOWNHALL : buildingTag;

		List<GoodStack> inputs = r.all("input").stream().map(GoodStack::parse).toList();
		List<GoodStack> outputs = r.all("output").stream().map(GoodStack::parse).toList();
		String limitGood = "";
		int limitMax = Integer.MAX_VALUE;
		if (r.has("buildinglimit")) {
			GoodStack bl = GoodStack.parse(r.first("buildinglimit").get());
			limitGood = bl.good();
			limitMax = bl.qty();
		}
		return new GenericGoalDefinition(key, priority, durationTicks, destination, requiredTag,
				inputs, outputs, limitGood, limitMax);
	}
}
