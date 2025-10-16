package com.markmode.economyshop;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class EconomyShopPlugin extends JavaPlugin implements Listener {

    private File balancesFile;
    private FileConfiguration balances;
    private NamespacedKey npcKey;
    private ShopManager shopManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.balancesFile = new File(getDataFolder(), "balances.yml");
        if(!balancesFile.exists()) {
            balancesFile.getParentFile().mkdirs();
            try { balancesFile.createNewFile(); } catch(IOException ignored) {}
        }
        this.balances = YamlConfiguration.loadConfiguration(balancesFile);
        this.npcKey = new NamespacedKey(this, "shopnpc");
        this.shopManager = new ShopManager(this);

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("EconomyShopJava enabled.");
    }

    @Override
    public void onDisable() {
        saveBalances();
    }

    public void saveBalances() {
        try { balances.save(balancesFile); } catch(IOException ignored) {}
    }

    public long getBalance(UUID uuid) {
        return balances.getLong(uuid.toString(), 0L);
    }
    public void setBalance(UUID uuid, long amount) {
        balances.set(uuid.toString(), Math.max(0L, amount));
        saveBalances();
    }
    public void add(UUID uuid, long delta) {
        setBalance(uuid, getBalance(uuid) + delta);
    }
    public boolean take(UUID uuid, long amount) {
        long cur = getBalance(uuid);
        if(cur < amount) return false;
        setBalance(uuid, cur - amount);
        return true;
    }

    // --- Commands ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "bal":
            case "balance":
                if(!(sender instanceof Player p)) { sender.sendMessage("In-game only."); return true; }
                ensureStartMoney(p);
                sender.sendMessage(ChatColor.YELLOW + "잔액: " + ChatColor.GREEN + getBalance(p.getUniqueId()));
                return true;

            case "pay":
                if(!(sender instanceof Player from)) { sender.sendMessage("In-game only."); return true; }
                if(args.length < 2){ sender.sendMessage("/pay <player> <amount>"); return true; }
                Player to = Bukkit.getPlayerExact(args[0]);
                long amt;
                try { amt = Long.parseLong(args[1]); } catch(Exception e){ sender.sendMessage("금액은 숫자여야 합니다."); return true; }
                if(to == null){ sender.sendMessage("플레이어를 찾을 수 없습니다."); return true; }
                if(amt <= 0){ sender.sendMessage("금액은 1 이상이어야 합니다."); return true; }
                if(!take(from.getUniqueId(), amt)){ sender.sendMessage("잔액이 부족합니다."); return true; }
                add(to.getUniqueId(), amt);
                from.sendMessage("송금 완료: " + to.getName() + "에게 " + amt);
                to.sendMessage(from.getName() + "로부터 " + amt + " 받았습니다.");
                return true;

            case "eco":
                if(!sender.hasPermission("econshop.eco")){ sender.sendMessage("권한이 없습니다."); return true; }
                if(args.length < 3){ sender.sendMessage("/eco set|add|take <player> <amount>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if(target == null){ sender.sendMessage("플레이어가 접속 중이어야 합니다."); return true; }
                long v;
                try { v = Long.parseLong(args[2]); } catch(Exception e){ sender.sendMessage("금액은 숫자여야 합니다."); return true; }
                switch (args[0].toLowerCase()) {
                    case "set" -> setBalance(target.getUniqueId(), Math.max(0L, v));
                    case "add" -> add(target.getUniqueId(), Math.max(0L, v));
                    case "take" -> take(target.getUniqueId(), Math.max(0L, v));
                    default -> { sender.sendMessage("set|add|take 중 선택"); return true; }
                }
                sender.sendMessage("처리 완료. " + target.getName() + " 잔액: " + getBalance(target.getUniqueId()));
                return true;

            case "shop":
                if(!(sender instanceof Player sp)) { sender.sendMessage("In-game only."); return true; }
                ensureStartMoney(sp);
                shopManager.openCategory(sp, 0);
                return true;

            case "shopnpc":
                if(!(sender instanceof Player pp)) { sender.sendMessage("In-game only."); return true; }
                if(!pp.hasPermission("econshop.shopnpc")){ pp.sendMessage("권한이 없습니다."); return true; }
                String sub = args.length > 0 ? args[0].toLowerCase() : "spawn";
                if(sub.equals("spawn")){
                    Villager vlg = pp.getWorld().spawn(pp.getLocation(), Villager.class, v -> {
                        v.setProfession(Villager.Profession.NONE);
                        v.setCustomName(ChatColor.GREEN + "상점 (우클릭)");
                        v.setCustomNameVisible(true);
                        v.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte)1);
                    });
                    pp.sendMessage("상점 NPC 소환 완료.");
                } else if(sub.equals("remove")){
                    Entity nearest = null; double best = 6.0;
                    for(Entity e : pp.getNearbyEntities(6, 6, 6)){
                        if(e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE)){
                            double d = e.getLocation().distance(pp.getLocation());
                            if(d <= best){ best = d; nearest = e; }
                        }
                    }
                    if(nearest != null){ nearest.remove(); pp.sendMessage("가까운 상점 NPC 제거."); }
                    else pp.sendMessage("근처에 상점 NPC가 없습니다.");
                }
                return true;
        }
        return false;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e){
        Entity ent = e.getRightClicked();
        if(ent.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE)){
            e.setCancelled(true);
            shopManager.openCategory(e.getPlayer(), 0);
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Player p)) return;
        if(e.getEntity().getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE)){
            e.setCancelled(true);
            shopManager.openCategory(p, 0);
        }
    }

    private void ensureStartMoney(Player p){
        if(!p.hasPlayedBefore() && getBalance(p.getUniqueId()) == 0L){
            long start = getConfig().getLong("start-money", 100L);
            add(p.getUniqueId(), start);
        }
    }
}
