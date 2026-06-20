package org.millenaire.content.economy;

import java.util.Locale;

/**
 * A quantity of an abstract good ({@code good,qty}) — used by generic crafting input/output and by
 * building limits (intent doc 01 §5.1, doc 04). The good name is the {@code goods.txt} indirection key.
 */
public record GoodStack(String good, int qty) {

	public static GoodStack parse(String text) {
		String[] p = text.split(",");
		int qty = 1;
		if (p.length > 1) {
			try {
				qty = Integer.parseInt(p[1].trim());
			} catch (NumberFormatException ignored) {
				// keep default
			}
		}
		return new GoodStack(p[0].trim().toLowerCase(Locale.ROOT), qty);
	}
}
