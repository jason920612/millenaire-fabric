package org.millenaire.content.culture;

/**
 * Name generation (intent doc 04). From {@code namelists/}: family-name and given-name pools per culture
 * (by gender), plus child-name pools. {@code culture.txt}'s {@code qualifierSeparator} joins terrain
 * qualifiers ("the hills", "the desert") into village names. TODO: parse the pools; generate villager
 * and village names.
 */
public final class NameList {

	private NameList() {
	}

	// TODO: parse(dir); String randomGivenName(gender, RandomSource); String randomFamilyName(...)
}
