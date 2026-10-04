package com.corot2b.skyblock.plugin.command;

import com.corot2b.api.model.PlayerStats;
import com.corot2b.api.model.SkyBlockProfile;
import com.corot2b.core.stats.StatCalculator;
import com.corot2b.skyblock.plugin.SkyBlockPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /skyblock — prints the caller's aggregated stats and purse.
 *
 * <p>Reads through the same {@link StatCalculator} path the combat engine uses, so
 * what a player sees here is exactly what the damage formula will use.
 */
public final class SbCommand implements CommandExecutor {

    private final SkyBlockPlugin plugin;

    public SbCommand(SkyBlockPlugin plugin) {
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
                player.sendMessage("§cYour profile could not be loaded. Try rejoining.");
                return;
            }
            render(player, profile);
        }).exceptionally(e -> {
            plugin.getLogger().warning("stat lookup failed for " + player.getName() + ": " + e.getMessage());
            player.sendMessage("§cCould not compute your stats right now.");
            return null;
        });
        return true;
    }

    private void render(Player player, SkyBlockProfile profile) {
        PlayerStats stats = StatCalculator.calculate(profile, ProfileResolver.heldItem(player));
        double ehp = stats.effectiveHealth();

        StringBuilder sb = new StringBuilder();
        sb.append("§6§lSkyBlock Stats\n");
        sb.append("§7Profile: §f").append(profile.name()).append('\n');
        sb.append("§7Purse: §6").append(String.format("%,d", profile.coinsCents())).append(" coins\n");
        sb.append("§7Bank: §6").append(String.format("%,d", profile.bankCents())).append(" coins\n");
        sb.append("§7Skill Average: §b")
                .append(String.format("%.1f", com.corot2b.core.skills.SkillService.skillAverage(profile))).append('\n');
        sb.append("§7Effective Health: §a").append(String.format("%,.0f", ehp)).append('\n');
        sb.append("§7Damage Reduction: §a")
                .append(String.format("%.1f%%", stats.damageReduction() * 100)).append('\n');
        sb.append("§8--- contributions ---\n");
        for (String line : StatCalculator.describe(stats)) {
            sb.append("§7").append(line).append('\n');
        }
        player.sendMessage(sb.toString());
    }
}
