package org.millenaire.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.millenaire.Millenaire;

/**
 * A living Millénaire villager. Its behaviour is driven by the custom {@link org.millenaire.entity.ai.VillagerScheduler}
 * (single {@code goalKey} at a time, highest-priority emergent selection — INTENT.md doc 01), NOT by
 * vanilla's parallel {@code GoalSelector}. The only vanilla goal kept is a swimming reflex.
 *
 * <p>Per-villager goal state ({@code goalKey}, target, start time) lives here and is persisted, so a
 * villager keeps its current goal across a reload.
 */
public class MillVillagerEntity extends PathfinderMob {

	private String goalKey = "";
	private BlockPos goalTarget;
	private long goalStartTime;

	public MillVillagerEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
	}

	/** Spawn (or re-spawn, for repair) a villager with a specific UUID, name and position. */
	public static MillVillagerEntity spawn(ServerLevel level, UUID id, String name, BlockPos pos, boolean invulnerable) {
		MillVillagerEntity v = new MillVillagerEntity(Millenaire.VILLAGER, level);
		if (id != null) {
			v.setUUID(id);
		}
		v.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
		if (name != null && !name.isEmpty()) {
			v.setCustomName(Component.literal(name));
			v.setCustomNameVisible(true);
		}
		v.setInvulnerable(invulnerable);
		level.addFreshEntity(v);
		return v;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.5);
	}

	@Override
	protected void registerGoals() {
		// Core decision-making is the custom scheduler; this is only a "don't drown" reflex, not the brain.
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	public String getGoalKey() {
		return goalKey;
	}

	public void setGoalKey(String key) {
		this.goalKey = key == null ? "" : key;
	}

	public BlockPos getGoalTarget() {
		return goalTarget;
	}

	public void setGoalTarget(BlockPos target) {
		this.goalTarget = target;
	}

	public long getGoalStartTime() {
		return goalStartTime;
	}

	public void setGoalStartTime(long time) {
		this.goalStartTime = time;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("MillGoalKey", goalKey);
		output.putLong("MillGoalStart", goalStartTime);
		output.putLong("MillGoalTarget", goalTarget == null ? Long.MIN_VALUE : goalTarget.asLong());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		goalKey = input.getStringOr("MillGoalKey", "");
		goalStartTime = input.getLongOr("MillGoalStart", 0L);
		long t = input.getLongOr("MillGoalTarget", Long.MIN_VALUE);
		goalTarget = t == Long.MIN_VALUE ? null : BlockPos.of(t);
	}
}
