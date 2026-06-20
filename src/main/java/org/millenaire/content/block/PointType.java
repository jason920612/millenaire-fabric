package org.millenaire.content.block;

/**
 * One entry from {@code blocklist.txt}: a logical building-cell type.
 *
 * <p>Line format (5 semicolon columns): {@code name;blockRef;meta;secondStep;R/G/B}.
 * An empty {@code blockRef} marks a <b>special tag</b> — a functional point (sleepingPos,
 * mainchest, sellingPos, *soil, *spawn, empty, …) that carries meaning but places no block.
 *
 * @param name       logical name (may contain spaces, e.g. {@code "planks pine"})
 * @param blockRef   1.12-era block reference ({@code minecraft:planks}, numeric {@code 17},
 *                   {@code millenaire:...}) or empty for special tags
 * @param meta       legacy block metadata (no longer exists in 26.2 — translated via flattening)
 * @param secondStep placed in the second construction pass (doors/torches/beds/water/signs)
 * @param colour     RGB key as {@code (R<<16)|(G<<8)|B}
 */
public record PointType(String name, String blockRef, int meta, boolean secondStep, int colour) {

	public boolean isSpecial() {
		return blockRef == null || blockRef.isBlank();
	}

	public boolean isEmpty() {
		return "empty".equalsIgnoreCase(name);
	}

	/** Stable key for the (blockRef, meta) pair, used by the flattening table. */
	public String refMetaKey() {
		return blockRef + "#" + meta;
	}

	public static String colourString(int colour) {
		return ((colour >> 16) & 0xFF) + "/" + ((colour >> 8) & 0xFF) + "/" + (colour & 0xFF);
	}
}
