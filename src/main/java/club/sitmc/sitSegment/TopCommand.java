package club.sitmc.sitSegment;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TopCommand implements CommandExecutor {
    private final ParkourManager manager;
    private final MessageUtil messages;

    public TopCommand(ParkourManager manager) {
        this.manager = manager;
        this.messages = manager.getMessages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "该命令只能在游戏内使用。");
            return true;
        }
        List<RecordEntry> top = manager.getTopRecords(player.getWorld(), 10);
        if (top.isEmpty()) {
            messages.send(sender, "当前地图暂无记录。");
            return true;
        }
        messages.send(sender, "当前地图排行榜:");
        int rank = 1;
        for (RecordEntry entry : top) {
            String line = rank + ". " + entry.getName() + " - " + manager.formatDuration(entry.getTimeMs());
            messages.send(sender, line);
            rank++;
        }
        return true;
    }
}
