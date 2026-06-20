package org.millenaire.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.millenaire.Millenaire;
import org.millenaire.net.MillHandshakePayload;

/**
 * Client-only entrypoint — L0 skeleton. Lives in the {@code client} sourceset
 * ({@code splitEnvironmentSourceSets()}), so this code never loads on a dedicated server (D2).
 */
public final class MillenaireClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		Millenaire.LOGGER.info("Millénaire client (26.2) initializing — L0 skeleton");

		// Receive the server handshake — proves the channel end-to-end.
		ClientPlayNetworking.registerGlobalReceiver(MillHandshakePayload.TYPE, (payload, context) ->
				Millenaire.LOGGER.info("Received server handshake: '{}'", payload.message()));
	}
}
