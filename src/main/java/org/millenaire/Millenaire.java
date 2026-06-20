package org.millenaire;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import org.millenaire.content.block.LogicalBlockMapping;
import org.millenaire.entity.MillVillagerEntity;
import org.millenaire.net.MillHandshakePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (server-authoritative) entrypoint — L0 skeleton.
 *
 * <p>Goal of L0: prove the toolchain (Fabric / MC 26.2 / Java 25), register a test item,
 * and stand up the three foundations the rest of the rewrite leans on:
 * <ol>
 *   <li>{@link LogicalBlockMapping} — logical block name &rarr; 26.2 BlockState (coupling A)</li>
 *   <li>{@link org.millenaire.client.gui.GuiText} — shared GUI text model (client sourceset)</li>
 *   <li>{@link MillHandshakePayload} — client/server payload channel (D2: multiplayer-first)</li>
 * </ol>
 */
public final class Millenaire implements ModInitializer {
	public static final String MOD_ID = "millenaire";
	public static final Logger LOGGER = LoggerFactory.getLogger("Millénaire");

	/** Dev: log villager goal switches (the custom scheduler). */
	public static final boolean LOG_VILLAGER_GOALS = true;

	/** A single test item to prove registration works on 26.2. */
	public static final Item DEBUG_WAND = registerItem("debug_wand", new Item.Properties());

	/** The living villager entity (L4). */
	public static final EntityType<MillVillagerEntity> VILLAGER = registerEntity("villager",
			EntityType.Builder.of(MillVillagerEntity::new, MobCategory.CREATURE).sized(0.6f, 1.95f));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static Item registerItem(String path, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		Item item = new Item(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static EntityType<MillVillagerEntity> registerEntity(String path, EntityType.Builder<MillVillagerEntity> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Millénaire (26.2 rewrite) initializing — L0 skeleton");

		// L4: villager entity attributes.
		FabricDefaultAttributeRegistry.register(VILLAGER, MillVillagerEntity.createAttributes());

		// D2 (multiplayer-first): declare the server->client payload channel.
		PayloadTypeRegistry.clientboundPlay().register(MillHandshakePayload.TYPE, MillHandshakePayload.CODEC);

		// On join, fire a handshake to prove the channel end-to-end.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				ServerPlayNetworking.send(handler.player, new MillHandshakePayload("millenaire-l0-handshake")));

		// Foundation A: warm up the logical block mapping (seed; L1 fills it from blocklist.txt).
		LogicalBlockMapping.bootstrap();

		// L1: load the whole content tree through the real production pipeline.
		org.millenaire.content.ContentLoader.load();

		// L2/L3 bridge: debug wand builds a schematic where you right-click.
		MillInteractions.register();

		// MillWorld: release any chunks left force-loaded by a previous session, then run the
		// active/inactive proximity state machine each tick (INTENT.md doc 02).
		ServerLifecycleEvents.SERVER_STARTED.register(
				server -> org.millenaire.world.MillWorld.releaseAllForcedChunksOnStart(server.overworld()));
		ServerTickEvents.END_SERVER_TICK.register(server -> org.millenaire.world.MillWorld.tick(server.overworld()));

		// Headless gameplay verification via a fake player (env-gated).
		org.millenaire.dev.FakePlayerProbe.register();

		LOGGER.info("Millénaire init complete. DEBUG_WAND = {}", BuiltInRegistries.ITEM.getKey(DEBUG_WAND));
	}
}
