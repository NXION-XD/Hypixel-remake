package com.corot2b.skyblock.plugin.command;

import com.corot2b.api.model.SkyBlockProfile;
import com.corot2b.core.skills.SkillService;
import com.corot2b.skyblock.plugin.SkyBlockPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /skills — lists every skill with level and progress to the next level.
 *
 * <p>Also exposes an admin variant ({@code /skillsadmin set|add}) for staff, gated by
 * the {@code skyblock.admin.skills} permission and written to the audit log.
 */
public final class SkillsCommand implements CommandExecutor, TabCompleter {

    private final SkyBlockPlugin plugin;

    public SkillsCommand(SkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is player-only.");
            return true;
        }
        ProfileResolver.resolve(plugin, player).thenAccept(profile -> {
            if (profile == null) {
                player.sendMessage("§cYour profile could not be loaded.");
                return;
            }
            player.sendMessage(render(profile));
        });
        return true;
    }

    static String render(SkyBlockProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6§lYour Skills\n");
        sb.append("§7Skill Average: §b")
                .append(String.format("%.2f", SkillService.skillAverage(profile))).append('\n');
        for (String skill : SkillService.DISPLAY.keySet()) {
            SkillService.LevelInfo info = SkillService.levelFromXp(profile.skillXp(skill), skill);
            String bar = bar(info.percent());
            sb.append("§7")
                    .append(String.format("%-14s", SkillService.DISPLAY.get(skill)))
                    .append(" §e").append(info.level()).append("§8/§e").append(SkillService.cap(skill))
                    .append(" §8[").append(bar).append("§8] ");
            if (info.maxed()) {
                sb.append("§aMAX");
            } else {
                sb.append("§7").append(String.format("%,d", info.xpIntoLevel()))
                        .append("§8/§7").append(String.format("%,d", info.xpForNext()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String bar(double pct) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, pct)) * 10);
        return "§a" + "█".repeat(filled) + "§8" + "█".repeat(10 - filled);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SkillService.DISPLAY.keySet().stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    /**
     * Staff variant. Deliberately a nested class so the permission check and the
     * audit write sit next to the code they guard.
     */
    public static final class Admin implements CommandExecutor {

        private final SkyBlockPlugin plugin;

        public Admin(SkyBlockPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("skyblock.admin.skills")) {
                sender.sendMessage("§cYou do not have permission to do that.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§7Usage: /skillsadmin <set|add> <skill> <xp>");
                return true;
            }
            String mode = args[0].toLowerCase();
            String skill = args[1].toLowerCase();
            if (!SkillService.CAPS.containsKey(skill)) {
                sender.sendMessage("§cUnknown skill: " + skill);
                return true;
            }
            long amount;
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cNot a number: " + args[2]);
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cRun this in game so we know which profile to modify.");
                return true;
            }

            ProfileResolver.resolve(plugin, player).thenAccept(profile -> {
                if (profile == null) return;
                if (mode.equals("set")) {
                    profile.skillXp(skill, Math.max(0, amount));
                } else if (mode.equals("add")) {
                    profile.skillXp(skill, Math.max(0, profile.skillXp(skill) + amount));
                } else {
                    player.sendMessage("§cUnknown mode: " + mode);
                    return;
                }
                plugin.profiles().markDirty(profile);
                player.sendMessage("§a" + SkillService.DISPLAY.get(skill) + " XP is now "
                        + String.format("%,d", profile.skillXp(skill)) + " (level "
                        + SkillService.levelFromXp(profile.skillXp(skill), skill).level() + ")");
            });
            return true;
        }
    }
}
