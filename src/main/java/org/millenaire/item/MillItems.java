package org.millenaire.item;

/**
 * Registration holder for Millénaire items (intent doc 06). Intended items: summoning wand (found/import
 * a village), negation wand (manage/export), parchment (village scroll / lore), purse (denier amount in
 * NBT), the three denier coins, and culture-specific items. TODO: register explicitly (the original used
 * reflection — doc 06; use a 26.2 {@code Registry}) and wire their interactions ({@link org.millenaire.MillInteractions}).
 */
public final class MillItems {

	private MillItems() {
	}

	// TODO: public static Item SUMMONING_WAND, NEGATION_WAND, PARCHMENT, PURSE, DENIER, DENIER_ARGENT, DENIER_OR;
	// TODO: static void register();
}
