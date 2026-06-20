package org.millenaire.world;

/**
 * Village↔village diplomacy (intent doc 02 §3.4). Relation values in [-100, 100]; initialised by
 * proximity within BackgroundRadius (same hamlet/owner = max, same culture = good, else bad); nightly
 * probabilistic drift, broadcasting to nearby players when crossing a threshold. Player-controlled and
 * lone buildings don't participate. TODO: implement; relation data lives on the {@link TownHall}.
 */
public final class Relations {

	private Relations() {
	}

	// TODO: initialiseRelations(TownHall), nightlyDrift(TownHall), getRelation(a, b)
}
