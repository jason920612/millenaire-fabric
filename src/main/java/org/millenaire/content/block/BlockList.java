package org.millenaire.content.block;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.millenaire.Millenaire;
import org.millenaire.content.Dsl;

/**
 * Loads {@code blocklist.txt} into:
 * <ul>
 *   <li>{@link #colourToType} — RGB int &rarr; {@link PointType} (consumed by the PNG decoder)</li>
 *   <li>{@link #nameToType} — logical name &rarr; {@link PointType}</li>
 *   <li>{@link LogicalBlockMapping} — logical name &rarr; 26.2 BlockState (coupling A)</li>
 * </ul>
 *
 * <p>blockRefs use 1.12-era {@code name + meta} (e.g. {@code minecraft:planks;1}) which no longer
 * exist in 26.2 (block flattening). A seed {@link #FLATTENING} table translates the most common
 * ones; the rest are reported as unresolved (a full table is follow-up work within L1/L2).
 */
public final class BlockList {

	private final Map<Integer, PointType> colourToType = new HashMap<>();
	private final Map<String, PointType> nameToType = new HashMap<>();
	private int resolved;
	private int unresolved;
	private int approximatedCount;

	public Map<Integer, PointType> colourToType() {
		return colourToType;
	}

	public Map<String, PointType> nameToType() {
		return nameToType;
	}

	public int resolvedCount() {
		return resolved;
	}

	public int unresolvedCount() {
		return unresolved;
	}

	public Optional<PointType> byColour(int colour) {
		return Optional.ofNullable(colourToType.get(colour & 0x00FFFFFF));
	}

	public static BlockList load(Path blocklistFile) throws Exception {
		BlockList bl = new BlockList();
		List<String> lines = Dsl.readLines(blocklistFile);
		for (String line : lines) {
			if (Dsl.isComment(line)) {
				continue;
			}
			String[] c = Dsl.splitSemicolons(line);
			if (c.length < 5) {
				continue;
			}
			String name = c[0].trim();
			String blockRef = c[1].trim();
			int meta = parseIntSafe(c[2].trim(), 0);
			boolean secondStep = c[3].trim().equalsIgnoreCase("true");
			int colour = parseColour(c[4].trim());
			if (name.isEmpty() || colour < 0) {
				continue;
			}
			PointType pt = new PointType(name, blockRef, meta, secondStep, colour);
			bl.colourToType.put(colour, pt);
			bl.nameToType.put(name.toLowerCase(java.util.Locale.ROOT), pt);
			bl.populateMapping(pt);
		}
		Millenaire.LOGGER.info("BlockList: {} entries ({} resolved [{} via approximation], {} special/unresolved)",
				bl.colourToType.size(), bl.resolved, bl.approximatedCount, bl.unresolved);
		return bl;
	}

