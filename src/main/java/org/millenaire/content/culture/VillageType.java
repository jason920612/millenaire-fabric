package org.millenaire.content.culture;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.millenaire.content.Dsl;

/**
 * A village type (one {@code villages/*.txt}, colon DSL): declares the buildings that make up a
 * village and how it grows. INTENT.md doc 04: {@code centre} (required) &rarr; all {@code start}
 * &rarr; {@code core} in order &rarr; {@code secondary}; {@code never} excludes.
 *
 * <p>L2 uses {@code centre} + {@code start} (the "starting buildings") to found a village; later
 * growth ({@code core}/{@code secondary}) is wired in L3+.
 */
public final class VillageType {

	public record BuildingSlot(String role, String key) {
	}

	private final String id;
	private final String name;
	private final int weight;
	private final int radius;
	private final List<String> biomes = new ArrayList<>();
	private final List<BuildingSlot> slots = new ArrayList<>();

	private VillageType(String id, String name, int weight, int radius) {
		this.id = id;
		this.name = name;
		this.weight = weight;
		this.radius = radius;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public int weight() {
		return weight;
	}

	public int radius() {
		return radius;
	}

	public List<String> biomes() {
		return biomes;
	}

	public List<BuildingSlot> slots() {
		return slots;
	}

	/** Buildings to place when founding the village (centre + starts), in placement order. */
	public List<BuildingSlot> startingBuildings() {
		List<BuildingSlot> out = new ArrayList<>();
		for (BuildingSlot s : slots) {
			if (s.role().equals("centre") || s.role().equals("start")) {
				out.add(s);
			}
		}
		return out;
	}

	public static VillageType parse(Path file) throws java.io.IOException {
		String id = file.getFileName().toString().replaceFirst("\\.txt$", "");
		// villages use the colon DSL but one assignment per line; reuse the per-line colon parser
		// by merging all lines into a single record.
		Dsl.Record r = new Dsl.Record();
		for (String line : Dsl.readLines(file)) {
			if (Dsl.isComment(line)) {
				continue;
			}
			int colon = line.indexOf(':');
			if (colon < 0) {
				continue;
			}
			r.add(line.substring(0, colon), line.substring(colon + 1).trim());
		}
		VillageType vt = new VillageType(id,
				r.first("name").orElse(id),
				r.firstInt("weight", 1),
				r.firstInt("radius", 40));
		vt.biomes.addAll(r.all("biome"));
		for (String role : List.of("centre", "start", "core", "secondary")) {
			for (String key : r.all(role)) {
				vt.slots.add(new BuildingSlot(role, key));
			}
		}
		return vt;
	}
}
