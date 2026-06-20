package org.millenaire.entity.ai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.entity.MillVillagerEntity;
import org.millenaire.world.BuildingProject;
import org.millenaire.world.TownHall;

/**
 * Registry of the (stateless, singleton) villager goals plus the minimal starter set used to prove
 * the scheduler actually switches goals based on village state:
 * <ul>
 *   <li>{@code observe_construction} (highest) — only possible while the village has an unfinished
 *       building; sends the villager to watch it being built;</li>
 *   <li>{@code go_to_townhall} — head back to the centre when away;</li>
 *   <li>{@code wander} — stroll to a nearby spot;</li>
 *   <li>{@code idle} (lowest) — stand for a moment.</li>
 * </ul>
 */
public final class VillagerGoals {

	public static final String IDLE = "idle";
	public static final String WANDER = "wander";
	public static final String GO_TO_TOWNHALL = "go_to_townhall";
	public static final String OBSERVE_CONSTRUCTION = "observe_construction";

	private static final Map<String, VillagerGoal> REGISTRY = new LinkedHashMap<>();
	/** Data-driven goals referenced by villager types, materialised on demand (one singleton per key). */
	private static final Map<String, VillagerGoal> GENERIC_CACHE = new ConcurrentHashMap<>();

	static {
		register(new Idle());
		register(new Wander());
		register(new GoToTownHall());
		register(new ObserveConstruction());
	}

	private VillagerGoals() {
	}

	private static void register(VillagerGoal g) {
		REGISTRY.put(g.key(), g);
	}

	public static VillagerGoal byKey(String key) {
		if (key == null || key.isEmpty()) {
			return null;
		}
		VillagerGoal g = REGISTRY.get(key);
		return g != null ? g : generic(key);
	}

	/** The (cached) data-driven goal for a content goal key (case-insensitive; backed by its real definition). */
	public static VillagerGoal generic(String goalKey) {
		String key = goalKey.toLowerCase(Locale.ROOT);
		return GENERIC_CACHE.computeIfAbsent(key, k -> {
			boolean defined = GoalDefinitions.get(k).isPresent();
			GenericGoalDefinition def = GoalDefinitions.get(k).orElseGet(() -> GenericGoalDefinition.placeholder(k));
			if (org.millenaire.Millenaire.LOG_VILLAGER_GOALS) {
				org.millenaire.Millenaire.LOGGER.info("Generic goal '{}': priority={} duration={}t dest='{}' requires='{}'{}",
						def.key(), def.priority(), def.duration(), def.destinationTag(), def.requiredTag(),
						defined ? "" : " (placeholder/hard-coded)");
			}
			return new GenericGoal(def);
		});
	}

	/** The hard-coded fallback goals (idle/wander/go-to-townhall/observe-construction). */
	public static Iterable<VillagerGoal> fallbacks() {
		return REGISTRY.values();
	}

	private static int jitter(MillVillagerEntity v) {
		return v.getRandom().nextInt(5);
	}

	private static double distSq(MillVillagerEntity v, BlockPos p) {
		return v.blockPosition().distSqr(p);
	}

	// ---- goals --------------------------------------------------------------------------------

	static final class Idle implements VillagerGoal {
		public String key() {
			return IDLE;
		}

		public boolean isPossible(MillVillagerEntity v, ServerLevel level, TownHall th) {
			return true;
		}

		public int priority(MillVillagerEntity v, ServerLevel level, TownHall th) {
			return 10 + jitter(v);
		}

		public void start(MillVillagerEntity v, ServerLevel level, TownHall th) {
			v.getNavigation().stop();
		}

		public boolean isFinished(MillVillagerEntity v, ServerLevel level, TownHall th) {
			return level.getGameTime() - v.getGoalStartTime() > 60;
		}
	}

	static final class Wander implements VillagerGoal {
		public String key() {
			return WANDER;
		}

		public boolean isPossible(MillVillagerEntity v, ServerLevel level, TownHall th) {
			return true;
		}

		public int priority(MillVillagerEntity v, ServerLevel level, TownHall th) {
			return 20 + jitter(v);
		}

		public void start(MillVillagerEntity v, ServerLevel level, TownHall th) {
			int dx = v.getRandom().nextInt(17) - 8;
			int dz = v.getRandom().nextInt(17) - 8;
			int x = v.blockPosition().getX() + dx;
			int z = v.blockPosition().getZ() + dz;
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			BlockPos target = new BlockPos(x, y, z);
			v.setGoalTarget(target);
			v.getNavigation().moveTo(x + 0.5, y, z + 0.5, 0.5);
		}

		public boolean isFinished(MillVillagerEntity v, ServerLevel level, TownHall th) {
			long elapsed = level.getGameTime() - v.getGoalStartTime();
			return elapsed > 200 || (elapsed > 30 && v.getNavigation().isDone());
		}
	}

	static final class GoToTownHall implements VillagerGoal {
		public String key() {
			return GO_TO_TOWNHALL;
		}

		public boolean isPossible(MillVillagerEntity v, ServerLevel level, TownHall th) {
			return distSq(v, th.centre()) > 6 * 6;
		}

		public int priority(MillVillagerEntity v, ServerLevel level, TownHall th) {
			return 30 + jitter(v);
		}

		public void start(MillVillagerEntity v, ServerLevel level, TownHall th) {
			BlockPos c = th.centre();
			v.setGoalTarget(c);
			v.getNavigation().moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0.55);
		}

		public boolean isFinished(MillVillagerEntity v, ServerLevel level, TownHall th) {
			long elapsed = level.getGameTime() - v.getGoalStartTime();
			return distSq(v, th.centre()) <= 4 * 4 || elapsed > 300 || (elapsed > 30 && v.getNavigation().isDone());
		}
	}

	static final class ObserveConstruction implements VillagerGoal {
		public String key() {
			return OBSERVE_CONSTRUCTION;
		}

		private BuildingProject underConstruction(TownHall th) {
			for (BuildingProject b : th.buildings()) {
				if (!b.isDone() && !b.isBlocked()) {
					return b;
				}
			}
			return null;
		}

		public boolean isPossible(MillVillagerEntity v, ServerLevel level, TownHall th) {
			return underConstruction(th) != null;
		}

		public int priority(MillVillagerEntity v, ServerLevel level, TownHall th) {
			return 40 + jitter(v);
		}

		public void start(MillVillagerEntity v, ServerLevel level, TownHall th) {
			BuildingProject b = underConstruction(th);
			if (b != null) {
				v.setGoalTarget(b.origin());
				v.getNavigation().moveTo(b.origin().getX() + 0.5, b.origin().getY(), b.origin().getZ() + 0.5, 0.5);
			}
		}

		public void tick(MillVillagerEntity v, ServerLevel level, TownHall th) {
			BlockPos t = v.getGoalTarget();
			if (t != null) {
				v.getLookControl().setLookAt(t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5);
			}
		}

		public boolean isFinished(MillVillagerEntity v, ServerLevel level, TownHall th) {
			long elapsed = level.getGameTime() - v.getGoalStartTime();
			BlockPos t = v.getGoalTarget();
			boolean arrived = t != null && distSq(v, t) <= 5 * 5;
			return underConstruction(th) == null || arrived || elapsed > 300;
		}
	}
}
