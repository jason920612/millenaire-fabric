package org.millenaire.entity.ai;

import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.Millenaire;
import org.millenaire.building.BuildingResManagers;
import org.millenaire.building.ResManager;
import org.millenaire.content.MillContent;
import org.millenaire.content.building.BuildingPlan;
import org.millenaire.content.economy.GoodStack;
import org.millenaire.entity.MillVillagerEntity;
import org.millenaire.world.BuildingProject;
import org.millenaire.world.GoodsStore;
import org.millenaire.world.TownHall;

/**
 * A {@link VillagerGoal} backed by a {@link GenericGoalDefinition} — the data-driven goals a villager
 * gets from its type's {@code goal=} list (intent doc 01 §5.1). Resolves the destination (townhall /
 * tagged building / the villager's home building), travels to the building's craftingPos (via
 * {@link ResManager}), and — for crafting goals — performs the recipe on arrival: deduct inputs, add
 * outputs to the village stock, respecting the building limit.
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
	public boolean isLeisure() {
		return VillagerGoals.isLeisureKey(def.key());
	}

	private boolean isBuildingDestination() {
		return !def.destinationTag().isEmpty() && !GenericGoalDefinition.TOWNHALL.equals(def.destinationTag());
	}

	/** The building this goal works at: the tagged building, or the villager's home building. */
	private Optional<BuildingProject> destinationBuilding(MillVillagerEntity v, TownHall townHall) {
		if (isBuildingDestination()) {
			return townHall.findBuildingByTag(def.destinationTag(), def.requiredTag());
		}
		return townHall.memberFor(v.getUUID()).flatMap(townHall::homeBuildingFor);
	}

	/** The goods stock this goal draws from / fills: the village (townhall goals) or the work building. */
	private GoodsStore storeFor(MillVillagerEntity v, TownHall townHall) {
		if (GenericGoalDefinition.TOWNHALL.equals(def.destinationTag())) {
			return townHall;
		}
		return destinationBuilding(v, townHall).map(b -> (GoodsStore) b).orElse(townHall);
	}

	@Override
	public boolean isPossible(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		// Building / unlock gating.
		if (isBuildingDestination()) {
			if (townHall.findBuildingByTag(def.destinationTag(), def.requiredTag()).isEmpty()) {
				return false;
			}
		} else if (!def.requiredTag().isEmpty()) {
			if (townHall.findBuildingByTag(def.requiredTag(), "").isEmpty()) {
				return false;
			}
		}
		// Crafting gating: must have all inputs and be under the building limit (docs' "produce until full").
		if (def.isCrafting()) {
			GoodsStore store = storeFor(v, townHall);
			if (!def.limitGood().isEmpty() && store.countGood(def.limitGood()) >= def.limitMax()) {
				return false;
			}
			for (GoodStack in : def.inputs()) {
				if (store.countGood(in.good()) < in.qty()) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public int priority(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		return def.priority() + v.getRandom().nextInt(5);
	}

	@Override
	public void start(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		BlockPos dest;
		if (GenericGoalDefinition.TOWNHALL.equals(def.destinationTag())) {
			dest = townHall.centre();
		} else {
			dest = destinationBuilding(v, townHall).map(b -> workPos(townHall, b)).orElseGet(() -> strollNear(v, level));
		}
		v.setGoalTarget(dest);
		v.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 0.5);
	}

	/** Prefer the building's craftingPos (from its ResManager) over its bare origin. */
	private BlockPos workPos(TownHall townHall, BuildingProject building) {
		Optional<BuildingPlan> plan = MillContent.building(townHall.culture(), building.key() + "_" + building.variant());
		if (plan.isPresent()) {
			ResManager rm = BuildingResManagers.forBuilding(plan.get(), building);
			Optional<BlockPos> cp = rm.first(ResManager.CRAFTING);
			if (cp.isPresent()) {
				return cp.get();
			}
		}
		return building.origin();
	}

	private BlockPos strollNear(MillVillagerEntity v, ServerLevel level) {
		int x = v.blockPosition().getX() + v.getRandom().nextInt(9) - 4;
		int z = v.blockPosition().getZ() + v.getRandom().nextInt(9) - 4;
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
		return new BlockPos(x, y, z);
	}

	@Override
	public void tick(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		BlockPos t = v.getGoalTarget();
		if (t != null) {
			v.getLookControl().setLookAt(t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5);
		}
		// Perform a craft once we're at the work building (generous radius — the craftingPos tile may be
		// inside the structure) and a duration has elapsed since the last action.
		if (def.isCrafting() && t != null && v.blockPosition().distSqr(t) <= 100
				&& level.getGameTime() - v.getLastActionTime() >= def.duration()) {
			if (craft(v, level, townHall)) {
				v.setLastActionTime(level.getGameTime());
			}
		}
	}

	private boolean craft(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		GoodsStore store = storeFor(v, townHall);
		if (!def.limitGood().isEmpty() && store.countGood(def.limitGood()) >= def.limitMax()) {
			return false;
		}
		for (GoodStack in : def.inputs()) {
			if (store.countGood(in.good()) < in.qty()) {
				return false;
			}
		}
		for (GoodStack in : def.inputs()) {
			store.removeGood(in.good(), in.qty());
		}
		for (GoodStack out : def.outputs()) {
			store.addGood(out.good(), out.qty());
		}
		townHall.markRuntimeDirty(); // persist the crafted goods (MillWorld marks the SavedData dirty)
		if (Millenaire.LOG_VILLAGER_GOALS) {
			String ins = def.inputs().stream().map(g -> g.good() + "×" + g.qty()).collect(Collectors.joining("+"));
			String outs = def.outputs().stream().map(g -> g.good() + "×" + g.qty()).collect(Collectors.joining("+"));
			String first = def.outputs().isEmpty() ? "" : def.outputs().get(0).good();
			Millenaire.LOGGER.info("Crafted '{}': {} -> {} by {} (building stock {}={})",
					def.key(), ins, outs, v.getName().getString(), first, store.countGood(first));
		}
		return true;
	}

	@Override
	public boolean isFinished(MillVillagerEntity v, ServerLevel level, TownHall townHall) {
		if (def.isCrafting()) {
			return false; // keep working until isPossible fails (out of inputs / limit reached)
		}
		return level.getGameTime() - v.getGoalStartTime() > def.duration();
	}
}
