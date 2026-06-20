package org.millenaire.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.millenaire.Millenaire;

/**
 * {@code MillWorld} persistence: the single source of truth for all {@link TownHall} villages
 * (INTENT.md doc 02), stored once per world via vanilla Codec-based {@link SavedData}. Survives
 * save/reload and is queryable without loading heavy per-village state.
 *
 * <p>The town-hall list is exposed read-only; all mutation goes through methods here so
 * {@link #setDirty()} is never missed — important once the aggregate grows.
 */
public class MillWorldData extends SavedData {

	/** Two town halls closer than this share an identity — used for duplicate-founding prevention. */
	public static final int MIN_VILLAGE_DISTANCE = 32;

	public static final Codec<MillWorldData> CODEC = RecordCodecBuilder.create(i -> i.group(
			TownHall.CODEC.listOf().fieldOf("townHalls").forGetter(d -> d.townHalls)
	).apply(i, MillWorldData::new));

	/** Datafix type: our Codec round-trips our own structure; LEVEL is a benign label (no fixers apply at current version). */
	public static final SavedDataType<MillWorldData> TYPE =
			new SavedDataType<>(Millenaire.id("world"), MillWorldData::new, CODEC, DataFixTypes.LEVEL);

	private final List<TownHall> townHalls;

	public MillWorldData() {
		this.townHalls = new ArrayList<>();
	}

	public MillWorldData(List<TownHall> townHalls) {
		this.townHalls = new ArrayList<>(townHalls);
	}

	/** The MillWorld index lives on the overworld's data storage (villages are tracked world-wide there). */
	public static MillWorldData get(ServerLevel anyLevel) {
		ServerLevel overworld = anyLevel.getServer().overworld();
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Read-only view; mutate via {@link #addTownHall} / {@link #markChanged}. */
	public List<TownHall> townHalls() {
		return Collections.unmodifiableList(townHalls);
	}

	public int townHallCount() {
		return townHalls.size();
	}

	/** Existing town hall whose centre is within {@link #MIN_VILLAGE_DISTANCE} of {@code pos}, if any. */
	public Optional<TownHall> findNear(net.minecraft.core.BlockPos pos) {
		long minSq = (long) MIN_VILLAGE_DISTANCE * MIN_VILLAGE_DISTANCE;
		for (TownHall t : townHalls) {
			if (t.centre().distSqr(pos) <= minSq) {
				return Optional.of(t);
			}
		}
		return Optional.empty();
	}

	/**
	 * Register a new town hall, rejecting duplicates that overlap an existing village's centre.
	 *
	 * @return {@code true} if added; {@code false} if rejected as a duplicate
	 */
	public boolean addTownHall(TownHall townHall) {
		Optional<TownHall> existing = findNear(townHall.centre());
		if (existing.isPresent()) {
			Millenaire.LOGGER.info("MillWorld: duplicate village near {} (existing '{}' at {}) — skipped",
					townHall.centre(), existing.get().name(), existing.get().centre());
			return false;
		}
		townHalls.add(townHall);
		setDirty();
		Millenaire.LOGGER.info("MillWorld now tracks {} village(s); added '{}' ({}) id={} at {} [{} buildings, {} villagers]",
				townHalls.size(), townHall.name(), townHall.culture(), townHall.id(), townHall.centre(),
				townHall.buildings().size(), townHall.villagers().size());
		return true;
	}

	/** Call after mutating a TownHall in place (e.g. growth) so persistence stays correct. */
	public void markChanged() {
		setDirty();
	}
}
