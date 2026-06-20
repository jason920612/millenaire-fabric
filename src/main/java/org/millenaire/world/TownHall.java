package org.millenaire.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * The <b>Town Hall aggregate root</b> (INTENT.md doc 02): a village's single source of truth. Owns
 * its sub-buildings and villagers, has a stable id, and carries the active/inactive runtime state
 * that future construction/NPC ticks hook into.
 *
 * <p>Persistent fields are serialized via {@link #CODEC}; {@code active} is runtime-only.
 */
public final class TownHall {

	public static final Codec<TownHall> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(t -> t.id),
			BlockPos.CODEC.fieldOf("centre").forGetter(t -> t.centre),
			Codec.STRING.fieldOf("culture").forGetter(t -> t.culture),
			Codec.STRING.fieldOf("type").forGetter(t -> t.villageType),
			Codec.STRING.fieldOf("name").forGetter(t -> t.name),
			BuildingProject.CODEC.listOf().fieldOf("buildings").forGetter(t -> t.buildings),
			UUIDUtil.CODEC.listOf().fieldOf("villagers").forGetter(t -> t.villagers)
	).apply(i, TownHall::new));

	private final UUID id;
	private final BlockPos centre;
	private final String culture;
	private final String villageType;
	private final String name;
	private final List<BuildingProject> buildings;
	private final List<UUID> villagers;

	/** Runtime only — not persisted. */
	private transient boolean active;

	public TownHall(UUID id, BlockPos centre, String culture, String villageType, String name,
			List<BuildingProject> buildings, List<UUID> villagers) {
		this.id = id;
		this.centre = centre;
		this.culture = culture;
		this.villageType = villageType;
		this.name = name;
		this.buildings = new ArrayList<>(buildings);
		this.villagers = new ArrayList<>(villagers);
	}

	/** Create a fresh Town Hall with a new stable id and empty contents. */
	public static TownHall create(BlockPos centre, String culture, String villageType, String name) {
		return new TownHall(UUID.randomUUID(), centre, culture, villageType, name, new ArrayList<>(), new ArrayList<>());
	}

	public UUID id() {
		return id;
	}

	public BlockPos centre() {
		return centre;
	}

	public String culture() {
		return culture;
	}

	public String villageType() {
		return villageType;
	}

	public String name() {
		return name;
	}

	public List<BuildingProject> buildings() {
		return Collections.unmodifiableList(buildings);
	}

	public List<UUID> villagers() {
		return Collections.unmodifiableList(villagers);
	}

	public void addBuilding(BuildingProject b) {
		buildings.add(b);
	}

	public void addVillager(UUID villagerId) {
		villagers.add(villagerId);
	}

	public boolean isActive() {
		return active;
	}

	/**
	 * Set the active state; returns {@code true} if this was a transition (state changed). The
	 * single entry point future systems use to start/stop a village's heavy simulation.
	 */
	public boolean setActive(boolean newActive) {
		if (this.active == newActive) {
			return false;
		}
		this.active = newActive;
		return true;
	}
}
