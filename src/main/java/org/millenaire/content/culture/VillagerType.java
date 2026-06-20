package org.millenaire.content.culture;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.millenaire.content.Dsl;

/**
 * A villager type (one {@code villagers/*.txt}, {@code key=value} DSL): the profession/role a
 * villager can have. For the data-driven goal slice (INTENT.md doc 01) the important part is the
 * ordered {@code goal=} list — the candidate goals this type may run. Other fields (tags, required
 * goods, tools, weapons…) are parsed later.
 */
public final class VillagerType {

	private final String key;
	private final String nativeName;
	private final String gender;
	private final List<String> goals;

	private VillagerType(String key, String nativeName, String gender, List<String> goals) {
		this.key = key;
		this.nativeName = nativeName;
		this.gender = gender;
		this.goals = goals;
	}

	public String key() {
		return key;
	}

	public String nativeName() {
		return nativeName;
	}

	public String gender() {
		return gender;
	}

	/** Ordered list of goal keys this type may run (from the {@code goal=} lines). */
	public List<String> goals() {
		return goals;
	}

	public static VillagerType parse(Path file) throws java.io.IOException {
		String key = file.getFileName().toString().replaceFirst("\\.txt$", "");
		Dsl.Record r = Dsl.parseEqualsFile(Dsl.readLines(file));
		return new VillagerType(key,
				r.first("native_name").orElse(key),
				r.first("gender").orElse("male"),
				r.all("goal").stream().map(g -> g.toLowerCase(Locale.ROOT)).toList());
	}
}
