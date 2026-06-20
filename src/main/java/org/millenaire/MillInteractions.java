package org.millenaire;

import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import org.millenaire.content.MillContent;
import org.millenaire.content.building.BuildingPlacer;
import org.millenaire.content.building.BuildingPlan;
import org.millenaire.content.culture.Culture;
import org.millenaire.content.culture.VillageType;
import org.millenaire.content.world.VillageGenerator;

/**
 * Server-authoritative player interactions. The debug wand:
 * <ul>
 *   <li>right-click &rarr; found a {@code norman} village (centre + start buildings) at that spot;</li>
 *   <li>sneak + right-click &rarr; place a single building (norman armoury) for quick checks.</li>
 * </ul>
 * This is the real production interaction path that the fake-player self-test also drives.
 */
public final class MillInteractions {

	private MillInteractions() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(level instanceof ServerLevel server)) {
				return InteractionResult.PASS;
			}
			if (player.getItemInHand(hand).getItem() != Millenaire.DEBUG_WAND) {
				return InteractionResult.PASS;
			}
			BlockPos clicked = hit.getBlockPos();

			if (player.isShiftKeyDown()) {
				Optional<BuildingPlan> plan = MillContent.building("norman", "armoury_A");
				if (plan.isEmpty()) {
					return InteractionResult.PASS;
				}
				int placed = BuildingPlacer.place(server, clicked.above(), plan.get(), 0);
				Millenaire.LOGGER.info("DEBUG_WAND placed norman armoury_A at {} ({} blocks)", clicked.above(), placed);
				return InteractionResult.SUCCESS;
			}

			Optional<Culture> culture = MillContent.culture("norman");
			Optional<VillageType> type = culture.flatMap(c -> c.villageType("grosbourg"));
			if (culture.isEmpty() || type.isEmpty()) {
				Millenaire.LOGGER.warn("DEBUG_WAND: norman/grosbourg not loaded");
				return InteractionResult.PASS;
			}
			VillageGenerator.generate(server, clicked, culture.get(), type.get());
			return InteractionResult.SUCCESS;
		});
	}
}
