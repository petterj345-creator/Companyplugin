package dk.companies.gui;

import dk.companies.Companies;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Captures the next chat line a player sends and feeds it to a callback. */
public class ChatInput implements Listener {

    private static final Map<UUID, Consumer<String>> WAITING = new HashMap<>();

    private final Companies plugin;

    public ChatInput(Companies plugin) { this.plugin = plugin; }

    public static void prompt(Player p, Consumer<String> callback) {
        WAITING.put(p.getUniqueId(), callback);
    }

    public static void cancel(UUID id) { WAITING.remove(id); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Consumer<String> cb = WAITING.remove(p.getUniqueId());
        if (cb == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        // run on main thread
        Bukkit.getScheduler().runTask(plugin, () -> cb.accept(text));
    }
}
