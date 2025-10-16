package com.markmode.economyshop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
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

    public ShopManager(EconomyShopPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** 카테고리 GUI 열기 */
    public void openCategory(Player p, int index) {
        FileConfiguration cfg = plugin.getConfig();

        // categories 키 목록 (정렬 유지)
        Set<String> keySet = Objects.requireNonNull(cfg.getConfigurationSection("categories"), "config.yml의 categories가 없음").getKeys(false);
        List<String> cats = new ArrayList<>(keySet);
        if (cats.isEmpty()) { p.sendMessage("상점 구성이 비어 있습니다."); return; }

        if (index < 0) index = cats.size() - 1;
        if (index >= cats.size()) index = 0;

        String cat = cats.get(index);

        // GUI 생성
        Inventory inv = Bukkit.createInventory(p, 6 * 9,
                "상점 - " + cat + " §7(잔액: " + plugin.getBalance(p.getUniqueId()) + ")");

        // 항목 로드 (map list)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) (List<?>) cfg.getList("categories." + cat);
        if (list != null) {
            int slot = 0;
            for (Map<String, Object> m : list) {
                String item = String.valueOf(m.get("item"));
                int amount = ((Number) m.getOrDefault("amount", 1)).intValue();
                long buy = ((Number) m.getOrDefault("buy", 0)).longValue();
                long sell = ((Number) m.getOrDefault("sell", 0)).longValue();

                Material mat = Material.matchMaterial(item);
                if (mat == null) continue;

                ItemStack it = new ItemStack(mat, Math.max(1, amount));
                ItemMeta meta = it.getItemMeta();
                meta.setDisplayName(ChatColor.YELLOW + item);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "좌클릭: 구매 " + buy);
                lore.add(ChatColor.GRAY + "우클릭: 판매 " + sell);
                meta.setLore(lore);
                it.setItemMeta(meta);

                inv.setItem(slot++, it);
                if (slot >= inv.getSize() - 9) break; // 마지막 줄은 네비게이션용
            }
        }

        // 네비게이션
        inv.setItem(inv.getSize() - 9 + 3, navItem(Material.ARROW, "◀ 이전"));
        inv.setItem(inv.getSize() - 9 + 5, navItem(Material.ARROW, "다음 ▶"));

        p.openInventory(inv);
    }

    private ItemStack navItem(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(ChatColor.AQUA + name);
        it.setItemMeta(im);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (title == null || !title.startsWith("상점 - ")) return;

        e.setCancelled(true); // GUI 보호

        // 현재 카테고리 이름 추출
        String cat = title.substring("상점 - ".length());
        int idx = cat.indexOf(" §7(");
        if (idx > 0) cat = cat.substring(0, idx);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        String disp = clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()
                ? ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                : "";

        // 네비게이션 처리
        List<String> cats = new ArrayList<>(Objects.requireNonNull(plugin.getConfig().getConfigurationSection("categories")).getKeys(false));
        int catIndex = Math.max(0, cats.indexOf(cat));
        if (disp.equals("◀ 이전")) { Bukkit.getScheduler().runTask(plugin, () -> openCategory(p, (catIndex - 1 + cats.size()) % cats.size())); return; }
        if (disp.equals("다음 ▶")) { Bukkit.getScheduler().runTask(plugin, () -> openCategory(p, (catIndex + 1) % cats.size())); return; }

        // 해당 카테고리의 상품 찾기
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) (List<?>) plugin.getConfig().getList("categories." + cat);
        if (list == null) return;

        Map<String, Object> offer = null;
        for (Map<String, Object> m : list) {
            String item = String.valueOf(m.get("item"));
            Material mat = Material.matchMaterial(item);
            if (mat != null && mat == clicked.getType()) { offer = m; break; }
        }
        if (offer == null) return;

        int amount = ((Number) offer.getOrDefault("amount", 1)).intValue();
        long buy = ((Number) offer.getOrDefault("buy", 0)).longValue();
        long sell = ((Number) offer.getOrDefault("sell", 0)).longValue();

        switch (e.getClick()) {
            case LEFT -> { // 구매
                if (buy <= 0) { p.sendMessage("이 항목은 구매할 수 없습니다."); return; }
                if (plugin.getBalance(p.getUniqueId()) < buy) { p.sendMessage("잔액이 부족합니다."); return; }
                ItemStack give = new ItemStack(clicked.getType(), Math.max(1, amount));
                Map<Integer, ItemStack> left = p.getInventory().addItem(give);
                if (left.isEmpty()) {
                    plugin.take(p.getUniqueId(), buy);
                    p.sendMessage("구매 완료: " + clicked.getType().name() + " x" + amount + " (-" + buy + ")");
                } else p.sendMessage("인벤토리가 가득 찼습니다.");
            }
            case RIGHT -> { // 판매
                if (sell <= 0) { p.sendMessage("이 항목은 판매할 수 없습니다."); return; }
                int need = Math.max(1, amount);
                int removed = 0;
                ItemStack[] contents = p.getInventory().getContents();
                for (int i = 0; i < contents.length && removed < need; i++) {
                    ItemStack st = contents[i];
                    if (st == null || st.getType().isAir()) continue;
                    if (st.getType() == clicked.getType()) {
                        int take = Math.min(need - removed, st.getAmount());
                        st.setAmount(st.getAmount() - take);
                        contents[i] = st.getAmount() > 0 ? st : null;
                        removed += take;
                    }
                }
                p.getInventory().setContents(contents);
                if (removed < need) { p.sendMessage(clicked.getType().name() + " x" + need + " 이(가) 필요합니다."); return; }
                plugin.add(p.getUniqueId(), sell);
                p.sendMessage("판매 완료: +" + sell);
            }
            default -> { /* 다른 클릭 타입 무시 */ }
        }

        // 갱신된 잔액 반영해서 GUI 다시 열기
        Bukkit.getScheduler().runTask(plugin, () -> openCategory(p, catIndex));
    }
}
