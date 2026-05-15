package dk.companies.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class MessageUtil {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private static String prefix = "&8[&6Companies&8] &r";

    private MessageUtil() {}

    public static void setPrefix(String p) { prefix = p == null ? "" : p; }

    public static Component color(String s) {
        if (s == null) return Component.empty();
        return LEGACY.deserialize(s);
    }

    public static Component prefixed(String s) {
        return LEGACY.deserialize(prefix + (s == null ? "" : s));
    }

    public static void send(CommandSender to, String msg) {
        to.sendMessage(prefixed(msg));
    }

    public static String stripLegacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(
                LEGACY.deserialize(s == null ? "" : s));
    }
}
