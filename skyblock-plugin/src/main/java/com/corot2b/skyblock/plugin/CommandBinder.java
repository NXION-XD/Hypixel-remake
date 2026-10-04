package com.corot2b.skyblock.plugin;

import com.corot2b.skyblock.plugin.command.SbCommand;
import com.corot2b.skyblock.plugin.command.SkillsCommand;
import org.bukkit.command.PluginCommand;

/**
 * Registers the plugin's commands against the entries declared in plugin.yml.
 *
 * <p>Kept separate from the main class so gameplay code never has to be imported
 * there, and so a missing plugin.yml entry surfaces here as a clear log line rather
 * than a silent no-op.
 */
final class CommandBinder {

    private CommandBinder() {}

    static void bind(SkyBlockPlugin plugin) {
        bind(plugin, "skyblock", new SbCommand(plugin));
        bind(plugin, "skills", new SkillsCommand(plugin));
        bind(plugin, "skillsadmin", new SkillsCommand.Admin(plugin));
    }

    private static void bind(SkyBlockPlugin plugin, String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) {
            plugin.getLogger().warning("command '" + name + "' is missing from plugin.yml — not registered");
            return;
        }
        cmd.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tc) {
            cmd.setTabCompleter(tc);
        }
    }
}
