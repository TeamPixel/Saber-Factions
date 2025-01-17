package com.massivecraft.factions.util;

import com.massivecraft.factions.Conf;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.SaberFactions;
import com.massivecraft.factions.integration.Econ;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Level;

public class WarpGUI implements InventoryHolder, FactionGUI {

	private final ConfigurationSection section;
	int guiSize;
	private Inventory warpGUI;
	private FPlayer fme;
	private HashMap<Integer, String> warpSlots = new HashMap<>();
	private int maxWarps;
	private List<Integer> dummySlots = new ArrayList<>();

	public WarpGUI(FPlayer fme) {
		this.fme = fme;
		this.section = SaberFactions.plugin.getConfig().getConfigurationSection("fwarp-gui");
	}

	@Override
	public void build() {
		if (section == null) {
			SaberFactions.plugin.log(Level.WARNING, "Attempted to build f warp GUI but config section not present.");
			SaberFactions.plugin.log(Level.WARNING, "Copy your config, allow the section to generate, then copy it back to your old config.");
			return;
		}

		// Build basic Inventory info
		guiSize = section.getInt("rows", 3);
		if (guiSize > 6) {
			guiSize = 6;
			SaberFactions.plugin.log(Level.INFO, "Warp GUI size out of bounds, defaulting to 6");
		}

		guiSize *= 9;
		String guiName = ChatColor.translateAlternateColorCodes('&', section.getString("name", "FactionPermissions"));
		warpGUI = Bukkit.createInventory(this, guiSize, guiName);

		maxWarps = SaberFactions.plugin.getConfig().getInt("max-warps", 5);

		Set<String> factionWarps = fme.getFaction().getWarps().keySet();
		List<Integer> warpOpenSlots = section.getIntegerList("warp-slots");

		buildDummyItems();

		if (maxWarps != warpOpenSlots.size()) {
			SaberFactions.plugin.log(Level.SEVERE, "Invalid warp slots for GUI, Please use same value as max warps");
			return;
		}

		int warpSlotIndex = 0;
		for (String warp : factionWarps) {
			warpSlots.put(warpOpenSlots.get(warpSlotIndex), warp);
			warpSlotIndex++;
		}

		buildItems();
	}

	@Override
	public Inventory getInventory() {
		return warpGUI;
	}

	private void buildItems() {
		for (Map.Entry<Integer, String> entry : warpSlots.entrySet()) {
			warpGUI.setItem(entry.getKey(), buildItem(entry.getValue()));
		}
	}

	@Override
	public void onClick(int slot, ClickType action) {
		if (warpSlots.containsKey(slot)) {
			fme.getPlayer().closeInventory();

			// All clear lets TP them or ask for password
			String warp = warpSlots.get(slot);
			if (!fme.getFaction().hasWarpPassword(warp)) {
				if (transact(fme)) {
					doWarmup(warp);
				}
			} else {
				fme.setEnteringPassword(true, warp);
				fme.msg(TL.COMMAND_FWARP_PASSWORD_REQUIRED);
				Bukkit.getScheduler().runTaskLater(SaberFactions.plugin, () -> {
					if (fme.isEnteringPassword()) {
						fme.msg(TL.COMMAND_FWARP_PASSWORD_TIMEOUT);
						fme.setEnteringPassword(false, "");
					}
				}, SaberFactions.plugin.getConfig().getInt("fwarp-gui.password-timeout", 5) * 20);
			}
		}
	}

	private void doWarmup(final String warp) {
		WarmUpUtil.process(fme, WarmUpUtil.Warmup.WARP, TL.WARMUPS_NOTIFY_TELEPORT, warp, () -> {
			Player player = Bukkit.getPlayer(fme.getPlayer().getUniqueId());
			if (player != null) {
				player.teleport(fme.getFaction().getWarp(warp).getLocation());
				fme.msg(TL.COMMAND_FWARP_WARPED, warp);
			}
		}, SaberFactions.plugin.getConfig().getLong("warmups.f-warp", 0));
	}

	private boolean transact(FPlayer player) {
		if (!SaberFactions.plugin.getConfig().getBoolean("warp-cost.enabled", false) || player.isAdminBypassing()) {
			return true;
		}

		double cost = SaberFactions.plugin.getConfig().getDouble("warp-cost.warp", 5);

		if (!Econ.shouldBeUsed() || this.fme == null || cost == 0.0 || fme.isAdminBypassing()) {
			return true;
		}

		if (Conf.bankEnabled && Conf.bankFactionPaysCosts && fme.hasFaction()) {
			return Econ.modifyMoney(fme.getFaction(), -cost, TL.COMMAND_FWARP_TOWARP.toString(), TL.COMMAND_FWARP_FORWARPING.toString());
		} else {
			return Econ.modifyMoney(fme, -cost, TL.COMMAND_FWARP_TOWARP.toString(), TL.COMMAND_FWARP_FORWARPING.toString());
		}
	}

