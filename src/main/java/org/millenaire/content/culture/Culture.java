package org.millenaire.content.culture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.millenaire.Millenaire;
import org.millenaire.content.Dsl;
import org.millenaire.content.block.BlockList;
import org.millenaire.content.building.BuildingPlan;

/**
 * A culture = a directory (its name is the culture key). Identity emerges from the sum of its
 * sub-files; {@code culture.txt} itself is tiny (INTENT.md doc 04). This L1 loader parses the real
 * content — every building plan is decoded through {@link BuildingPlan} — so loading a culture is a
 * genuine end-to-end exercise of the schematic + DSL pipeline, not a stub.
 */
public final class Culture {

	private final String name;
	private final Dsl.Record cultureConfig;
	private final Map<String, BuildingPlan> buildings = new LinkedHashMap<>();
	private final Map<String, VillagerType> villagerTypes = new LinkedHashMap<>();
	private final Map<String, VillageType> villageTypes = new LinkedHashMap<>();
	private int buildingFailures;

	private Culture(String name, Dsl.Record cultureConfig) {
		this.name = name;
		this.cultureConfig = cultureConfig;
	}

	public String name() {
		return name;
	}

	public Dsl.Record config() {
		return cultureConfig;
	}

	public Map<String, BuildingPlan> buildings() {
		return buildings;
	}

	public int buildingCount() {
		return buildings.size();
	}

	public int buildingFailures() {
		return buildingFailures;
	}

	public int villagerTypeCount() {
		return villagerTypes.size();
	}

	public Map<String, VillagerType> villagerTypes() {
		return villagerTypes;
	}

	public Optional<VillagerType> villagerType(String key) {
		return Optional.ofNullable(villagerTypes.get(key));
	}

	public int villageTypeCount() {
		return villageTypes.size();
	}

	public Map<String, VillageType> villageTypes() {
		return villageTypes;
	}

	public Optional<VillageType> villageType(String id) {
		return Optional.ofNullable(villageTypes.get(id));
	}

	/** Find any building plan whose authored key matches (ignoring variant), e.g. {@code "largefort"}. */
	public Optional<BuildingPlan> findBuilding(String buildingKey) {
		for (BuildingPlan p : buildings.values()) {
			if (p.key().equalsIgnoreCase(buildingKey)) {
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}

	public static Culture load(Path cultureDir, BlockList blockList) throws IOException {
		String name = cultureDir.getFileName().toString();
		Dsl.Record cfg = Dsl.parseEqualsFile(Dsl.readLines(cultureDir.resolve("culture.txt")));
		Culture culture = new Culture(name, cfg);

		// Buildings: core / extra / lone — parse + decode every plan.
		for (String sub : List.of("buildings/core", "buildings/extra", "buildings/lone")) {
			Path bdir = cultureDir.resolve(sub);
			if (!Files.isDirectory(bdir)) {
				continue;
			}
			try (Stream<Path> s = Files.list(bdir)) {
				List<Path> txts = s.filter(p -> p.getFileName().toString().endsWith(".txt")).sorted().toList();
				for (Path txt : txts) {
					try {
						BuildingPlan plan = BuildingPlan.parse(txt, blockList);
						culture.buildings.put(plan.key() + "_" + plan.variant(), plan);
					} catch (Exception e) {
						culture.buildingFailures++;
						Millenaire.LOGGER.warn("[{}] building parse failed: {} ({})", name, txt.getFileName(), e.getMessage());
					}
				}
			}
		}

		culture.loadVillagerTypes(cultureDir.resolve("villagers"));
		culture.loadVillageTypes(cultureDir.resolve("villages"));

		Millenaire.LOGGER.info("Culture '{}': {} buildings ({} failed), {} villager types, {} village types",
				name, culture.buildingCount(), culture.buildingFailures, culture.villagerTypeCount(), culture.villageTypeCount());
		return culture;
	}

	private void loadVillageTypes(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> s = Files.list(dir)) {
			List<Path> txts = s.filter(p -> p.getFileName().toString().endsWith(".txt")).sorted().toList();
			for (Path txt : txts) {
				try {
					VillageType vt = VillageType.parse(txt);
					villageTypes.put(vt.id(), vt);
				} catch (Exception e) {
					Millenaire.LOGGER.warn("[{}] village type parse failed: {} ({})", name, txt.getFileName(), e.getMessage());
				}
			}
		}
	}

	private void loadVillagerTypes(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> s = Files.list(dir)) {
			List<Path> txts = s.filter(p -> p.getFileName().toString().endsWith(".txt")).sorted().toList();
			for (Path txt : txts) {
				try {
					VillagerType vt = VillagerType.parse(txt);
					villagerTypes.put(vt.key(), vt);
				} catch (Exception e) {
					Millenaire.LOGGER.warn("[{}] villager type parse failed: {} ({})", name, txt.getFileName(), e.getMessage());
				}
			}
		}
	}
}
