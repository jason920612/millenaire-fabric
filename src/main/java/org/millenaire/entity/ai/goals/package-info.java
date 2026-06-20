/**
 * Hard-coded villager goals (intent doc 01 §5.1): special behaviours the data-driven generic goal
 * system cannot express — sleep, fetch-tool, chop whole trees, mine to a vein, bring resources home,
 * deliver to shop/household, be a seller, etc. New <i>recipes/crops</i> are data (goals/*.txt); these
 * are the irreducibly-procedural behaviours.
 *
 * <p>TODO: ChopTreesGoal, MineGoal, BringBackResourcesHomeGoal, DeliverGoodsGoal, BeSellerGoal, …
 */
package org.millenaire.entity.ai.goals;
