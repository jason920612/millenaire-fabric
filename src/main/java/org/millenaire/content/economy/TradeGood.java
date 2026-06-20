package org.millenaire.content.economy;

/**
 * One {@code traded_goods.txt} row (intent doc 04/06): the culture-wide price baseline for a good. The
 * full CSV has ten columns (selling/buying/reserved/target/foreignMerchant/minReputation/descCode …);
 * this skeleton captures the essentials. Resolution order at runtime: this baseline &rarr; village-type
 * override &rarr; foreign-merchant track (see {@link org.millenaire.economy.ShopStock}). TODO: full parse.
 *
 * @param good          abstract good name (resolved to an item via {@code goods.txt})
 * @param sellingPrice  price the village sells to the player (denier)
 * @param buyingPrice   price the village buys from the player
 * @param minReputation reputation required to trade this good
 */
public record TradeGood(String good, int sellingPrice, int buyingPrice, int minReputation) {
}
