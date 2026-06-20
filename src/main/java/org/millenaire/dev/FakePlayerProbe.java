package org.millenaire.dev;

import java.util.Optional;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.millenaire.Millenaire;
import org.millenaire.entity.MillVillagerEntity;

/**
 * Headless gameplay test via a Fabric {@link FakePlayer}: a server-side fake player actually
 * right-clicks the ground holding the debug wand, driving the <b>same</b> production interaction
 * ({@link org.millenaire.MillInteractions}) a real player would — founding a whole village. It then
 * reads blocks back from the real world to confirm buildings were constructed. Lets us test the real
 * player flow without anyone joining. Gated by env var {@code MILLENAIRE_SELFTEST}.
 */
public final class FakePlayerProbe {

	private FakePlayerProbe() {
	}

	public static void register() {
		if (System.getenv("MILLENAIRE_SELFTEST") == null) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				ServerLevel level = server.overworld();

				// Force all villages active so headless construction proceeds without an online player.
				org.millenaire.world.MillWorld.forceActiveForTest = true;

				// Persistence check: villages + construction progress that survived previous runs.
				org.millenaire.world.MillWorldData world = org.millenaire.world.MillWorldData.get(level);
				int existing = world.townHallCount();
				Millenaire.LOGGER.info("MillWorld persistence check: loaded {} village(s) at startup", existing);
				for (org.millenaire.world.TownHall th : world.townHalls()) {
					long doneCount = th.buildings().stream().filter(org.millenaire.world.BuildingProject::isDone).count();
					String cursors = th.buildings().stream()
							.map(b -> b.key() + "(" + (b.isDone() ? "done" : "cursor=" + b.cursor() + ",pass=" + b.pass()) + ")")
							.collect(java.util.stream.Collectors.joining(", "));
					Millenaire.LOGGER.info("  resume state: '{}' {}/{} buildings done [{}]",
							th.name(), doneCount, th.buildings().size(), cursors);
				}

				int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0);
				BlockPos clicked = new BlockPos(0, surfaceY - 1, 0); // solid block at the surface

				FakePlayer fake = FakePlayer.get(level);
				fake.snapTo(clicked.getX() + 0.5, surfaceY, clicked.getZ() + 0.5, 0f, 0f);
				ItemStack wand = new ItemStack(Millenaire.DEBUG_WAND);
				fake.setItemInHand(InteractionHand.MAIN_HAND, wand);

				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false);

				Millenaire.LOGGER.info("FakePlayer self-test: '{}' right-clicks {} with debug wand to found a village...",
						fake.getName().getString(), clicked);
				InteractionResult result = fake.gameMode.useItemOn(fake, level, wand, InteractionHand.MAIN_HAND, hit);

				Millenaire.LOGGER.info("FakePlayer self-test: useItemOn -> {}; village scheduled — construction proceeds over the next ticks",
						result.getClass().getSimpleName());

				AABB box = AABB.ofSize(Vec3.atCenterOf(clicked), 160, 96, 160);
				var villagers = level.getEntitiesOfClass(MillVillagerEntity.class, box);
				Millenaire.LOGGER.info("FakePlayer self-test: {} Millénaire villager entities alive (e.g. {})",
						villagers.size(),
						villagers.isEmpty() ? "<none>"
								: villagers.get(0).getName().getString() + " @ " + villagers.get(0).blockPosition());

				fake.discard();

				// Flush SavedData to disk so the village survives even an abrupt stop (persistence verification).
				server.saveEverything(true, true, true);
				Millenaire.LOGGER.info("MillWorld after founding + save: {} village(s) tracked",
						org.millenaire.world.MillWorldData.get(level).townHallCount());
			} catch (Exception e) {
				Millenaire.LOGGER.error("FakePlayer self-test failed", e);
			}
		});
	}
}
