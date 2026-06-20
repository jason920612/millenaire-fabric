package org.millenaire.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * A building's named work positions (intent doc 01 §5.3 / doc 03): the special points decoded from a
 * building's layered-PNG schematic — {@code craftingPos}, {@code sellingPos}, {@code sleepingPos},
 * {@code defendingPos}, {@code leasurePos}, {@code mainChest}, {@code *soil}, {@code *spawn}, etc.
 *
 * <p>Key design intent: <b>goals ask the building "where do I stand"</b> via these named positions
 * rather than hard-coding coordinates, so one goal works across any building variant / village layout.
 * TODO: populate from {@code BuildingPlan} special points at construction time; expose per-name lookup.
 */
public final class ResManager {

	// Common position names (from blocklist special tags).
	public static final String CRAFTING = "craftingpos";
	public static final String SELLING = "sellingpos";
	public static final String SLEEPING = "sleepingpos";
	public static final String DEFENDING = "defendingpos";
	public static final String LEISURE = "leasurepos";
	public static final String MAIN_CHEST = "mainchest";

	private final Map<String, List<BlockPos>> positions = new HashMap<>();

	public void add(String name, BlockPos pos) {
		positions.computeIfAbsent(name.toLowerCase(java.util.Locale.ROOT), k -> new ArrayList<>()).add(pos);
	}

	public List<BlockPos> get(String name) {
		return positions.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), List.of());
	}

	public java.util.Optional<BlockPos> first(String name) {
		List<BlockPos> list = get(name);
		return list.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(list.get(0));
	}
}
