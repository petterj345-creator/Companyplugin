package dk.companies.gui;

import dk.companies.Companies;
import dk.companies.model.JobTemplate;
import dk.companies.model.LicenseType;
import dk.companies.util.FormatUtil;
import dk.companies.util.ItemBuilder;
import dk.companies.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Top-level builder: name, icon material, price, lifetime + button to open the jobs list.
 */
public class AdminBuilderMenu extends Menu {

    public static class JobDraft {
        public Material material;
        public int baseAmount = 100;
        public double baseReward = 1000.0;
        public long jobDurationMillis = 60L * 60_000L; // 1h
    }

    public static class Draft {
        public UUID editingId;
        public String name = "Unnamed License";
        public Material iconMaterial;                 // icon shown in the shop
        public double price = 5000.0;
        public long validDurationMillis = 7L * 24 * 60 * 60_000L; // 7d
        public final List<JobDraft> jobs = new ArrayList<>();
    }

    private final Companies plugin;
    private final Draft draft;

    public AdminBuilderMenu(Companies plugin, Player viewer, Draft draft) {
        super(viewer);
        this.plugin = plugin;
        this.draft = draft;
    }

    @Override public boolean cancelByDefault() { return false; }

    @Override public void open() {
        inv = Bukkit.createInventory(null, 45, MessageUtil.color("&8License Builder"));

        inv.setItem(4, ItemBuilder.of(Material.NAME_TAG)
                .name("&eName: &f" + draft.name)
                .lore("&8Click to rename in chat.").build());

        inv.setItem(11, ItemBuilder.of(draft.iconMaterial == null ? Material.BARRIER : draft.iconMaterial)
                .name(draft.iconMaterial == null ? "&cIcon material: not set" : "&aIcon material: &f" + draft.iconMaterial.name())
                .lore("&7Drag an item from your inventory",
                        "&7onto this slot to set the icon.").build());

        inv.setItem(13, ItemBuilder.of(Material.GOLD_INGOT)
                .name("&ePrice (cost to buy): &6" + FormatUtil.money(draft.price))
                .lore("&8Click to set in chat.").build());

        inv.setItem(15, ItemBuilder.of(Material.ENDER_PEARL)
                .name("&eLicense lifetime: &f" + FormatUtil.duration(draft.validDurationMillis))
                .lore("&8Click to set in chat (e.g. '7d', '24h').").build());

        inv.setItem(22, ItemBuilder.of(Material.WRITABLE_BOOK)
                .name("&bJob rotation: &f" + draft.jobs.size() + " job(s)")
                .lore("&7Add/edit the jobs this license cycles between.",
                        "&8Click to manage.")
                .glow(!draft.jobs.isEmpty())
                .build());

        // Save / cancel / back
        inv.setItem(40, ItemBuilder.of(Material.EMERALD_BLOCK)
                .name("&a&lSave")
                .lore(draft.iconMaterial == null ? "&cIcon material not set!"
                        : draft.jobs.isEmpty() ? "&cAdd at least one job!"
                        : "&8Click to save.").build());
        inv.setItem(36, ItemBuilder.of(Material.ARROW).name("&7Back").build());

        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.openInventory(inv);
            GuiListener.register(viewer, this);
        });
    }

    @Override public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();

        // Icon slot: take type from cursor/click without consuming.
        if (raw == 11) {
            e.setCancelled(true);
            ItemStack cursor = e.getCursor();
            if (cursor != null && !cursor.getType().isAir() && cursor.getType() != Material.BARRIER) {
                draft.iconMaterial = cursor.getType();
                open();
            }
            return;
        }

        // Bottom-inventory shift-click: pick that material.
        if (raw >= inv.getSize()) {
            if (e.getClick().isShiftClick()) {
                e.setCancelled(true);
                ItemStack clicked = e.getCurrentItem();
                if (clicked != null && !clicked.getType().isAir()) {
                    draft.iconMaterial = clicked.getType();
                    open();
                }
            }
            return;
        }

        e.setCancelled(true);
        switch (raw) {
            case 4 -> promptString("Type the new name:", s -> { draft.name = s; open(); });
            case 13 -> promptDouble("Type the license price:", v -> { draft.price = v; open(); });
            case 15 -> promptDuration("Type the license lifetime (e.g. 7d, 24h):",
                    v -> { draft.validDurationMillis = v; open(); });
            case 22 -> new JobsListMenu(plugin, viewer, draft).open();
            case 36 -> new AdminMenu(plugin, viewer).open();
            case 40 -> save();
        }
    }

    private void save() {
        if (draft.iconMaterial == null) {
            MessageUtil.send(viewer, "&cYou must pick an icon material first.");
            return;
        }
        if (draft.jobs.isEmpty()) {
            MessageUtil.send(viewer, "&cAdd at least one job before saving.");
            return;
        }
        UUID id = draft.editingId != null ? draft.editingId : UUID.randomUUID();
        LicenseType lt = new LicenseType(id, draft.name, draft.iconMaterial,
                draft.price, draft.validDurationMillis);
        for (JobDraft jd : draft.jobs) {
            if (jd.material == null) continue;
            lt.getJobs().add(new JobTemplate(jd.material, jd.baseAmount, jd.baseReward, jd.jobDurationMillis));
        }
        if (lt.getJobs().isEmpty()) {
            MessageUtil.send(viewer, "&cAll job entries are missing materials.");
            return;
        }
        plugin.companies().data().saveLicenseType(lt);
        MessageUtil.send(viewer, "&aSaved license type: " + draft.name);
        new AdminMenu(plugin, viewer).open();
    }

    // ─── chat input helpers ───
    private void promptString(String msg, java.util.function.Consumer<String> cb) {
        viewer.closeInventory();
        MessageUtil.send(viewer, "&e" + msg);
        ChatInput.prompt(viewer, text -> {
            if (text.equalsIgnoreCase("cancel")) { open(); return; }
            cb.accept(text);
        });
    }

    private void promptDouble(String msg, java.util.function.DoubleConsumer cb) {
        viewer.closeInventory();
        MessageUtil.send(viewer, "&e" + msg);
        ChatInput.prompt(viewer, text -> {
            if (text.equalsIgnoreCase("cancel")) { open(); return; }
            try { cb.accept(Double.parseDouble(text.trim())); }
            catch (NumberFormatException ex) { MessageUtil.send(viewer, "&cNot a number."); open(); }
        });
    }

    private void promptDuration(String msg, java.util.function.LongConsumer cb) {
        viewer.closeInventory();
        MessageUtil.send(viewer, "&e" + msg);
        ChatInput.prompt(viewer, text -> {
            if (text.equalsIgnoreCase("cancel")) { open(); return; }
            Long ms = parseDuration(text.trim());
            if (ms == null) {
                MessageUtil.send(viewer, "&cInvalid duration. Use 1d, 3h, 30m, 45s, or combos like '1d12h'.");
                open();
                return;
            }
            cb.accept(ms);
        });
    }

    public static Long parseDuration(String s) {
        if (s == null) return null;
        s = s.toLowerCase().replace(" ", "");
        long total = 0;
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isDigit(ch)) num.append(ch);
            else {
                if (num.length() == 0) return null;
                long n = Long.parseLong(num.toString());
                num.setLength(0);
                switch (ch) {
                    case 'd' -> total += n * 86_400_000L;
                    case 'h' -> total += n * 3_600_000L;
                    case 'm' -> total += n * 60_000L;
                    case 's' -> total += n * 1_000L;
                    default -> { return null; }
                }
            }
        }
        if (num.length() > 0) {
            if (total == 0) total = Long.parseLong(num.toString()) * 60_000L;
            else return null;
        }
        return total <= 0 ? null : total;
    }
}
