package org.millenaire.content.block;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.Millenaire;

/**
 * Foundation for <b>coupling A</b> (PLAN.md §7 / INTENT.md §5) — the single highest-impact
 * migration concern.
 *
 * <p>Millénaire content (blocklist.txt, building layer-PNGs, goods.txt) references blocks by
 * <i>logical names</i> that, on 1.12.2, resolved to {@code (Block + int meta)}. Minecraft 26.2
 * has no block metadata. This layer maps a logical block name &rarr; a 26.2 {@link BlockState},
 * so the original 2000+ content files can be reused unchanged: the "color/good name &rarr;
 * logical name" layer stays put, and only this "logical name &rarr; BlockState" layer is new.
 *
 * <p>L0 seeds a few entries; L1 replaces {@link #bootstrap()} with a full blocklist.txt loader.
 */
public final class LogicalBlockMapping {

	private static final Map<String, BlockState> MAP = new ConcurrentHashMap<>();

	private LogicalBlockMapping() {
	}

	/** Seed a handful of mappings to prove the layer; L1 loads the real blocklist.txt. */
	public static void bootstrap() {
		registerVanilla("air", "minecraft:air");
		registerVanilla("cobblestone", "minecraft:cobblestone");
		registerVanilla("wood", "minecraft:oak_planks");
		registerVanilla("wood_pine", "minecraft:spruce_planks");
		registerVanilla("dirt", "minecraft:dirt");
		Millenaire.LOGGER.info("LogicalBlockMapping seeded with {} entries (L1 will load blocklist.txt)", MAP.size());
	}

	/** Map a logical name to the default state of a registered block id (e.g. {@code minecraft:cobblestone}). */
	public static void registerVanilla(String logicalName, String blockId) {
		Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(blockId));
		if (block.isEmpty()) {
			Millenaire.LOGGER.warn("LogicalBlockMapping: unknown block id '{}' for logical name '{}'", blockId, logicalName);
			return;
		}
		MAP.put(key(logicalName), block.get().defaultBlockState());
	}

	/** Map a logical name directly to a fully-specified BlockState (for blocks that need non-default properties). */
	public static void put(String logicalName, BlockState state) {
		MAP.put(key(logicalName), state);
	}

	/** Resolve a logical name to a BlockState, falling back to AIR if unknown. */
	public static BlockState resolve(String logicalName) {
		BlockState state = MAP.get(key(logicalName));
		return state != null ? state : Blocks.AIR.defaultBlockState();
	}

	public static Optional<BlockState> resolveOptional(String logicalName) {
		return Optional.ofNullable(MAP.get(key(logicalName)));
	}

	public static int size() {
		return MAP.size();
	}

	private static String key(String logicalName) {
		return logicalName.toLowerCase(java.util.Locale.ROOT);
	}
}