	private void populateMapping(PointType pt) {
		if (pt.isSpecial()) {
			return; // functional point, no block
		}
		// 1) exact flattening / pass-through of the 1.12 blockRef
		Optional<String> modern = modernId(pt.blockRef(), pt.meta());
		// 2) fallback: nearest-vanilla approximation keyed by logical name (interim; custom blocks come in L6/L7)
		boolean approximated = false;
		if (modern.isEmpty()) {
			String approx = NAME_APPROX.get(pt.name().toLowerCase(java.util.Locale.ROOT));
			if (approx != null) {
				modern = Optional.of(approx);
				approximated = true;
			}
		}
		if (modern.isPresent()) {
			Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(modern.get()));
			if (block.isPresent()) {
				LogicalBlockMapping.put(pt.name(), block.get().defaultBlockState());
				resolved++;
				if (approximated) {
					approximatedCount++;
				}
				return;
			}
		}
		unresolved++;
		Millenaire.LOGGER.debug("BlockList: unresolved blockRef '{}#{}' for '{}'", pt.blockRef(), pt.meta(), pt.name());
	}

	/** Translate a 1.12 (blockRef, meta) to a 26.2 block id, where known. */
	private static Optional<String> modernId(String blockRef, int meta) {
		String flat = FLATTENING.get(blockRef + "#" + meta);
		if (flat != null) {
			return Optional.of(flat);
		}
		// Modern / still-valid ids (air, stone, dirt, cobblestone, …) pass through unchanged.
		if (blockRef.contains(":") && !blockRef.startsWith("millenaire:")) {
			Identifier id = Identifier.parse(blockRef);
			if (BuiltInRegistries.BLOCK.getOptional(id).isPresent()) {
				return Optional.of(blockRef);
			}
		}
		return Optional.empty();
	}

	private static int parseColour(String rgb) {
		String[] p = rgb.split("/");
		if (p.length != 3) {
			return -1;
		}
		try {
			int r = Integer.parseInt(p[0].trim());
			int g = Integer.parseInt(p[1].trim());
			int b = Integer.parseInt(p[2].trim());
			return (r << 16) | (g << 8) | b;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static int parseIntSafe(String s, int fallback) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/**
	 * Seed flattening table: 1.12 {@code blockRef#meta} &rarr; 26.2 block id. Covers the most common
	 * construction blocks so building decode has meaningful coverage; extend toward completeness in L2.
	 */
	private static final Map<String, String> FLATTENING = new HashMap<>();

	static {
		// planks
		FLATTENING.put("minecraft:planks#0", "minecraft:oak_planks");
		FLATTENING.put("minecraft:planks#1", "minecraft:spruce_planks");
		FLATTENING.put("minecraft:planks#2", "minecraft:birch_planks");
		FLATTENING.put("minecraft:planks#3", "minecraft:jungle_planks");
		FLATTENING.put("minecraft:planks#4", "minecraft:acacia_planks");
		FLATTENING.put("minecraft:planks#5", "minecraft:dark_oak_planks");
		// logs (id 17 = log, log2 = acacia/dark oak)
		FLATTENING.put("17#0", "minecraft:oak_log");
		FLATTENING.put("17#1", "minecraft:spruce_log");
		FLATTENING.put("17#2", "minecraft:birch_log");
		FLATTENING.put("17#3", "minecraft:jungle_log");
		FLATTENING.put("minecraft:log#0", "minecraft:oak_log");
		FLATTENING.put("minecraft:log#1", "minecraft:spruce_log");
		FLATTENING.put("minecraft:log#2", "minecraft:birch_log");
		FLATTENING.put("minecraft:log#3", "minecraft:jungle_log");
		FLATTENING.put("minecraft:log2#0", "minecraft:acacia_log");
		FLATTENING.put("minecraft:log2#1", "minecraft:dark_oak_log");
		// stone bricks
		FLATTENING.put("minecraft:stonebrick#0", "minecraft:stone_bricks");
		FLATTENING.put("minecraft:stonebrick#1", "minecraft:mossy_stone_bricks");
		FLATTENING.put("minecraft:stonebrick#2", "minecraft:cracked_stone_bricks");
		FLATTENING.put("minecraft:stonebrick#3", "minecraft:chiseled_stone_bricks");
		// common renamed singletons
		FLATTENING.put("minecraft:grass#0", "minecraft:grass_block");
		FLATTENING.put("minecraft:brick_block#0", "minecraft:bricks");
		FLATTENING.put("minecraft:stained_glass#0", "minecraft:white_stained_glass");
		FLATTENING.put("minecraft:wool#0", "minecraft:white_wool");
		FLATTENING.put("minecraft:snow_layer#0", "minecraft:snow");
		FLATTENING.put("minecraft:web#0", "minecraft:cobweb");
		FLATTENING.put("minecraft:waterlily#0", "minecraft:lily_pad");
	}

	/**
	 * Interim nearest-vanilla approximations keyed by logical name, used when the 1.12 blockRef does
	 * not resolve (largely Millénaire custom blocks: thatch, timber-frame "colombages", paths, mud).
	 * Replaced by real custom blocks in L6/L7; for now these keep buildings visually whole instead of
	 * full of holes. Keys are lowercased logical names.
	 */
	private static final Map<String, String> NAME_APPROX = new HashMap<>();

	static {
		NAME_APPROX.put("doubleslab", "minecraft:smooth_stone");
		NAME_APPROX.put("thatched", "minecraft:hay_block");
		NAME_APPROX.put("thatch", "minecraft:hay_block");
		NAME_APPROX.put("thatchedslab", "minecraft:smooth_stone_slab");
		NAME_APPROX.put("mud brick", "minecraft:mud_bricks");
		NAME_APPROX.put("mud", "minecraft:packed_mud");
		NAME_APPROX.put("oak leaves", "minecraft:oak_leaves");
		NAME_APPROX.put("leaves", "minecraft:oak_leaves");
		NAME_APPROX.put("dirt wall", "minecraft:packed_mud");
		NAME_APPROX.put("pathgravel", "minecraft:gravel");
		NAME_APPROX.put("pathgravelslabs", "minecraft:smooth_stone_slab");
		NAME_APPROX.put("pathdirt", "minecraft:dirt_path");
		NAME_APPROX.put("pathslabs", "minecraft:smooth_stone_slab");
		NAME_APPROX.put("fence", "minecraft:oak_fence");
		NAME_APPROX.put("cooked brick", "minecraft:bricks");
		NAME_APPROX.put("colombages plain", "minecraft:oak_planks");
		NAME_APPROX.put("colombages", "minecraft:oak_planks");
		NAME_APPROX.put("colombages cross", "minecraft:oak_planks");
		NAME_APPROX.put("wattle", "minecraft:oak_planks");
		NAME_APPROX.put("long grass", "minecraft:short_grass");
		NAME_APPROX.put("tallgrass", "minecraft:short_grass");
	}
}
