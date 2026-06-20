package org.millenaire.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.millenaire.Millenaire;

/**
 * L0 test payload (server &rarr; client). Proves the custom networking channel works on 26.2,
 * which the whole D2 multiplayer-first plan depends on. Later layers add the real packets
 * (PACKET_BUILDING, PACKET_GUIACTION, content negotiation, etc. — see INTENT.md doc 06).
 */
public record MillHandshakePayload(String message) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<MillHandshakePayload> TYPE =
			new CustomPacketPayload.Type<>(Millenaire.id("handshake"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MillHandshakePayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.STRING_UTF8, MillHandshakePayload::message,
					MillHandshakePayload::new);

	@Override
	public CustomPacketPayload.Type<MillHandshakePayload> type() {
		return TYPE;
	}
}
