package id.veyrion.ktp;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class VeyrionID extends JavaPlugin implements Listener, TabExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    // --- Konstanta HUD (harus sama dengan tools/generate_assets.py) ---
    private static final Key FONT_SPACE = Key.key("veyrion", "space");
    private static final Key FONT_BG = Key.key("veyrion", "card_bg");
    private static final String BG_CHAR = "\uE100";
    private static final int BG_ADVANCE = 225;      // lebar kartu 224 + 1
    private static final int VALUE_X = 57;          // kolom nilai dari kiri kartu
    private static final int CHAR_ADVANCE = 6;      // lebar tiap huruf (5 + 1)
    private static final TextColor INK = TextColor.color(0x3A2A10);
    private static final TextColor GOLD = TextColor.color(0xB07A1A);

    private NamespacedKey ownerKey;
    private File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, BukkitTask> hudTasks = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ownerKey = new NamespacedKey(this, "owner");
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        var cmd = getCommand("ki");
        if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        hudTasks.values().forEach(BukkitTask::cancel);
        hudTasks.clear();
    }

    // ---------- Helper izin ----------

    private boolean isAdmin(CommandSender s) {
        return s.isOp() || s.hasPermission("veyrion.id.admin");
    }

    private List<String> trusted() {
        return data.getStringList("trusted");
    }

    /** Hanya OP atau pemain yang di-/ki trust yang boleh mendaftarkan orang lain. */
    private boolean canRegister(CommandSender s) {
        if (isAdmin(s)) return true;
        return s instanceof Player p && trusted().contains(p.getUniqueId().toString());
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer findPlayer(String name) {
        Player on = Bukkit.getPlayerExact(name);
        if (on != null) return on;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null ? cached : Bukkit.getOfflinePlayer(name);
    }

    // ---------- Pesan & kartu (chat / lore) ----------

    private Component msg(String body) {
        return MM.deserialize(getConfig().getString("prefix", "") + "<white>" + body);
    }

    private List<Component> cardLines(UUID id) {
        String p = "players." + id + ".";
        TagResolver r = TagResolver.resolver(
                Placeholder.unparsed("nama", data.getString(p + "nama", "-")),
                Placeholder.unparsed("umur", data.getString(p + "umur", "-")),
                Placeholder.unparsed("lahir", data.getString(p + "lahir", "-")),
                Placeholder.unparsed("ras", data.getString(p + "ras", "-")));
        String[] t = {
                "<#7a6a45>══════════════════════",
                "<gradient:#b8892b:#f1d98a><bold>KARTU IDENTITAS VEYRION</bold></gradient>",
                "<#7a6a45>══════════════════════",
                "<#b8892b>Nama   <gray>: <white><nama>",
                "<#b8892b>Umur   <gray>: <white><umur>",
                "<#b8892b>Lahir  <gray>: <white><lahir>",
                "<#b8892b>Ras    <gray>: <#f1d98a><ras>",
                "<#7a6a45>══════════════════════"};
        List<Component> out = new ArrayList<>();
        for (String line : t) out.add(MM.deserialize(line, r));
        return out;
    }

    // ---------- HUD ItemsAdder (kartu di layar) ----------

    private boolean hudAvailable() {
        return getConfig().getBoolean("hud.enabled", true)
                && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    /** Geser kursor teks sebanyak px piksel (negatif = ke kiri) lewat font spasi. */
    private static Component shift(int px) {
        if (px == 0) return Component.empty();
        boolean neg = px < 0;
        int n = Math.abs(px);
        StringBuilder sb = new StringBuilder();
        for (int bit = 10; bit >= 0; bit--) {
            if ((n & (1 << bit)) != 0) sb.append((char) ((neg ? 0xE000 : 0xE010) + bit));
        }
        return Component.text(sb.toString()).font(FONT_SPACE);
    }

    private static String clean(String s, int max) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toUpperCase(Locale.ROOT).toCharArray()) {
            sb.append(ch >= 32 && ch < 127 ? ch : '?');
            if (sb.length() >= max) break;
        }
        return sb.toString();
    }

    private Component hudRow(int row, String text, TextColor color) {
        if (text.isEmpty()) return Component.empty();
        Key font = Key.key("veyrion", "row" + row);
        return shift(VALUE_X)
                .append(Component.text(text).font(font).color(color))
                .append(shift(-(VALUE_X + text.length() * CHAR_ADVANCE)));
    }

    private Component buildHud(UUID owner) {
        String p = "players." + owner + ".";
        int x = getConfig().getInt("hud.offset-x", -460);
        return Component.empty()
                .append(shift(x))
                .append(Component.text(BG_CHAR).font(FONT_BG))
                .append(shift(-BG_ADVANCE))
                .append(hudRow(1, clean(data.getString(p + "nama", "-"), 24), INK))
                .append(hudRow(2, clean(data.getString(p + "umur", "-"), 4), INK))
                .append(hudRow(3, clean(data.getString(p + "lahir", "-"), 10), INK))
                .append(hudRow(4, clean(data.getString(p + "ras", "-"), 16), GOLD))
                .append(shift(-x)); // total lebar = 0 -> posisi tidak bergeser
    }

    private void showCard(Player viewer, UUID owner) {
        if (!data.contains("players." + owner)) {
            viewer.sendMessage(msg("Kartu ini belum terdaftar."));
            return;
        }
        if (!hudAvailable()) {
            cardLines(owner).forEach(viewer::sendMessage);
            return;
        }
        hideHud(viewer, false);
        final Component hud = buildHud(owner);
        final int total = Math.max(1, getConfig().getInt("hud.duration-seconds", 20)) * 20;
        final int[] elapsed = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!viewer.isOnline() || elapsed[0] >= total) { hideHud(viewer, true); return; }
            viewer.sendActionBar(hud);
            elapsed[0] += 30;
        }, 0L, 30L);
        hudTasks.put(viewer.getUniqueId(), task);
    }

    private void hideHud(Player viewer, boolean clear) {
        BukkitTask t = hudTasks.remove(viewer.getUniqueId());
        if (t != null) {
            t.cancel();
            if (clear && viewer.isOnline()) viewer.sendActionBar(Component.empty());
        }
    }

    // ---------- Item kartu fisik ----------

    private ItemStack baseItem() {
        String id = getConfig().getString("item.itemsadder-id", "");
        if (!id.isBlank() && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            try {
                Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object stack = cs.getMethod("getInstance", String.class).invoke(null, id);
                if (stack != null) {
                    ItemStack it = (ItemStack) cs.getMethod("getItemStack").invoke(stack);
                    if (it != null) return it.clone();
                }
                getLogger().warning("Item ItemsAdder '" + id + "' tidak ditemukan, pakai kertas biasa.");
            } catch (ReflectiveOperationException | RuntimeException e) {
                getLogger().warning("Gagal memuat item ItemsAdder: " + e.getMessage());
            }
        }
        ItemStack paper = new ItemStack(Material.PAPER);
        int cmd = getConfig().getInt("item.custom-model-data", 0);
        if (cmd > 0) {
            ItemMeta m = paper.getItemMeta();
            m.setCustomModelData(cmd);
            paper.setItemMeta(m);
        }
        return paper;
    }

    private ItemStack makeCard(UUID owner) {
        ItemStack item = baseItem();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gradient:#b8892b:#f1d98a><bold>Kartu Identitas Veyrion</bold></gradient>")
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        String p = "players." + owner + ".";
        lore.add(MM.deserialize("<#b8892b>Nama: <white>" + data.getString(p + "nama", "-").replace("<", ""))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(MM.deserialize("<#b8892b>Ras: <#f1d98a>" + data.getString(p + "ras", "-").replace("<", ""))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(MM.deserialize("<gray>Klik kanan: lihat · Klik kanan pemain: tunjukkan")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        item.setItemMeta(meta);
        return item;
    }

    private void giveCard(Player p, UUID owner) {
        p.getInventory().addItem(makeCard(owner)).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }

    private UUID cardOwner(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private void save() {
        try { data.save(dataFile); } catch (IOException e) { getLogger().severe("Gagal menyimpan data: " + e.getMessage()); }
    }

    // ---------- Event ----------

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        UUID owner = cardOwner(e.getItem());
        if (owner == null) return;
        e.setCancelled(true);
        showCard(e.getPlayer(), owner);
    }

    @EventHandler
    public void onShow(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Player target)) return;
        UUID owner = cardOwner(e.getPlayer().getInventory().getItemInMainHand());
        if (owner == null) return;
        e.setCancelled(true);
        target.sendMessage(msg("<#f1d98a>" + e.getPlayer().getName() + "</#f1d98a> menunjukkan kartu identitasnya."));
        showCard(target, owner);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking()) hideHud(e.getPlayer(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hideHud(e.getPlayer(), false);
    }

    // ---------- Perintah ----------

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) { s.sendMessage(msg("/ki <daftar|lihat|tunjuk|kartu|trust|untrust|trustlist|hapus|reload>")); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "daftar" -> register(s, a);
            case "lihat" -> {
                if (!(s instanceof Player p)) { s.sendMessage(msg("Hanya untuk pemain.")); return true; }
                UUID target = p.getUniqueId();
                if (a.length > 1) {
                    if (!isAdmin(s)) { s.sendMessage(msg("Kamu tidak punya izin.")); return true; }
                    Player t = Bukkit.getPlayerExact(a[1]);
                    if (t == null) { s.sendMessage(msg("Pemain tidak ditemukan.")); return true; }
                    target = t.getUniqueId();
                }
                showCard(p, target);
            }
            case "tunjuk" -> {
                if (!(s instanceof Player p)) return true;
                if (!data.contains("players." + p.getUniqueId())) { p.sendMessage(msg("Kamu belum punya kartu.")); return true; }
                double r = getConfig().getDouble("show-radius", 6);
                p.sendMessage(msg("Kartu ditunjukkan ke pemain di sekitarmu."));
                for (Player o : p.getWorld().getPlayers()) {
                    if (o.equals(p) || o.getLocation().distance(p.getLocation()) > r) continue;
                    o.sendMessage(msg("<#f1d98a>" + p.getName() + "</#f1d98a> menunjukkan kartu identitasnya."));
                    showCard(o, p.getUniqueId());
                }
            }
            case "kartu" -> {
                // /ki kartu <pemain> : beri ulang kartu fisik (hanya OP / trusted)
                if (!canRegister(s)) { s.sendMessage(msg("Hanya OP atau pemain yang dipercaya yang bisa memberi kartu.")); return true; }
                if (a.length < 2) { s.sendMessage(msg("/ki kartu <pemain>")); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage(msg("Pemain harus online.")); return true; }
                if (!data.contains("players." + t.getUniqueId())) { s.sendMessage(msg("Pemain itu belum terdaftar.")); return true; }
                giveCard(t, t.getUniqueId());
                s.sendMessage(msg("Kartu " + t.getName() + " diberikan ulang."));
            }
            case "trust", "untrust" -> trustCmd(s, a);
            case "trustlist" -> {
                if (!isAdmin(s)) { s.sendMessage(msg("Kamu tidak punya izin.")); return true; }
                List<String> names = trusted().stream().map(u -> {
                    String n = Bukkit.getOfflinePlayer(UUID.fromString(u)).getName();
                    return n == null ? u : n;
                }).collect(Collectors.toList());
                s.sendMessage(msg("Dipercaya: " + (names.isEmpty() ? "-" : String.join(", ", names))));
            }
            case "hapus" -> {
                if (!isAdmin(s)) { s.sendMessage(msg("Kamu tidak punya izin.")); return true; }
                if (a.length < 2) { s.sendMessage(msg("/ki hapus <pemain>")); return true; }
                data.set("players." + findPlayer(a[1]).getUniqueId(), null);
                save();
                s.sendMessage(msg("Data identitas " + a[1] + " dihapus."));
            }
            case "reload" -> {
                if (!isAdmin(s)) { s.sendMessage(msg("Kamu tidak punya izin.")); return true; }
                reloadConfig();
                data = YamlConfiguration.loadConfiguration(dataFile);
                s.sendMessage(msg("Konfigurasi dimuat ulang."));
            }
            default -> s.sendMessage(msg("Perintah tidak dikenal."));
        }
        return true;
    }

    private void trustCmd(CommandSender s, String[] a) {
        if (!isAdmin(s)) { s.sendMessage(msg("Hanya OP yang bisa mengatur trust.")); return; }
        boolean add = a[0].equalsIgnoreCase("trust");
        if (a.length < 2) { s.sendMessage(msg("/ki " + a[0].toLowerCase(Locale.ROOT) + " <pemain>")); return; }
        OfflinePlayer t = findPlayer(a[1]);
        if (!t.isOnline() && !t.hasPlayedBefore()) { s.sendMessage(msg("Pemain itu belum pernah join.")); return; }
        String id = t.getUniqueId().toString();
        List<String> list = new ArrayList<>(trusted());
        if (add) {
            if (list.contains(id)) { s.sendMessage(msg(a[1] + " sudah dipercaya.")); return; }
            list.add(id);
        } else if (!list.remove(id)) {
            s.sendMessage(msg(a[1] + " tidak ada di daftar trust."));
            return;
        }
        data.set("trusted", list);
        save();
        s.sendMessage(msg(add ? "<#f1d98a>" + a[1] + "</#f1d98a> sekarang boleh mendaftarkan penduduk."
                              : "<#f1d98a>" + a[1] + "</#f1d98a> tidak lagi boleh mendaftarkan penduduk."));
        if (t.isOnline() && t.getPlayer() != null) {
            t.getPlayer().sendMessage(msg(add ? "Kamu diberi izin mendaftarkan penduduk: <#f1d98a>/ki daftar</#f1d98a>."
                                              : "Izin mendaftarkan penduduk kamu dicabut."));
        }
    }

    /** /ki daftar <pemain> <nama> <umur> <DD/MM/YYYY> <ras> */
    private void register(CommandSender s, String[] a) {
        if (!canRegister(s)) {
            s.sendMessage(msg("Hanya OP atau pemain yang dipercaya (/ki trust) yang bisa mendaftarkan penduduk."));
            return;
        }
        if (a.length < 6) {
            s.sendMessage(msg("/ki daftar <pemain> <nama> <umur> <DD/MM/YYYY> <ras>"));
            s.sendMessage(msg("<gray>Pakai _ sebagai spasi pada nama. Contoh: /ki daftar Steve Kynaro_Karamazov 17 11/01/2009 Elf"));
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { s.sendMessage(msg("Pemain harus online untuk menerima kartu.")); return; }
        String key = "players." + t.getUniqueId();
        if (data.contains(key) && !isAdmin(s)) {
            s.sendMessage(msg("Pemain itu sudah terdaftar. Hanya OP yang bisa menimpa data."));
            return;
        }
        String nama = a[2].replace('_', ' ');
        if (nama.length() < 2 || nama.length() > 32) { s.sendMessage(msg("Nama harus 2–32 karakter.")); return; }
        int umur;
        try { umur = Integer.parseInt(a[3]); } catch (NumberFormatException e) { umur = -1; }
        if (umur < 1 || umur > 9999) { s.sendMessage(msg("Umur harus angka 1–9999.")); return; }
        try { LocalDate.parse(a[4], DATE); } catch (Exception e) { s.sendMessage(msg("Format tanggal lahir: DD/MM/YYYY.")); return; }
        String ras = getConfig().getStringList("races").stream()
                .filter(x -> x.equalsIgnoreCase(a[5])).findFirst().orElse(null);
        if (ras == null) {
            s.sendMessage(msg("Ras tidak valid. Pilihan: " + String.join(", ", getConfig().getStringList("races"))));
            return;
        }
        data.set(key + ".nama", nama);
        data.set(key + ".umur", umur);
        data.set(key + ".lahir", a[4]);
        data.set(key + ".ras", ras);
        data.set(key + ".terbit", LocalDate.now().format(DATE));
        data.set(key + ".didaftarkan-oleh", s.getName());
        save();
        giveCard(t, t.getUniqueId());
        t.sendMessage(msg("Kamu resmi tercatat sebagai penduduk Veyrion."));
        s.sendMessage(msg("<#f1d98a>" + t.getName() + "</#f1d98a> berhasil didaftarkan sebagai <white>" + nama.replace("<", "") + "</white>."));
        showCard(t, t.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> opts = new ArrayList<>();
        boolean reg = canRegister(s);
        if (a.length == 1) {
            opts.addAll(List.of("lihat", "tunjuk"));
            if (reg) opts.addAll(List.of("daftar", "kartu"));
            if (isAdmin(s)) opts.addAll(List.of("trust", "untrust", "trustlist", "hapus", "reload"));
        } else if (a.length == 2 && List.of("daftar", "kartu", "trust", "hapus", "lihat").contains(a[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("untrust")) {
            trusted().forEach(u -> { String n = Bukkit.getOfflinePlayer(UUID.fromString(u)).getName(); if (n != null) opts.add(n); });
        } else if (a.length == 6 && a[0].equalsIgnoreCase("daftar")) {
            opts.addAll(getConfig().getStringList("races"));
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return opts.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }
}
