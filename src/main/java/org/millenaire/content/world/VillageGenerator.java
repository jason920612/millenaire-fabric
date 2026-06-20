package org.millenaire.content.world;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.Millenaire;
import org.millenaire.content.building.BuildingPlan;
import org.millenaire.content.culture.Culture;
import org.millenaire.content.culture.VillageType;
import org.millenaire.entity.MillVillagerEntity;
import org.millenaire.world.BuildingProject;
import org.millenaire.world.MillWorld;
import org.millenaire.world.MillWorldData;
import org.millenaire.world.TownHall;
import org.millenaire.world.VillagerMember;

/**
 * L2 village generation (first slice): given a centre, a culture and a village type, place the
 * starting buildings (centre + starts) into the real world, each aligned to local ground via the
 * surface heightmap. This is the "玩家附近 post-gen" placement (coupling B) — it runs against an
 * already-generated world rather than hooking deterministic chunk gen.
 *
 * <p>Layout is a simple grid for now; faithful road/orientation/spacing layout (BuildingLocation,
 * {@code around}/{@code moveinpriority}) is L3 work.
 */
public final class VillageGenerator {

	public record Result(int buildingsPlaced, int buildingsMissing, int blocksPlaced, int villagersSpawned, BlockPos centreOrigin) {
	}

	private static final int SPACING = 28;

	private VillageGenerator() {
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Deterministic grid placement of building {@code index} of {@code count}, aligned to ground. */
	public static BlockPos buildingOrigin(ServerLevel level, BlockPos centre, int index, int count) {
		int cols = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
		int col = index % cols;
		int row = index / cols;
		int x = centre.getX() + (col - cols / 2) * SPACING;
		int z = centre.getZ() + (row - cols / 2) * SPACING;
		level.getChunk(x >> 4, z >> 4); // ensure the chunk is generated so the heightmap is valid
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
		return new BlockPos(x, y, z);
	}

	public static Result generate(ServerLevel level, BlockPos centre, Culture culture, VillageType type) {
		List<VillageType.BuildingSlot> starts = type.startingBuildings();
		int cols = Math.max(1, (int) Math.ceil(Math.sqrt(starts.size())));
		int placed = 0;
		int missing = 0;
		int blocks = 0;
		BlockPos centreOrigin = null;

		// Duplicate-founding prevention: one village per locality (stable identity by centre proximity).
		MillWorldData world = MillWorldData.get(level);
		Optional<TownHall> dup = world.findNear(centre);
		if (dup.isPresent()) {
			Millenaire.LOGGER.info("Village already exists near {} ('{}') — not founding a duplicate",
					centre, dup.get().name());
			return new Result(0, 0, 0, 0, dup.get().centre());
		}
		TownHall townHall = TownHall.create(centre, culture.name(), type.id(), type.name());

		Millenaire.LOGGER.info("Founding '{}' village type '{}' ({} starting buildings) near {}",
				culture.name(), type.name(), starts.size(), centre);

		for (int i = 0; i < starts.size(); i++) {
			VillageType.BuildingSlot slot = starts.get(i);
			Optional<BuildingPlan> plan = culture.findBuilding(slot.key());
			if (plan.isEmpty()) {
				missing++;
				Millenaire.LOGGER.warn("  [{}] building '{}' not found, skipping", slot.role(), slot.key());
				continue;
			}
			BlockPos origin = buildingOrigin(level, centre, i, starts.size());
			int orientation = plan.get().levels().get(0).config().firstInt("orientation", 0);
			placed++;
			if (slot.role().equals("centre")) {
				centreOrigin = origin;
			}
			// Schedule the building as a construction project; the active village builds it gradually (L3).
			List<String> buildingTags = plan.get().levels().get(0).config().all("tag").stream()
					.map(s -> s.toLowerCase(java.util.Locale.ROOT)).toList();
			townHall.addBuilding(new BuildingProject(plan.get().key(), plan.get().variant(), slot.role(), origin, 0, orientation, buildingTags));
			Millenaire.LOGGER.info("  scheduled {} '{}_{}' at {} (orientation {})",
					slot.role(), plan.get().key(), plan.get().variant(), origin, orientation);
		}

		// Populate the village with a few living villagers near the centre, each of a culture villager type.
		int villagers = 0;
		if (centreOrigin != null) {
			List<String> typeKeys = new java.util.ArrayList<>(culture.villagerTypes().keySet());
			for (int v = 0; v < 3; v++) {
				int sx = centreOrigin.getX() - 3 - v;
				int sz = centreOrigin.getZ() - 3;
				level.getChunk(sx >> 4, sz >> 4);
				int sy = level.getHeight(Heightmap.Types.WORLD_SURFACE, sx, sz);
				BlockPos home = new BlockPos(sx, sy, sz);
				String vtype = typeKeys.isEmpty() ? "" : typeKeys.get(v % typeKeys.size());
				String vname = capitalize(culture.name()) + " " + (vtype.isEmpty() ? "villager" : vtype) + " " + (v + 1);
				// test-only invulnerable: survive headless construction so the scheduler can be exercised
				Optional<MillVillagerEntity> spawned =
						MillVillagerEntity.spawn(level, null, vname, vtype, home, MillWorld.forceActiveForTest);
				if (spawned.isPresent()) {
					villagers++;
					// Home building = the centre for now (until residential assignment exists).
					townHall.addVillager(new VillagerMember(spawned.get().getUUID(), vname, vtype, home, centreOrigin));
				}
			}
			Millenaire.LOGGER.info("  spawned {} villagers near centre {}", villagers, centreOrigin);
		}

		// Test seed: give the village some raw goods so a crafting villager can actually work
		// (e.g. alchemistapprentice -> makeglassbottles needs glass). Real production chains feed this later.
		if (MillWorld.forceActiveForTest) {
			townHall.addGood("glass", 200);
		}

		// Register the Town Hall aggregate into the MillWorld persistent index (single source of truth).
		world.addTownHall(townHall);

		Millenaire.LOGGER.info("Village founded: {} buildings scheduled, {} missing, {} villagers (construction is gradual)",
				placed, missing, villagers);
		return new Result(placed, missing, blocks, villagers, centreOrigin);
	}
}
