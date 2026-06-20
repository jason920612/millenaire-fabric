package org.millenaire.content.building;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.millenaire.Millenaire;
import org.millenaire.content.Dsl;
import org.millenaire.content.block.BlockList;
import org.millenaire.content.block.PointType;

/**
 * A building plan: one {@code <key>_<variant>.txt} plus its per-upgrade-level layered PNGs
 * ({@code <key>_<variant><level>.png}). Parses the {@code key:value;} config lines (one per level,
 * level 0 carrying the global fields) and decodes each PNG into a 3-D grid of {@link PointType}.
 *
 * <p>PNG encoding (authoritative, from the original {@code BuildingPlan.java}):
 * a single image tiles all Y-floors horizontally with a 1px separator, so
 * {@code nbfloors = (imgWidth + 1) / (width + 1)} and image {@code height == length} (Z).
 * Pixel X is stored mirrored: {@code px = i*width + i + (width - k - 1)}. A pixel whose alpha is
 * not 0xFF (or pure white) is <i>empty</i> (does not overwrite — upgrade layers only draw deltas).
 */
public final class BuildingPlan {

	public record Level(int index, Dsl.Record config, PointType[][][] grid, int nbfloors) {
	}

	private final String key;
	private final String variant;
	private final int width;
	private final int length;
	private final int startLevel;
	private final List<Level> levels;

	private BuildingPlan(String key, String variant, int width, int length, int startLevel, List<Level> levels) {
		this.key = key;
		this.variant = variant;
		this.width = width;
		this.length = length;
		this.startLevel = startLevel;
		this.levels = levels;
	}

	public String key() {
		return key;
	}

	public String variant() {
		return variant;
	}

	public int width() {
		return width;
	}

	public int length() {
		return length;
	}

	public int startLevel() {
		return startLevel;
	}

	public List<Level> levels() {
		return levels;
	}

	/** Parse a building {@code <key>_<variant>.txt} and decode all its level PNGs. */
	public static BuildingPlan parse(Path txtFile, BlockList blockList) throws IOException {
		String fileBase = txtFile.getFileName().toString().replaceFirst("\\.txt$", ""); // e.g. armoury_A
		int us = fileBase.lastIndexOf('_');
		String key = us >= 0 ? fileBase.substring(0, us) : fileBase;
		String variant = us >= 0 ? fileBase.substring(us + 1) : "";

		List<Dsl.Record> levelConfigs = new ArrayList<>();
		for (String line : Dsl.readLines(txtFile)) {
			if (line.isBlank()) {
				continue;
			}
			levelConfigs.add(Dsl.parseColonSegments(line));
		}
		if (levelConfigs.isEmpty()) {
			throw new IOException("Empty building plan: " + txtFile);
		}

		Dsl.Record level0 = levelConfigs.get(0);
		int width = level0.firstInt("width", 0);
		int length = level0.firstInt("length", 0);
		int startLevel = level0.firstInt("startLevel", 0);
		if (width <= 0 || length <= 0) {
			throw new IOException(fileBase + ": missing width/length in level 0 config");
		}

		List<Level> levels = new ArrayList<>();
		Path dir = txtFile.getParent();
		for (int i = 0; i < levelConfigs.size(); i++) {
			Path png = dir.resolve(fileBase + i + ".png");
			if (!Files.exists(png)) {
				Millenaire.LOGGER.debug("BuildingPlan {}: missing PNG for level {} ({}), skipping level",
						fileBase, i, png.getFileName());
				continue;
			}
			DecodeResult dr = decode(png, width, length, blockList, fileBase, i);
			levels.add(new Level(i, levelConfigs.get(i), dr.grid, dr.nbfloors));
		}
		return new BuildingPlan(key, variant, width, length, startLevel, levels);
	}

	private record DecodeResult(PointType[][][] grid, int nbfloors) {
	}

	private static DecodeResult decode(Path png, int width, int length, BlockList blockList, String name, int level)
			throws IOException {
		BufferedImage img = ImageIO.read(png.toFile());
		if (img == null) {
			throw new IOException("Could not read PNG: " + png);
		}
		if (img.getHeight() != length) {
			throw new IOException(name + "_" + level + ": expected height(length)=" + length
					+ " but PNG height=" + img.getHeight());
		}
		float fnbfloors = (img.getWidth() + 1f) / (width + 1f);
		if (Math.round(fnbfloors) != fnbfloors) {
			throw new IOException(name + "_" + level + ": non-integer floor count " + fnbfloors
					+ " (imgWidth=" + img.getWidth() + ", width=" + width + ")");
		}
		int nbfloors = Math.round(fnbfloors);

		PointType empty = blockList.byColour(0x00FFFFFF).orElse(null);
		PointType[][][] grid = new PointType[nbfloors][length][width];

		for (int i = 0; i < nbfloors; i++) {
			for (int j = 0; j < length; j++) {
				for (int k = 0; k < width; k++) {
					int argb = img.getRGB(i * width + i + width - k - 1, j);
					int colour;
					if (((argb >>> 24) & 0xFF) != 0xFF) {
						colour = 0x00FFFFFF; // transparent -> empty
					} else {
						colour = argb & 0x00FFFFFF;
					}
					PointType pt = blockList.byColour(colour).orElse(null);
					if (pt == null) {
						pt = empty; // unknown colour: treat as empty (original logs + skips)
					}
					grid[i][j][k] = pt;
				}
			}
		}
		return new DecodeResult(grid, nbfloors);
	}

	/** Count non-empty cells across all floors of a given level (for the L1 self-test). */
	public int nonEmptyCells(int levelIndex) {
		Level lvl = levels.get(levelIndex);
		int count = 0;
		for (PointType[][] floor : lvl.grid()) {
			for (PointType[] row : floor) {
				for (PointType pt : row) {
					if (pt != null && !pt.isEmpty() && !pt.isSpecial()) {
						count++;
					}
				}
			}
		}
		return count;
	}
}
