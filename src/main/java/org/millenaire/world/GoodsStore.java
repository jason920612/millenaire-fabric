package org.millenaire.world;

/**
 * A goods inventory (good name &rarr; count). Implemented by both {@link BuildingProject} (a building's
 * own stock — where generic crafting consumes/produces, intent doc 01 §5.1/§5.3) and {@link TownHall}
 * (the village-wide stock used by {@code townhallgoal} goals and deliveries).
 */
public interface GoodsStore {

	int countGood(String good);

	void addGood(String good, int qty);

	/** Remove up to {@code qty}; returns true if there was enough. */
	boolean removeGood(String good, int qty);
}
