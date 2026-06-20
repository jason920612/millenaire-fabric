package org.millenaire.content;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.millenaire.content.block.BlockList;
import org.millenaire.content.building.BuildingPlan;
import org.millenaire.content.culture.Culture;

/**
 * Runtime holder for the loaded content (populated by {@link ContentLoader} at the end of the
 * real load flow). Gives later systems (world gen, building, NPCs) a single access point.
 */
public final class MillContent {

	private static final Map<String, Culture> CULTURES = new LinkedHashMap<>();
	private static volatile BlockList blockList;

	private MillContent() {
	}

	public static void set(List<Culture> cultures, BlockList bl) {
		CULTURES.clear();
		for (Culture c : cultures) {
			CULTURES.put(c.name().toLowerCase(Locale.ROOT), c);
		}
		blockList = bl;
	}

	public static Optional<Culture> culture(String name) {
		return Optional.ofNullable(CULTURES.get(name.toLowerCase(Locale.ROOT)));
	}

	public static Map<String, Culture> cultures() {
		return CULTURES;
	}

	public static BlockList blockList() {
		return blockList;
	}

	/** Find a building plan by culture + "{key}_{variant}" id (e.g. {@code armoury_A}). */
	public static Optional<BuildingPlan> building(String cultureName, String buildingId) {
		return culture(cultureName).map(c -> c.buildings().get(buildingId));
	}
}
