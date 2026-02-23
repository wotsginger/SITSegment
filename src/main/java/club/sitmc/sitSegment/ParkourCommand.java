package club.sitmc.sitSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ParkourCommand implements CommandExecutor, TabCompleter {
    private final ParkourManager manager;
    private final MessageUtil messages;

    public ParkourCommand(ParkourManager manager) {
        this.manager = manager;
        this.messages = manager.getMessages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sitsegment.admin")) {
            messages.send(sender, "没有权限。");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "world":
                handleWorld(sender, args);
                return true;
            case "set":
                handleSet(sender, args);
                return true;
            case "del":
                handleDelete(sender, args);
                return true;
            case "reload":
                handleReload(sender);
                return true;
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void handleWorld(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "该命令只能在游戏内使用。");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "用法: /sitsegment world <segment|onlysprint|none>");
            return;
        }
        WorldMode mode = parseMode(args[1]);
        if (mode == null) {
            messages.send(sender, "模式只能是 segment、onlysprint 或 none。");
            return;
        }
        World world = player.getWorld();
        manager.setWorldMode(world, mode);
        if (mode == WorldMode.NONE) {
            messages.send(sender, "已为当前世界关闭所有模式。");
            return;
        }
        String modeName = mode == WorldMode.SEGMENT ? "segment" : "onlysprint";
        messages.send(sender, "已为当前世界启用 " + modeName + " 模式。");
        manager.giveDefaultItems(player);
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "该命令只能在游戏内使用。");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "用法: /sitsegment set <start|checkpoint|end> [编号]");
            return;
        }
        String target = args[1].toLowerCase();
        World world = player.getWorld();
        switch (target) {
            case "start":
                manager.setStart(world, player.getLocation());
                messages.send(sender, "已设置起点(记录点)。");
                return;
            case "end":
                manager.setEnd(world, player.getLocation());
                messages.send(sender, "已设置终点。");
                return;
            case "checkpoint":
                if (args.length < 3) {
                    messages.send(sender, "用法: /sitsegment set checkpoint <编号>");
                    return;
                }
                Integer index = parseIndex(args[2], sender);
                if (index == null) {
                    return;
                }
                manager.setCheckpoint(world, index, player.getLocation());
                messages.send(sender, "已设置记录点 " + index + "。");
                return;
            default:
                messages.send(sender, "用法: /sitsegment set <start|checkpoint|end> [编号]");
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "该命令只能在游戏内使用。");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "用法: /sitsegment del <start|checkpoint|end> [编号]");
            return;
        }
        String target = args[1].toLowerCase();
        World world = player.getWorld();
        switch (target) {
            case "start":
                manager.removeStart(world);
                messages.send(sender, "已删除起点(记录点)。");
                return;
            case "end":
                manager.removeEnd(world);
                messages.send(sender, "已删除终点。");
                return;
            case "checkpoint":
                if (args.length < 3) {
                    messages.send(sender, "用法: /sitsegment del checkpoint <编号>");
                    return;
                }
                Integer index = parseIndex(args[2], sender);
                if (index == null) {
                    return;
                }
                manager.removeCheckpoint(world, index);
                messages.send(sender, "已删除记录点 " + index + "。");
                return;
            default:
                messages.send(sender, "用法: /sitsegment del <start|checkpoint|end> [编号]");
        }
    }

    private void handleReload(CommandSender sender) {
        manager.getPlugin().reloadAll();
        messages.send(sender, "已刷新配置并重新加载。");
    }

    private void sendUsage(CommandSender sender) {
        messages.send(sender, "用法: /sitsegment world <segment|onlysprint|none>");
        messages.send(sender, "用法: /sitsegment set <start|checkpoint|end> [编号]");
        messages.send(sender, "用法: /sitsegment del <start|checkpoint|end> [编号]");
        messages.send(sender, "用法: /sitsegment reload");
    }

    private WorldMode parseMode(String input) {
        if ("segment".equalsIgnoreCase(input)) {
            return WorldMode.SEGMENT;
        }
        if ("onlysprint".equalsIgnoreCase(input)) {
            return WorldMode.ONLY_SPRINT;
        }
        if ("none".equalsIgnoreCase(input)) {
            return WorldMode.NONE;
        }
        return null;
    }

    private Integer parseIndex(String input, CommandSender sender) {
        int index;
        try {
            index = Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            messages.send(sender, "编号必须是正整数。");
            return null;
        }
        if (index <= 0) {
            messages.send(sender, "编号必须是正整数。");
            return null;
        }
        return index;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(args[0], List.of("world", "set", "del", "reload"));
        }
        if (args.length == 2) {
            if ("world".equalsIgnoreCase(args[0])) {
                return filterPrefix(args[1], List.of("segment", "onlysprint", "none"));
            }
            if ("set".equalsIgnoreCase(args[0]) || "del".equalsIgnoreCase(args[0])) {
                return filterPrefix(args[1], List.of("start", "checkpoint", "end"));
            }
        }
        if (args.length == 3 && ("set".equalsIgnoreCase(args[0]) || "del".equalsIgnoreCase(args[0]))
                && "checkpoint".equalsIgnoreCase(args[1])) {
            return filterPrefix(args[2], List.of("1", "2", "3"));
        }
        return Collections.emptyList();
    }

    private List<String> filterPrefix(String input, List<String> options) {
        if (input == null || input.isEmpty()) {
            return options;
        }
        List<String> results = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(input.toLowerCase())) {
                results.add(option);
            }
        }
        return results;
    }
}
