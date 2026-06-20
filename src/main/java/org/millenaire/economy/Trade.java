package org.millenaire.economy;

/**
 * Server-authoritative trade settlement (intent doc 06: {@code ContainerTrade.slotClick}). Prices are
 * static (no supply/demand swing); reputation only gates access ({@code MIN_REPUTATION_FOR_TRADE}), it
 * does not change unit price. Every purchase adjusts reputation (paying = +rep) and culture language
 * proficiency. TODO: implement against a 26.2 {@code ScreenHandler} with server-side settlement.
 */
public final class Trade {

	private Trade() {
	}

	// TODO: buy(player, shop, good, qty), sell(...), price lookups via ShopStock
}
