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
    private static final String PERM_WORLD = "sitsegment.command.world";
    private static final String PERM_SET = "sitsegment.command.set";
    private static final String PERM_DEL = "sitsegment.command.del";
    private static final String PERM_RELOAD = "sitsegment.command.reload";
    private static final String PERM_EXIT = "sitsegment.command.exit";

    private final ParkourManager manager;
    private final MessageUtil messages;

    public ParkourCommand(ParkourManager manager) {
        this.manager = manager;
        this.messages = manager.getMessages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (manager.getPracticeSpecManager().onNamedCommand(command.getName(), sender)) {
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("exit".equals(sub)) {
            if (!sender.hasPermission(PERM_EXIT)) {
                messages.send(sender, "&c你没有权限。");
                return true;
            }
            handleExit(sender);
            return true;
        }

        switch (sub) {
            case "world":
                if (!sender.hasPermission(PERM_WORLD)) {
                    messages.send(sender, "&c你没有权限。");
                    return true;
                }
                handleWorld(sender, args);
                return true;
            case "set":
                if (!sender.hasPermission(PERM_SET)) {
                    messages.send(sender, "&c你没有权限。");
                    return true;
                }
                handleSet(sender, args);
                return true;
            case "del":
                if (!sender.hasPermission(PERM_DEL)) {
                    messages.send(sender, "&c你没有权限。");
                    return true;
                }
                handleDelete(sender, args);
                return true;
            case "reload":
                if (!sender.hasPermission(PERM_RELOAD)) {
                    messages.send(sender, "&c你没有权限。");
                    return true;
                }
                handleReload(sender);
                return true;
            case "prac":
                manager.getPracticeSpecManager().handlePrac(sender);
                return true;
            case "unprac":
                manager.getPracticeSpecManager().handleUnprac(sender);
                return true;
            case "pracworld":
                manager.getPracticeSpecManager().handlePracWorld(sender);
                return true;
            case "spec":
                manager.getPracticeSpecManager().handleSpec(sender);
                return true;
            case "unspec":
                manager.getPracticeSpecManager().handleUnspec(sender);
                return true;
            case "specworld":
                manager.getPracticeSpecManager().handleSpecWorld(sender);
                return true;
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void handleWorld(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "&c该命令只能由玩家执行。");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "&f用法: /sitpk world <segment|onlysprint|none>");
            return;
        }
        WorldMode mode = parseMode(args[1]);
        if (mode == null) {
            messages.send(sender, "&c模式只能是 segment、onlysprint 或 none。");
            return;
        }
        World world = player.getWorld();
        manager.setWorldMode(world, mode);
        if (mode == WorldMode.NONE) {
            messages.send(sender, "&a已为当前世界关闭跑酷模式。");
            return;
        }
        String modeName = mode == WorldMode.SEGMENT ? "segment" : "onlysprint";
        messages.send(sender, "&a已为当前世界启用模式: &f" + modeName);
        manager.giveDefaultItems(player);
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "&c该命令只能由玩家执行。");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "&f用法: /sitpk set <start|checkpoint|end> [编号]");
            return;
        }
        String target = args[1].toLowerCase();
        World world = player.getWorld();
        switch (target) {
            case "start":
                manager.setStart(world, player.getLocation());
                messages.send(sender, "&a已设置起点。");
                return;
            case "end":
                manager.setEnd(world, player.getLocation());
                messages.send(sender, "&a已设置终点。");
                return;
            case "checkpoint":
                if (args.length < 3) {
                    messages.send(sender, "&f用法: /sitpk set checkpoint <编号>");
                    return;
                }
                Integer index = parseIndex(args[2], sender);
                if (index == null) {
                    return;
                }
                manager.setCheckpoint(world, index, player.getLocation());
                messages.send(sender, "&a已设置记录点: &f" + index);
                return;
            default:
                messages.send(sender, "&f用法: /sitpk set <start|checkpoint|end> [编号]");
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "&c该命令只能由玩家执行。");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "&f用法: /sitpk del <start|checkpoint|end> [编号]");
            return;
        }
        String target = args[1].toLowerCase();
        World world = player.getWorld();
        switch (target) {
            case "start":
                manager.removeStart(world);
                messages.send(sender, "&a已删除起点。");
                return;
            case "end":
                manager.removeEnd(world);
                messages.send(sender, "&a已删除终点。");
                return;
            case "checkpoint":
                if (args.length < 3) {
                    messages.send(sender, "&f用法: /sitpk del checkpoint <编号>");
                    return;
                }
                Integer index = parseIndex(args[2], sender);
                if (index == null) {
                    return;
                }
                manager.removeCheckpoint(world, index);
                messages.send(sender, "&a已删除记录点: &f" + index);
                return;
            default:
                messages.send(sender, "&f用法: /sitpk del <start|checkpoint|end> [编号]");
        }
    }

    private void handleReload(CommandSender sender) {
        manager.getPlugin().reloadAll();
        messages.send(sender, "&a配置已重载。");
    }

    private void handleExit(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "&c该命令只能由玩家执行。");
            return;
        }
        manager.handleExitParkour(player);
    }

    private void sendUsage(CommandSender sender) {
        if (sender.hasPermission(PERM_EXIT)) {
            messages.send(sender, "&f用法: /sitpk exit");
        }
        if (sender.hasPermission(PERM_WORLD)) {
            messages.send(sender, "&f用法: /sitpk world <segment|onlysprint|none>");
        }
        if (sender.hasPermission(PERM_SET)) {
            messages.send(sender, "&f用法: /sitpk set <start|checkpoint|end> [编号]");
        }
        if (sender.hasPermission(PERM_DEL)) {
            messages.send(sender, "&f用法: /sitpk del <start|checkpoint|end> [编号]");
        }
        if (sender.hasPermission(PERM_RELOAD)) {
            messages.send(sender, "&f用法: /sitpk reload");
        }
        if (sender.hasPermission("sitsegment.command.prac")) {
            messages.send(sender, "&f用法: /sitpk prac 或 /prac");
        }
        if (sender.hasPermission("sitsegment.command.unprac")) {
            messages.send(sender, "&f用法: /sitpk unprac 或 /unprac");
        }
        if (sender.hasPermission("sitsegment.command.pracworld")) {
            messages.send(sender, "&f用法: /sitpk pracworld 或 /pracworld");
        }
        if (sender.hasPermission("sitsegment.command.spec")) {
            messages.send(sender, "&f用法: /sitpk spec 或 /spec");
        }
        if (sender.hasPermission("sitsegment.command.unspec")) {
            messages.send(sender, "&f用法: /sitpk unspec 或 /unspec");
        }
        if (sender.hasPermission("sitsegment.command.specworld")) {
            messages.send(sender, "&f用法: /sitpk specworld 或 /specworld");
        }
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
            messages.send(sender, "&c编号必须是正整数。");
            return null;
        }
        if (index <= 0) {
            messages.send(sender, "&c编号必须是正整数。");
            return null;
        }
        return index;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase();
        if (!"sitpk".equals(commandName)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission(PERM_EXIT)) {
                subs.add("exit");
            }
            if (sender.hasPermission(PERM_WORLD)) {
                subs.add("world");
            }
            if (sender.hasPermission(PERM_SET)) {
                subs.add("set");
            }
            if (sender.hasPermission(PERM_DEL)) {
                subs.add("del");
            }
            if (sender.hasPermission(PERM_RELOAD)) {
                subs.add("reload");
            }
            if (sender.hasPermission("sitsegment.command.prac")) {
                subs.add("prac");
            }
            if (sender.hasPermission("sitsegment.command.unprac")) {
                subs.add("unprac");
            }
            if (sender.hasPermission("sitsegment.command.pracworld")) {
                subs.add("pracworld");
            }
            if (sender.hasPermission("sitsegment.command.spec")) {
                subs.add("spec");
            }
            if (sender.hasPermission("sitsegment.command.unspec")) {
                subs.add("unspec");
            }
            if (sender.hasPermission("sitsegment.command.specworld")) {
                subs.add("specworld");
            }
            return filterPrefix(args[0], subs);
        }
        if (args.length == 2) {
            if ("world".equalsIgnoreCase(args[0]) && sender.hasPermission(PERM_WORLD)) {
                return filterPrefix(args[1], List.of("segment", "onlysprint", "none"));
            }
            if (("set".equalsIgnoreCase(args[0]) && sender.hasPermission(PERM_SET))
                    || ("del".equalsIgnoreCase(args[0]) && sender.hasPermission(PERM_DEL))) {
                return filterPrefix(args[1], List.of("start", "checkpoint", "end"));
            }
        }
        if (args.length == 3
                && (("set".equalsIgnoreCase(args[0]) && sender.hasPermission(PERM_SET))
                || ("del".equalsIgnoreCase(args[0]) && sender.hasPermission(PERM_DEL)))
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
        String lower = input.toLowerCase();
        for (String option : options) {
            if (option.startsWith(lower)) {
                results.add(option);
            }
        }
        return results;
    }
}
