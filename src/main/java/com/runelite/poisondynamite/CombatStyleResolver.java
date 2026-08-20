package com.runelite.poisondynamite;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Resolves the player's actual combat style from the selected attack style
 * (via the weapon styles cache enum, same lookup as script4525) instead of
 * guessing from equipment bonuses alone, and folds in prayer and stance
 * accuracy bonuses.
 */
@Singleton
class CombatStyleResolver
{
	enum Stance
	{
		ACCURATE, AGGRESSIVE, CONTROLLED, DEFENSIVE,
		RANGING, LONGRANGE, CASTING, DEFENSIVE_CASTING, OTHER
	}

	private final Client client;
	private final ItemManager itemManager;

	@Inject
	CombatStyleResolver(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/** Combat style category: stab, slash, crush, ranged or magic. */
	String getAttackStyle()
	{
		switch (getStance())
		{
			case RANGING:
			case LONGRANGE:
				return "ranged";
			case CASTING:
			case DEFENSIVE_CASTING:
				return "magic";
			default:
				return getMeleeType();
		}
	}

	int getEffectiveAttackLevel(String style)
	{
		Skill skill;
		switch (style)
		{
			case "ranged":
				skill = Skill.RANGED;
				break;
			case "magic":
				skill = Skill.MAGIC;
				break;
			default:
				skill = Skill.ATTACK;
				break;
		}
		int visibleLevel = client.getBoostedSkillLevel(skill);
		return HitChanceCalculator.getEffectiveLevel(
			visibleLevel, getPrayerMultiplier(style), getStanceBonus());
	}

	int getEquipmentAttackBonus(String style)
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return 0;
		}

		int total = 0;
		for (Item item : equipment.getItems())
		{
			ItemEquipmentStats eq = getEquipmentStats(item);
			if (eq == null)
			{
				continue;
			}
			switch (style)
			{
				case "stab":
					total += eq.getAstab();
					break;
				case "slash":
					total += eq.getAslash();
					break;
				case "crush":
					total += eq.getAcrush();
					break;
				case "magic":
					total += eq.getAmagic();
					break;
				case "ranged":
					total += eq.getArange();
					break;
			}
		}
		return total;
	}

	private Stance getStance()
	{
		int styleIndex = client.getVarpValue(VarPlayerID.COM_MODE);
		int weaponType = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);

		// from script4525: staffs use style 4 plus a casting mode varbit
		// for defensive casting
		if (styleIndex == 4)
		{
			styleIndex += client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE);
		}

		Stance[] stances = getWeaponTypeStances(weaponType);
		if (styleIndex < 0 || styleIndex >= stances.length || stances[styleIndex] == null)
		{
			return Stance.OTHER;
		}
		return stances[styleIndex];
	}

	private Stance[] getWeaponTypeStances(int weaponType)
	{
		int weaponStyleEnum = client.getEnum(EnumID.WEAPON_STYLES).getIntValue(weaponType);
		if (weaponStyleEnum == -1)
		{
			return new Stance[0];
		}

		int[] structs = client.getEnum(weaponStyleEnum).getIntVals();
		Stance[] stances = new Stance[structs.length];
		int i = 0;
		for (int structId : structs)
		{
			StructComposition struct = client.getStructComposition(structId);
			String name = struct.getStringValue(ParamID.ATTACK_STYLE_NAME);
			Stance stance;
			try
			{
				stance = Stance.valueOf(name.toUpperCase());
			}
			catch (IllegalArgumentException e)
			{
				stance = Stance.OTHER;
			}
			// "Defensive" in slot 5 is defensive casting
			if (i == 5 && stance == Stance.DEFENSIVE)
			{
				stance = Stance.DEFENSIVE_CASTING;
			}
			stances[i++] = stance;
		}
		return stances;
	}

	private int getStanceBonus()
	{
		int styleIndex = client.getVarpValue(VarPlayerID.COM_MODE);
		switch (getStance())
		{
			case ACCURATE:
				return 3;
			case CONTROLLED:
				return 1;
			case RANGING:
			case CASTING:
				// index 0 is the accurate stance for ranged and powered staves
				return styleIndex == 0 ? 3 : 0;
			default:
				return 0;
		}
	}

	private double getPrayerMultiplier(String style)
	{
		switch (style)
		{
			case "ranged":
				if (client.isPrayerActive(Prayer.RIGOUR))
				{
					return 1.20;
				}
				if (client.isPrayerActive(Prayer.DEADEYE))
				{
					return 1.18;
				}
				if (client.isPrayerActive(Prayer.EAGLE_EYE))
				{
					return 1.15;
				}
				if (client.isPrayerActive(Prayer.HAWK_EYE))
				{
					return 1.10;
				}
				if (client.isPrayerActive(Prayer.SHARP_EYE))
				{
					return 1.05;
				}
				return 1.0;
			case "magic":
				if (client.isPrayerActive(Prayer.AUGURY))
				{
					return 1.25;
				}
				if (client.isPrayerActive(Prayer.MYSTIC_VIGOUR))
				{
					return 1.18;
				}
				if (client.isPrayerActive(Prayer.MYSTIC_MIGHT))
				{
					return 1.15;
				}
				if (client.isPrayerActive(Prayer.MYSTIC_LORE))
				{
					return 1.10;
				}
				if (client.isPrayerActive(Prayer.MYSTIC_WILL))
				{
					return 1.05;
				}
				return 1.0;
			default:
				if (client.isPrayerActive(Prayer.PIETY))
				{
					return 1.20;
				}
				if (client.isPrayerActive(Prayer.CHIVALRY)
					|| client.isPrayerActive(Prayer.INCREDIBLE_REFLEXES))
				{
					return 1.15;
				}
				if (client.isPrayerActive(Prayer.IMPROVED_REFLEXES))
				{
					return 1.10;
				}
				if (client.isPrayerActive(Prayer.CLARITY_OF_THOUGHT))
				{
					return 1.05;
				}
				return 1.0;
		}
	}

	private String getMeleeType()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return "crush";
		}

		Item[] items = equipment.getItems();
		int weaponIdx = EquipmentInventorySlot.WEAPON.getSlotIdx();
		if (weaponIdx >= items.length)
		{
			return "crush";
		}

		ItemEquipmentStats eq = getEquipmentStats(items[weaponIdx]);
		if (eq == null)
		{
			// unarmed punches are crush
			return "crush";
		}

		int stab = eq.getAstab();
		int slash = eq.getAslash();
		int crush = eq.getAcrush();
		if (slash >= stab && slash >= crush)
		{
			return "slash";
		}
		if (stab >= crush)
		{
			return "stab";
		}
		return "crush";
	}

	private ItemEquipmentStats getEquipmentStats(Item item)
	{
		if (item == null || item.getId() == -1)
		{
			return null;
		}
		ItemStats stats = itemManager.getItemStats(item.getId());
		if (stats == null || !stats.isEquipable())
		{
			return null;
		}
		return stats.getEquipment();
	}
}
