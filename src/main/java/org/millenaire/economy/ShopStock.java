package org.millenaire.economy;

/**
 * Shop stock and the three-tier price model (intent doc 04/06): {@code traded_goods.txt} culture
 * baseline &rarr; village-type {@code sellingPrice}/{@code buyingPrice} override &rarr; foreign-merchant
 * price track. Goods are referenced by abstract good name (the {@code goods.txt} indirection layer).
 * TODO: implement stock per building + price resolution order.
 */
public final class ShopStock {

	private ShopStock() {
	}

	// TODO: int sellingPrice(good, villageType), buyingPrice(...), stock(building)
}
