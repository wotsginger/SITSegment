package club.sitmc.sitSegment;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SITSegment extends JavaPlugin {

    private ParkourManager parkourManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        parkourManager = new ParkourManager(this);

        ParkourListener listener = new ParkourListener(parkourManager);
        getServer().getPluginManager().registerEvents(listener, this);

        ParkourCommand command = new ParkourCommand(parkourManager);
        PluginCommand pluginCommand = getCommand("sitpk");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        registerStandaloneCommand("prac", command);
        registerStandaloneCommand("unprac", command);
        registerStandaloneCommand("pracworld", command);
        registerStandaloneCommand("spec", command);
        registerStandaloneCommand("unspec", command);
        registerStandaloneCommand("specworld", command);
        PluginCommand topCommand = getCommand("top");
        if (topCommand != null) {
            topCommand.setExecutor(new TopCommand(parkourManager));
        }

        parkourManager.startTasks();
        reloadAll();
    }

    @Override
    public void onDisable() {
        if (parkourManager != null) {
            parkourManager.shutdown();
        }
    }

    public void reloadAll() {
        reloadConfig();
        parkourManager.load();
        parkourManager.restoreOnlinePlayers();
    }

    private void registerStandaloneCommand(String name, ParkourCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
