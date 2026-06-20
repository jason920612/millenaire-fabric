package org.millenaire.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * Per-player relationship and progress (intent doc 02 §2.7 / doc 05). The <b>player side</b> owns the
 * reputation relationship (not the village): village reputation (keyed by village centre) + culture
 * reputation, plus controlled villages, quest instances, language proficiency and player tags.
 *
 * <p>Keyed by player <b>UUID</b> (not display name — doc 02 §8.1). TODO: persist as a per-player
 * SavedData / data attachment; wire {@code adjustReputation} into trade and quests.
 */
public final class UserProfile {

	private final UUID player;
	private final Map<BlockPos, Integer> villageReputation = new HashMap<>();
	private final Map<String, Integer> cultureReputation = new HashMap<>();

	public UserProfile(UUID player) {
		this.player = player;
	}

	public UUID player() {
		return player;
	}

	/** Effective reputation toward a village = its village-level rep + the culture-level rep (doc 02 §3.5). */
	public int reputation(BlockPos villageCentre, String culture) {
		return villageReputation.getOrDefault(villageCentre, 0) + cultureReputation.getOrDefault(culture, 0);
	}

	// TODO: adjustReputation(villageCentre, culture, delta); controlledVillages; questInstances; tags
}
