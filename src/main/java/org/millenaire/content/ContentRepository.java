package org.millenaire.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.millenaire.Millenaire;

/**
 * Locates the Millénaire content tree and discovers cultures.
 *
 * <p>Resolution order: {@code -Dmillenaire.content.dir=...} &rarr; {@code ./content} (run cwd)
 * &rarr; {@code ../_reference/kinniken-src/millenaire} (dev: reference checkout one level up from
 * the {@code run/} working dir). The reused content keeps its original on-disk layout (hybrid DSL
 * strategy, PLAN.md §6).
 */
public final class ContentRepository {

	private final Path root;

	private ContentRepository(Path root) {
		this.root = root;
	}

	public Path root() {
		return root;
	}

	public Path blocklist() {
		return root.resolve("blocklist.txt");
	}

	public Path goods() {
		return root.resolve("goods.txt");
	}

	/** Culture dirs = subdirectories of {@code cultures/} that contain a {@code culture.txt}. */
	public List<Path> cultures() throws IOException {
		Path cdir = root.resolve("cultures");
		List<Path> out = new ArrayList<>();
		if (!Files.isDirectory(cdir)) {
			return out;
		}
		try (Stream<Path> s = Files.list(cdir)) {
			s.filter(Files::isDirectory)
					.filter(p -> Files.exists(p.resolve("culture.txt")))
					.sorted()
					.forEach(out::add);
		}
		return out;
	}

	public static ContentRepository discover() {
		List<Path> candidates = new ArrayList<>();
		String override = System.getProperty("millenaire.content.dir");
		if (override != null && !override.isBlank()) {
			candidates.add(Path.of(override));
		}
		candidates.add(Path.of("content"));
		candidates.add(Path.of("..", "_reference", "kinniken-src", "millenaire"));

		for (Path c : candidates) {
			Path abs = c.toAbsolutePath().normalize();
			if (Files.exists(abs.resolve("blocklist.txt"))) {
				Millenaire.LOGGER.info("Content root: {}", abs);
				return new ContentRepository(abs);
			}
		}
		Millenaire.LOGGER.error("No content root found (looked for blocklist.txt in {}). Content will not load.",
				candidates);
		return null;
	}
}