	private ItemStack buildItem(String warp) {
		ConfigurationSection warpItemSection = section.getConfigurationSection("warp-item");
		if (warpItemSection == null) {
			SaberFactions.plugin.log(Level.WARNING, "Attempted to build f warp GUI but config section not present.");
			SaberFactions.plugin.log(Level.WARNING, "Copy your config, allow the section to generate, then copy it back to your old config.");
			return new ItemStack(Material.AIR);
		}

		String displayName = replacePlaceholers(warpItemSection.getString("name"), warp, fme.getFaction());
		List<String> lore = new ArrayList<>();

		if (warpItemSection.getString("material") == null) {
			return null;
		}
		Material material = Material.matchMaterial(warpItemSection.getString("material"));
		if (material == null) {
			material = Material.STONE;
		}

		ItemStack item = new ItemStack(material);
		ItemMeta itemMeta = item.getItemMeta();

		for (String loreLine : warpItemSection.getStringList("lore")) {
			lore.add(replacePlaceholers(loreLine, warp, fme.getFaction()));
		}

		itemMeta.setDisplayName(displayName);
		itemMeta.setLore(lore);
		item.setItemMeta(itemMeta);

		return item;
	}

	private String replacePlaceholers(String string, String warp, Faction faction) {
		string = ChatColor.translateAlternateColorCodes('&', string);
		string = string.replace("{warp}", warp);
		string = string.replace("{warp-protected}", faction.hasWarpPassword(warp) ? "Enabled" : "Disabled");
		string = string.replace("{warp-cost}", !SaberFactions.plugin.getConfig().getBoolean("warp-cost.enabled", false) ? "Disabled" : Integer.toString(SaberFactions.plugin.getConfig().getInt("warp-cost.warp", 5)));
		return string;
	}

	private void buildDummyItems() {
		for (String key : section.getConfigurationSection("dummy-slots").getKeys(false)) {
			int dummyId;
			try {
				dummyId = Integer.parseInt(key);
			} catch (NumberFormatException exception) {
				SaberFactions.plugin.log(Level.WARNING, "Invalid dummy item id: " + key.toUpperCase());
				continue;
			}

			ItemStack dummyItem = buildDummyItem(dummyId);
			if (dummyItem == null) {
				continue;
			}

			List<Integer> dummyIdSlots = section.getIntegerList("dummy-slots." + key);
			for (Integer slot : dummyIdSlots) {
				if (slot + 1 > guiSize || slot < 0) {
					SaberFactions.plugin.log(Level.WARNING, "Invalid slot: " + slot + " for dummy item: " + key);
					continue;
				}
				dummySlots.add(slot);
				warpGUI.setItem(slot, dummyItem);
			}
		}
	}

	private ItemStack buildDummyItem(int id) {
		final ConfigurationSection dummySection = section.getConfigurationSection("dummy-items." + id);

		if (dummySection == null) {
			SaberFactions.plugin.log(Level.WARNING, "Attempted to build f warp GUI but config section not present.");
			SaberFactions.plugin.log(Level.WARNING, "Copy your config, allow the section to generate, then copy it back to your old config.");
			return new ItemStack(Material.AIR);
		}

		Material material = Material.matchMaterial(dummySection.getString("material", ""));
		if (material == null) {
			SaberFactions.plugin.log(Level.WARNING, "Invalid material for dummy item: " + id);
			return null;
		}

		ItemStack itemStack = new ItemStack(material);

		DyeColor color;
		try {
			color = DyeColor.valueOf(dummySection.getString("color", ""));
		} catch (Exception exception) {
			color = null;
		}
		if (color != null) {
			itemStack.setDurability(color.getWoolData());
		}

		ItemMeta itemMeta = itemStack.getItemMeta();

		if (!SaberFactions.plugin.mc17) {
			itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
		}


		itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', dummySection.getString("name", " ")));

		List<String> lore = new ArrayList<>();
		for (String loreLine : dummySection.getStringList("lore")) {
			lore.add(ChatColor.translateAlternateColorCodes('&', loreLine));
		}
		itemMeta.setLore(lore);

		itemStack.setItemMeta(itemMeta);

		return itemStack;
	}

}