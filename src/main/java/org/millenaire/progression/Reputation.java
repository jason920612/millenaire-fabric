package org.millenaire.progression;

/**
 * Reputation as a <b>permission ladder</b>, not a stat bonus (intent doc 05): thresholds defined in
 * {@code languages/<lang>/<culture>_reputation.txt} ({@code level;Label;Desc}, level via a base-64
 * expression) progressively unlock actions/buttons on the village UI. The "the better the relationship,
 * the more you can do" progressive disclosure is the progression spine. TODO: parse the ladder; map a
 * reputation value to the unlocked tier.
 */
public final class Reputation {

	private Reputation() {
	}

	// TODO: List<Tier> ladder(culture); Tier tierFor(int reputation)
}
