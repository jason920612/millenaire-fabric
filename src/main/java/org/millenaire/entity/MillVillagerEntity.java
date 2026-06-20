package org.millenaire.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * L4 first slice: a living Millénaire villager. For now a {@link PathfinderMob} with vanilla wander
 * goals so it walks around the village — proves custom entity registration + spawning + AI on 26.2.
 *
 * <p>The real Millénaire goal system (data-driven, single-highest-priority emergent scheduler,
 * INTENT.md doc 01) replaces these vanilla goals in the full L4. Culture/type/name/job, daily
 * routine, production and the client renderer are follow-ups.
 */
public class MillVillagerEntity extends PathfinderMob {

	public MillVillagerEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.5);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0f));
		this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}
}
