package org.millenaire.block;

/**
 * Registration holder for Millénaire blocks (intent doc 06). Intended blocks: panels/signs (read-only
 * village dashboards — census/population/build progress/military/trade), culture-specific decorative
 * blocks, and custom crops (8 growth stages, irrigation/slow-growth). Panels need a BlockEntity.
 * TODO: register; map logical block names via {@link org.millenaire.content.block.LogicalBlockMapping}.
 */
public final class MillBlocks {

	private MillBlocks() {
	}

	// TODO: public static Block PANEL, ... ; BlockEntityType<PanelBlockEntity>; static void register();
}
