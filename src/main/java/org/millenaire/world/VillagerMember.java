package org.millenaire.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * A villager a {@link TownHall} owns: its stable {@code id}, display {@code name} and {@code home}
 * (spawn) position. Persisting the id + home lets the village <b>repair</b> a member whose entity is
 * gone after a reload — respawning it with the same UUID so the membership stays consistent
 * (no duplicates). The richer per-villager record (job, family, vrecords) comes later.
 */
public record VillagerMember(UUID id, String name, String type, BlockPos home, BlockPos homeBuilding) {

	public static final Codec<VillagerMember> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(VillagerMember::id),
			Codec.STRING.fieldOf("name").forGetter(VillagerMember::name),
			Codec.STRING.optionalFieldOf("type", "").forGetter(VillagerMember::type),
			BlockPos.CODEC.fieldOf("home").forGetter(VillagerMember::home),
			BlockPos.CODEC.optionalFieldOf("homeBuilding", BlockPos.ZERO).forGetter(VillagerMember::homeBuilding)
	).apply(i, VillagerMember::new));
}
