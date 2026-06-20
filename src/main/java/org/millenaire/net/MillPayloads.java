package org.millenaire.net;

/**
 * Server-authoritative networking map (intent doc 06). The original used a single channel where the
 * server is the only authority — even "which GUI opens" is decided server-side ({@code PACKET_OPENGUI}).
 *
 * <p>Intended payloads (TODO: define as {@code CustomPacketPayload} records + register via
 * {@code PayloadTypeRegistry}, mirroring {@link MillHandshakePayload}):
 * <ul>
 *   <li>{@code BuildingSyncPayload} — PACKET_BUILDING, the heavy one carrying most GUI data;</li>
 *   <li>{@code GuiActionPayload} — PACKET_GUIACTION, the universal C2S action dispatcher (~25 sub-actions);</li>
 *   <li>{@code OpenGuiPayload} — server tells the client which screen to open;</li>
 *   <li>{@code PanelUpdatePayload} — village dashboard panels;</li>
 *   <li>{@code ContentNegotiationPayload} — client declares owned content, server sends the diff.</li>
 * </ul>
 * Optimistic local prediction (client runs the action locally for instant feedback, then the
 * server-authoritative packet overwrites) is the original's sync style — preserve the semantics.
 */
public final class MillPayloads {

	private MillPayloads() {
	}
}
