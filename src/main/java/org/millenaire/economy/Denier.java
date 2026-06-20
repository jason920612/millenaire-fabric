package org.millenaire.economy;

/**
 * The denier currency (intent doc 06): three coins in base-64 — {@code denier} (copper),
 * {@code denier_argent} (silver), {@code denier_or} (gold), where {@code 1 gold = 64 silver = 64*64
 * copper}. TODO: total&harr;coins conversion with change-making, and the {@code ItemPurse} NBT amount.
 */
public final class Denier {

	private Denier() {
	}

	public static final int COPPER = 1;
	public static final int SILVER = 64;
	public static final int GOLD = 64 * 64;

	// TODO: int[] toCoins(int total) -> {gold, silver, copper}; int totalFromCoins(int g, int s, int c)
}
