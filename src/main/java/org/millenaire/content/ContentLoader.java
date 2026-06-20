package org.millenaire.content;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.Millenaire;
import org.millenaire.content.block.BlockList;
import org.millenaire.content.block.LogicalBlockMapping;
import org.millenaire.content.block.PointType;
import org.millenaire.content.building.BuildingPlan;
import org.millenaire.content.culture.Culture;
import org.millenaire.content.economy.Goods;

/**
 * L1 orchestrator: loads the whole content tree through the <b>real</b> production pipeline
 * (blocklist &rarr; goods &rarr; cultures &rarr; building decode), then runs a self-test that
 * reconstructs one building into a block-coordinate table. Invoked from {@link Millenaire#onInitialize()}
 * so verification happens via the actual mod-load flow, not a side harness.
 */
public final class ContentLoader {

	private ContentLoader() {
	}

	public static void load() {
		ContentRepository repo = ContentRepository.discover();
		if (repo == null) {
			return;
		}
		try {
			BlockList blockList = BlockList.load(repo.blocklist());
			Goods goods = Goods.load(repo.goods());
			org.millenaire.entity.ai.GoalDefinitions.load(repo.root().resolve("goals"));

			List<Culture> cultures = new ArrayList<>();
			int totalBuildings = 0;
			int totalFailures = 0;
			for (Path cdir : repo.cultures()) {
				Culture culture = Culture.load(cdir, blockList);
				cultures.add(culture);
				totalBuildings += culture.buildingCount();
				totalFailures += culture.buildingFailures();
			}

			MillContent.set(cultures, blockList);

			Millenaire.LOGGER.info("L1 content load complete: {} cultures, {} buildings decoded ({} failed), "
							+ "{} goods, {} logical blocks mapped",
					cultures.size(), totalBuildings, totalFailures, goods.size(), LogicalBlockMapping.size());

			coverageReport(cultures);
			selfTest(cultures, blockList);
		} catch (Exception e) {
			Millenaire.LOGGER.error("L1 content load failed", e);
		}
	}

	/**
	 * Quantify the silent-air risk (Codex audit P2): scan every building's decoded cells and report
	 * how many distinct logical blocks (and what fraction of placed cells) currently map to a real
	 * 26.2 BlockState vs would silently become air. Makes the flattening-table gap a measured number.
	 */
	private static void coverageReport(List<Culture> cultures) {
		Set<String> mapped = new HashSet<>();
		Map<String, Integer> unmapped = new HashMap<>();
		long totalCells = 0;
		long unmappedCells = 0;
		for (Culture c : cultures) {
			for (BuildingPlan p : c.buildings().values()) {
				for (BuildingPlan.Level lvl : p.levels()) {
					for (PointType[][] floor : lvl.grid()) {
						for (PointType[] row : floor) {
							for (PointType pt : row) {
								if (pt == null || pt.isEmpty() || pt.isSpecial()) {
									continue;
								}
								totalCells++;
								String name = pt.name().toLowerCase(Locale.ROOT);
								if (LogicalBlockMapping.resolveOptional(name).isPresent()) {
									mapped.add(name);
								} else {
									unmapped.merge(name, 1, Integer::sum);
									unmappedCells++;
								}
							}
						}
					}
				}
			}
		}
		String top = unmapped.entrySet().stream()
				.sorted((a, b) -> b.getValue() - a.getValue())
				.limit(12)
				.map(e -> e.getKey() + "(" + e.getValue() + ")")
				.collect(Collectors.joining(", "));
		double pct = totalCells == 0 ? 0.0 : (unmappedCells * 100.0 / totalCells);
		Millenaire.LOGGER.warn("Block coverage: {} distinct building blocks mapped, {} UNMAPPED (→air silently). "
						+ "{}/{} placed cells ({}%) would be missing. Top unmapped: [{}]",
				mapped.size(), unmapped.size(), unmappedCells, totalCells, String.format("%.2f", pct), top);
	}

	/**
	 * Self-test (production-flow): reconstruct a real building into a coordinate table and report
	 * how many of its blocks resolve to a 26.2 BlockState. Proves the schematic decode + coupling-A
	 * mapping end-to-end.
	 */
	private static void selfTest(List<Culture> cultures, BlockList blockList) {
		Culture norman = cultures.stream().filter(c -> c.name().equals("norman")).findFirst()
				.orElse(cultures.isEmpty() ? null : cultures.get(0));
		if (norman == null || norman.buildings().isEmpty()) {
			Millenaire.LOGGER.warn("Self-test skipped: no buildings to inspect");
			return;
		}
		BuildingPlan plan = norman.buildings().getOrDefault("armoury_A",
				norman.buildings().values().iterator().next());
		BuildingPlan.Level lvl0 = plan.levels().get(0);

		int resolvedBlocks = 0;
		int placedBlocks = 0;
		int sampled = 0;
		StringBuilder samples = new StringBuilder();
		PointType[][][] grid = lvl0.grid();
		for (int y = 0; y < lvl0.nbfloors(); y++) {
			for (int z = 0; z < plan.length(); z++) {
				for (int x = 0; x < plan.width(); x++) {
					PointType pt = grid[y][z][x];
					if (pt == null || pt.isEmpty() || pt.isSpecial()) {
						continue;
					}
					placedBlocks++;
					BlockState state = LogicalBlockMapping.resolve(pt.name());
					boolean ok = state != null && !state.isAir();
					if (ok) {
						resolvedBlocks++;
					}
					if (sampled < 6) {
						samples.append(String.format("%n    (x=%d,y=%d,z=%d) '%s' -> %s",
								x, y + plan.startLevel(), z, pt.name(), ok ? state.getBlock() : "<unresolved>"));
						sampled++;
					}
				}
			}
		}

		Millenaire.LOGGER.info("L1 self-test — building '{}_{}' level 0: size {}x{}x{} (W x Z x floors), "
						+ "{} placed blocks, {} resolved to 26.2 BlockState ({}%). Sample:{}",
				plan.key(), plan.variant(), plan.width(), plan.length(), lvl0.nbfloors(),
				placedBlocks, resolvedBlocks,
				placedBlocks == 0 ? 0 : (resolvedBlocks * 100 / placedBlocks), samples);
	}
}
