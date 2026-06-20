package org.millenaire.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * A building a {@link TownHall} owns, together with its <b>persistent construction progress</b>
 * (INTENT.md doc 03: building is gradual, interruptible, two-pass). Villagers build it block by
 * block over time; the {@code cursor}/{@code pass}/{@code done} fields persist so construction
 * resumes exactly where it left off after a reload.
 *
 * @param origin      world origin where the plan is anchored
 * @param level       upgrade level being built (0 at founding)
 * @param orientation placement orientation 0..3 (read from the plan; no longer fixed 0)
 * @param cursor      linear index into {@code floor*length*width} for the current pass
 * @param pass        0 = main structure, 1 = secondStep (doors/torches/beds/...)
 * @param done        construction complete
 */
public final class BuildingProject implements GoodsStore {

	public static final Codec<BuildingProject> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("key").forGetter(b -> b.key),
			Codec.STRING.fieldOf("variant").forGetter(b -> b.variant),
			Codec.STRING.fieldOf("role").forGetter(b -> b.role),
			BlockPos.CODEC.fieldOf("origin").forGetter(b -> b.origin),
			Codec.INT.fieldOf("level").forGetter(b -> b.level),
			Codec.INT.fieldOf("orientation").forGetter(b -> b.orientation),
			Codec.INT.fieldOf("cursor").forGetter(b -> b.cursor),
			Codec.INT.fieldOf("pass").forGetter(b -> b.pass),
			Codec.BOOL.fieldOf("done").forGetter(b -> b.done),
			Codec.BOOL.optionalFieldOf("blocked", false).forGetter(b -> b.blocked),
			Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(b -> b.tags),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("goods", Map.of()).forGetter(b -> b.goods)
	).apply(i, BuildingProject::new));

	private final String key;
	private final String variant;
	private final String role;
	private final BlockPos origin;
	private final int level;
	private final int orientation;
	private int cursor;
	private int pass;
	private boolean done;
	/** Construction cannot proceed (e.g. plan not found) — surfaced, not silently "done". */
	private boolean blocked;
	/** The building's tags (from the plan's {@code tag:} fields) — used to resolve goal destinations. */
	private final List<String> tags;
	/** This building's own goods stock — what generic crafting in this building consumes/produces. */
	private final Map<String, Integer> goods;

	public BuildingProject(String key, String variant, String role, BlockPos origin, int level, int orientation,
			int cursor, int pass, boolean done, boolean blocked, List<String> tags, Map<String, Integer> goods) {
		this.key = key;
		this.variant = variant;
		this.role = role;
		this.origin = origin;
		this.level = level;
		this.orientation = orientation;
		this.cursor = cursor;
		this.pass = pass;
		this.done = done;
		this.blocked = blocked;
		this.tags = List.copyOf(tags);
		this.goods = new HashMap<>(goods);
	}

	/** A fresh, unbuilt project. */
	public BuildingProject(String key, String variant, String role, BlockPos origin, int level, int orientation,
			List<String> tags) {
		this(key, variant, role, origin, level, orientation, 0, 0, false, false, tags, new HashMap<>());
	}

	@Override
	public int countGood(String good) {
		return goods.getOrDefault(good.toLowerCase(java.util.Locale.ROOT), 0);
	}

	@Override
	public void addGood(String good, int qty) {
		goods.merge(good.toLowerCase(java.util.Locale.ROOT), qty, Integer::sum);
	}

	@Override
	public boolean removeGood(String good, int qty) {
		String k = good.toLowerCase(java.util.Locale.ROOT);
		int have = goods.getOrDefault(k, 0);
		if (have < qty) {
			return false;
		}
		goods.put(k, have - qty);
		return true;
	}

	public List<String> tags() {
		return tags;
	}

	public boolean hasTag(String tag) {
		return tags.contains(tag);
	}

	public String key() {
		return key;
	}

	public String variant() {
		return variant;
	}

	public String role() {
		return role;
	}

	public BlockPos origin() {
		return origin;
	}

	public int level() {
		return level;
	}

	public int orientation() {
		return orientation;
	}

	public int cursor() {
		return cursor;
	}

	public int pass() {
		return pass;
	}

	public boolean isDone() {
		return done;
	}

	public boolean isBlocked() {
		return blocked;
	}

	public void setBlocked(boolean blocked) {
		this.blocked = blocked;
	}

	public void setCursor(int cursor) {
		this.cursor = cursor;
	}

	public void setPass(int pass) {
		this.pass = pass;
	}

	public void setDone(boolean done) {
		this.done = done;
	}
}
