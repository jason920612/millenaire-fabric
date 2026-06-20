package org.millenaire.content.economy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.millenaire.Millenaire;
import org.millenaire.content.Dsl;

/**
 * The global {@code goods.txt} registry — the economy's universal indirection layer (INTENT.md
 * doc 04/06): every production/trade/building reference uses an abstract <b>good name</b> that this
 * file binds to a concrete item (id + legacy meta + indicative label).
 *
 * <p>Line format: {@code goodName;itemId;itemMeta;label}. {@code itemId == "null"} means the good
 * has no backing item (e.g. {@code anyenchanted}). Item flattening (meta &rarr; modern id) is wired
 * later (mirrors {@link org.millenaire.content.block.BlockList}); for L1 we keep the raw binding.
 */
public final class Goods {

	public record Good(String name, String itemId, int meta, String label) {
		public boolean hasItem() {
			return itemId != null && !itemId.isBlank() && !itemId.equalsIgnoreCase("null");
		}
	}

	private final Map<String, Good> byName = new HashMap<>();

	public Optional<Good> get(String name) {
		return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
	}

	public int size() {
		return byName.size();
	}

	public int withItemCount() {
		return (int) byName.values().stream().filter(Good::hasItem).count();
	}

	public static Goods load(Path goodsFile) throws Exception {
		Goods goods = new Goods();
		for (String line : Dsl.readLines(goodsFile)) {
			if (Dsl.isComment(line)) {
				continue;
			}
			String[] c = Dsl.splitSemicolons(line);
			if (c.length < 3) {
				continue;
			}
			String name = c[0].trim().toLowerCase(Locale.ROOT);
			String itemId = c[1].trim();
			int meta;
			try {
				meta = Integer.parseInt(c[2].trim());
			} catch (NumberFormatException e) {
				meta = 0;
			}
			String label = c.length >= 4 ? c[3].trim() : "";
			goods.byName.put(name, new Good(name, itemId, meta, label));
		}
		Millenaire.LOGGER.info("Goods: {} entries ({} with a backing item)", goods.size(), goods.withItemCount());
		return goods;
	}
}
