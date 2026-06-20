package org.millenaire.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.entity.MillVillagerEntity;
import org.millenaire.world.BuildingProject;
import org.millenaire.world.TownHall;

/**
 * A {@link VillagerGoal} backed by a {@link GenericGoalDefinition} — the data-driven goals a villager
 * gets from its type's {@code goal=} list. Stateless singleton per definition (cached in
 * {@link VillagerGoals}). Behaviour for this slice is a short local stroll for {@code duration} ticks;
 * real per-goal behaviour (craft / go to a tagged building / etc.) lands with issues #3/#4.
 */
public final class GenericGoal implements VillagerGoal {

	private final GenericGoalDefinition def;

	public GenericGoal(GenericGoalDefinition def) {
		this.def = def;
	}

	@Override
	public String key() {
		return def.key();
	}

	@Override
	public boolean isPossible(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		// A building-destination goal is only possible if the village actually has that building
		// (with the required upgrade tag) — models the docs' building/unlock gating.
		if (isBuildingDestination()) {
			return townHall.findBuildingByTag(def.destinationTag(), def.requiredTag()).isPresent();
		}
		return true;
	}

	private boolean isBuildingDestination() {
		return !def.destinationTag().isEmpty() && !GenericGoalDefinition.TOWNHALL.equals(def.destinationTag());
	}

	@Override
	public int priority(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		return def.priority() + v.getRandom().nextInt(5);
	}

	@Override
	public void start(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		BlockPos dest;
		if (GenericGoalDefinition.TOWNHALL.equals(def.destinationTag())) {
			dest = townHall.centre(); // townhallgoal=true -> the centre
		} else if (isBuildingDestination()) {
			// Walk to the building carrying the goal's buildingTag (and requiredTag). Crafting/harvest
			// behaviour at the destination is issue #3; here we resolve and travel to the real building.
			dest = townHall.findBuildingByTag(def.destinationTag(), def.requiredTag())
					.map(BuildingProject::origin).orElse(townHall.centre());
		} else {
			// No destination (the villager's house) — local stroll until houses are modelled (#3).
			int x = v.blockPosition().getX() + v.getRandom().nextInt(9) - 4;
			int z = v.blockPosition().getZ() + v.getRandom().nextInt(9) - 4;
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			dest = new BlockPos(x, y, z);
		}
		v.setGoalTarget(dest);
		v.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 0.45);
	}

	@Override
	public boolean isFinished(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		return level.getGameTime() - v.getGoalStartTime() > def.duration();
	}
}
