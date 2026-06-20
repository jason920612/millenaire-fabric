package org.millenaire.world;

/**
 * Village growth (intent doc 02 §3.2): an active village keeps picking the next building project to
 * construct or upgrade. Exhaust "new building" tiers (centre/start/core/secondary) before opening
 * upgrades; weighted-random choice; non-player villages must have a building "bought" before paid/gift
 * builds; villages never naturally die. TODO: implement; integrates with {@link Construction}.
 */
public final class VillageGrowth {

	private VillageGrowth() {
	}

	// TODO: nextProject(TownHall) -> Optional<BuildingProject>  (decides what to build next)
	// TODO: noProjectsLeft(TownHall)
}
