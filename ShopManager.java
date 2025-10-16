package com.markmode.economyshop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ShopManager implements Listener {
    private final EconomyShopPlugin plugin;

    public ShopManager(EconomyShopPlugin plugin){
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openCategory(Player p, int index){
        List<String> cats = new ArrayList<>(Objects.requireNonNull(plugin.getConfig().getConfigurationSection("categories")).getKeys(false));
        if(cats.isEmpty()){ p.sendMessage("상점 구성이 비어 있습니다."); return; }
        if(index < 0) index = cats.size()-1; if(index >= cats.size()) index = 0;
        String cat = cats.get(index);
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("categories."+cat);
        Inventory inv = Bukkit.createInventory(p, 6*9, "상점 - " + cat + " §7(잔액: " + plugin.getBalance(p.getUniqueId()) + ")");
        int slot = 0;
        for(String key : Objects.requireNonNull(sec).getKeys(false)){
            // Actually it's a list; iterate by index
        }
        // The section is a list, so handle list items
        List<Map<?,?>> list = sec.getMapList(".");
        if(list.isEmpty()){
            // Spigot can't access '.'; fallback to direct from config
            list = (List<Map<?,?>>)plugin.getConfig().getList("categories."+cat);
        }
        if(list != null){
            for(Map<?,?> m : list){
                String item = String.valueOf(m.get("item"));
                int amount = ((Number)m.getOrDefault("amount", 1)).intValue();
                long buy = ((Number)m.getOrDefault("buy", 0)).longValue();
                long sell = ((Number)m.getOrDefault("sell", 0)).longValue();
                Material mat = Material.matchMaterial(item);
                if(mat == null) continue;
                ItemStack it = new ItemStack(mat, Math.max(1, amount));
                ItemMeta meta = it.getItemMeta();
                meta.setDisplayName(ChatColor.YELLOW + item);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "좌클릭: 구매 " + buy);
                lore.add(ChatColor.GRAY + "우클릭: 판매 " + sell);
                meta.setLore(lore);
                it.setItemMeta(meta);
                inv.setItem(slot++, it);
                if(slot >= inv.getSize()-9) break; // leave last row for nav
            }
        }
        // nav row
        inv.setItem(inv.getSize()-9 + 3, navItem(Material.ARROW, "◀ 이전"));
        inv.setItem(inv.getSize()-9 + 5, navItem(Material.ARROW, "다음 ▶"));
        p.openInventory(inv);
        // store index via metadata could be added; we will deduce by title
    }

    private ItemStack navItem(Material m, String name){
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(ChatColor.AQUA + name);
        it.setItemMeta(im);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)) return;
        if(e.getView().getTitle() == null) return;
        if(!e.getView().getTitle().startsWith("상점 - ")) return;
        e.setCancelled(true);
        String title = e.getView().getTitle();
        // extract category name within title "상점 - <cat> §7(잔액: ..."
        String cat = title.substring("상점 - ".length());
        int idx = cat.indexOf(" §7(");
        if(idx > 0) cat = cat.substring(0, idx);

        // handle nav
        ItemStack clicked = e.getCurrentItem();
        if(clicked == null) return;
        String name = clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName() ? ChatColor.stripColor(clicked.getItemMeta().getDisplayName()) : "";
        List<String> cats = new ArrayList<>(Objects.requireNonNull(plugin.getConfig().getConfigurationSection("categories")).getKeys(false));
        int catIndex = Math.max(0, cats.indexOf(cat));

        if(name.equals("◀ 이전")){ plugin.getServer().getScheduler().runTask(plugin, () -> openCategory(p, (catIndex-1+cats.size())%cats.size())); return; }
        if(name.equals("다음 ▶")){ plugin.getServer().getScheduler().runTask(plugin, () -> openCategory(p, (catIndex+1)%cats.size())); return; }

        // purchase/sell
        if(clicked.getType().isAir()) return;
        // find offer by material
        List<Map<?,?>> list = (List<Map<?,?>>)plugin.getConfig().getList("categories."+cat);
        if(list == null) return;
        Map<?,?> offer = null;
        for(Map<?,?> m : list){
            if(String.valueOf(m.get("item")).equalsIgnoreCase(clicked.getType().name())){
                offer = m; break;
            }
        }
        if(offer == null) return;
        int amount = ((Number)offer.getOrDefault("amount", 1)).intValue();
        long buy = ((Number)offer.getOrDefault("buy", 0)).longValue();
        long sell = ((Number)offer.getOrDefault("sell", 0)).longValue();

        // left = buy, right = sell
        switch (e.getClick()) {
            case LEFT -> {
                if(buy <= 0){ p.sendMessage("이 항목은 구매할 수 없습니다."); return; }
                if(plugin.getBalance(p.getUniqueId()) < buy){ p.sendMessage("잔액이 부족합니다."); return; }
                ItemStack give = new ItemStack(clicked.getType(), Math.max(1, amount));
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(give);
                if(left.isEmpty()){
                    plugin.take(p.getUniqueId(), buy);
                    p.sendMessage("구매 완료: " + clicked.getType().name() + " x" + amount + " (-" + buy + ")");
                } else {
                    p.sendMessage("인벤토리가 가득 찼습니다.");
                }
            }
            case RIGHT -> {
                if(sell <= 0){ p.sendMessage("이 항목은 판매할 수 없습니다."); return; }
                // remove 'amount' from inventory
                int need = Math.max(1, amount);
                int removed = 0;
                ItemStack[] contents = p.getInventory().getContents();
                for(int i=0;i<contents.length && removed<need;i++){
                    ItemStack st = contents[i];
                    if(st == null || st.getType().isAir()) continue;
                    if(st.getType() == clicked.getType()){
                        int take = Math.min(need-removed, st.getAmount());
                        st.setAmount(st.getAmount()-take);
                        contents[i] = st.getAmount() > 0 ? st : null;
                        removed += take;
                    }
                }
                p.getInventory().setContents(contents);
                if(removed < need){ p.sendMessage(clicked.getType().name()+" x"+need+" 이(가) 필요합니다."); return; }
                plugin.add(p.getUniqueId(), sell);
                p.sendMessage("판매 완료: +" + sell);
            }
            default -> {}
        }
        // refresh title balance
        plugin.getServer().getScheduler().runTask(plugin, () -> openCategory(p, catIndex));
    }
}
