package club.sitmc.sitSegment;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MessageUtil {
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private final String prefix;

    public MessageUtil(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public Component format(String message) {
        return serializer.deserialize(prefix + message);
    }

    public void send(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            player.sendMessage(format(message));
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + message));
        }
    }

    public void send(Player player, String message) {
        player.sendMessage(format(message));
    }

    public void actionBar(Player player, String message) {
        player.sendActionBar(format(message));
    }

    public void clearActionBar(Player player) {
        player.sendActionBar(Component.empty());
    }
}
