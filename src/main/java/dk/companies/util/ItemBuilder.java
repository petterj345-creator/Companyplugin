package dk.companies.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tiny builder around ItemStack for menu icons. */
public final class ItemBuilder {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final ItemStack stack;
    private final ItemMeta meta;

    private ItemBuilder(Material m, int amount) {
        this.stack = new ItemStack(m, amount);
        this.meta = stack.getItemMeta();
    }

    public static ItemBuilder of(Material m) { return new ItemBuilder(m, 1); }
    public static ItemBuilder of(Material m, int amount) { return new ItemBuilder(m, amount); }

    public ItemBuilder name(String legacy) {
        if (meta != null) {
            meta.displayName(LEGACY.deserialize(legacy)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        if (meta != null) {
            List<Component> comps = new ArrayList<>();
            for (String l : lines) {
                comps.add(LEGACY.deserialize(l).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(comps);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        return lore(lines.toArray(new String[0]));
    }

    public ItemBuilder flag(ItemFlag... flags) {
        if (meta != null) meta.addItemFlags(flags);
        return this;
    }

    public ItemBuilder glow(boolean g) {
        if (meta != null && g) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder pdc(NamespacedKey key, String value) {
        if (meta != null) {
            PersistentDataContainer c = meta.getPersistentDataContainer();
            c.set(key, PersistentDataType.STRING, value);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) stack.setItemMeta(meta);
        return stack;
    }

    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    public static List<String> asLore(String... s) { return new ArrayList<>(Arrays.asList(s)); }
}
