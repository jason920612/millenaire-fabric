package org.millenaire.content;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Parsing utilities for Millénaire's four micro-DSL dialects (INTENT.md doc 04):
 * <ul>
 *   <li>{@code key=value} line files (villagers, shops, config, languages)</li>
 *   <li>{@code key:value;key:value} colon/semicolon segments (building plans, villages)</li>
 *   <li>{@code a;b;c} semicolon lists (goods.txt, reputation)</li>
 *   <li>comma CSV (traded_goods)</li>
 * </ul>
 * Cross-format conventions preserved here: keys are case-insensitive; a repeated key
 * <b>appends</b> to a list; comment lines start with {@code //}; bilingual {@code "fr / en"}
 * values are kept verbatim (splitting is a display concern handled later).
 */
public final class Dsl {

	private Dsl() {
	}

	/** Ordered, case-insensitive multimap: one key may hold several values (repeated-key = append). */
	public static final class Record {
		private final Map<String, List<String>> map = new LinkedHashMap<>();

		public void add(String key, String value) {
			map.computeIfAbsent(norm(key), k -> new ArrayList<>()).add(value);
		}

		public boolean has(String key) {
			return map.containsKey(norm(key));
		}

		public Optional<String> first(String key) {
			List<String> v = map.get(norm(key));
			return (v == null || v.isEmpty()) ? Optional.empty() : Optional.of(v.get(0));
		}

		public List<String> all(String key) {
			return map.getOrDefault(norm(key), List.of());
		}

		public int firstInt(String key, int fallback) {
			return first(key).map(s -> {
				try {
					return Integer.parseInt(s.trim());
				} catch (NumberFormatException e) {
					return fallback;
				}
			}).orElse(fallback);
		}

		public boolean firstBool(String key, boolean fallback) {
			return first(key).map(s -> s.trim().equalsIgnoreCase("true")).orElse(fallback);
		}

		public java.util.Set<String> keys() {
			return map.keySet();
		}

		public int size() {
			return map.size();
		}
	}

	/**
	 * Read a content file tolerantly: strict UTF-8 first, falling back to ISO-8859-1 (which never
	 * fails) for the legacy-encoded files in the original content set (some carry non-UTF-8 accented
	 * bytes, e.g. {@code Tour détruite}). Proper localisation cleanup is L7 work.
	 */
	public static List<String> readLines(Path file) throws IOException {
		try {
			return Files.readAllLines(file, StandardCharsets.UTF_8);
		} catch (MalformedInputException e) {
			return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
		}
	}

	public static boolean isComment(String line) {
		String t = line.trim();
		return t.isEmpty() || t.startsWith("//");
	}

	private static String norm(String key) {
		return key.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Parse a {@code key=value} file (one assignment per non-comment line) into a single Record.
	 * The value keeps its original case and any embedded spaces / {@code "fr / en"} content.
	 */
	public static Record parseEqualsFile(List<String> lines) {
		Record r = new Record();
		for (String line : lines) {
			if (isComment(line)) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq < 0) {
				continue;
			}
			r.add(line.substring(0, eq), line.substring(eq + 1).trim());
		}
		return r;
	}

	/**
	 * Parse one {@code key:value;key:value;...} line into a Record. Each segment splits on the
	 * <i>first</i> {@code :} only; the original code requires exactly two parts, so a value must
	 * not contain {@code :}. Segments without a {@code :} are stored under their own name with an
	 * empty value (used as flags/tags).
	 */
	public static Record parseColonSegments(String line) {
		Record r = new Record();
		for (String seg : line.split(";")) {
			if (seg.isBlank()) {
				continue;
			}
			int colon = seg.indexOf(':');
			if (colon < 0) {
				r.add(seg.trim(), "");
			} else {
				r.add(seg.substring(0, colon), seg.substring(colon + 1).trim());
			}
		}
		return r;
	}

	/** Split a semicolon list ({@code a;b;c}) keeping empty trailing fields (e.g. blocklist's 5 cols). */
	public static String[] splitSemicolons(String line) {
		return line.split(";", -1);
	}
}
