package org.millenaire.entity.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.millenaire.Millenaire;

/**
 * Global registry of {@link GenericGoalDefinition}s loaded from {@code millenaire/goals/**}. These give
 * the data-driven goals their real {@code priority}/{@code duration}/destination (issue #4). Goals
 * referenced by villager types but with no definition file (the hard-coded keys like {@code gopray})
 * fall back to a placeholder.
 */
public final class GoalDefinitions {

	private static final Map<String, GenericGoalDefinition> DEFS = new ConcurrentHashMap<>();

	private GoalDefinitions() {
	}

	public static Optional<GenericGoalDefinition> get(String key) {
		return key == null ? Optional.empty() : Optional.ofNullable(DEFS.get(key.toLowerCase(Locale.ROOT)));
	}

	public static int size() {
		return DEFS.size();
	}

	/** Load every {@code *.txt} under {@code goalsDir} (recursively) as a goal definition. */
	public static void load(Path goalsDir) {
		DEFS.clear();
		if (!Files.isDirectory(goalsDir)) {
			Millenaire.LOGGER.warn("GoalDefinitions: no goals dir at {}", goalsDir);
			return;
		}
		try (Stream<Path> walk = Files.walk(goalsDir)) {
			walk.filter(p -> p.getFileName().toString().endsWith(".txt")).sorted().forEach(p -> {
				try {
					GenericGoalDefinition def = GenericGoalDefinition.parse(p);
					DEFS.put(def.key(), def);
				} catch (Exception e) {
					Millenaire.LOGGER.warn("GoalDefinitions: failed to parse {} ({})", p.getFileName(), e.getMessage());
				}
			});
		} catch (Exception e) {
			Millenaire.LOGGER.error("GoalDefinitions: failed to scan {}", goalsDir, e);
		}
		long buildingDest = DEFS.values().stream()
				.filter(d -> !d.destinationTag().isEmpty() && !GenericGoalDefinition.TOWNHALL.equals(d.destinationTag())).count();
		long townhall = DEFS.values().stream().filter(d -> GenericGoalDefinition.TOWNHALL.equals(d.destinationTag())).count();
		long required = DEFS.values().stream().filter(d -> !d.requiredTag().isEmpty()).count();
		Millenaire.LOGGER.info("GoalDefinitions: loaded {} goal definition(s) ({} buildingTag dest, {} townhall, {} requiredTag)",
				DEFS.size(), buildingDest, townhall, required);
	}
}
