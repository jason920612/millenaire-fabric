package org.millenaire.entity.ai;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * A goal's resolved destination (intent doc 01 §2.3): where to go, which building it belongs to, and
 * an optional target entity. Stored per-villager (goals are stateless singletons); read back by the
 * scheduler to drive navigation and "arrived?" checks.
 *
 * @param dest            the world position to travel to (may be {@code null} if entity-targeted)
 * @param destBuildingPos the owning building's origin ({@code null} if none)
 * @param targetEntity    a target entity's UUID ({@code null} if positional)
 */
public record GoalInformation(BlockPos dest, BlockPos destBuildingPos, UUID targetEntity) {

	public static GoalInformation at(BlockPos dest) {
		return new GoalInformation(dest, null, null);
	}

	public static GoalInformation atBuilding(BlockPos dest, BlockPos buildingPos) {
		return new GoalInformation(dest, buildingPos, null);
	}

	public static GoalInformation onEntity(UUID target) {
		return new GoalInformation(null, null, target);
	}

	public Optional<BlockPos> destination() {
		return Optional.ofNullable(dest);
	}
}
