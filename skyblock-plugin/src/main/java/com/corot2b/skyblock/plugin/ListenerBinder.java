package com.corot2b.skyblock.plugin;

import com.corot2b.skyblock.plugin.command.ProfileResolver;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Registers listeners and owns the join/quit lifecycle that keeps the profile cache
 * and the save queue consistent.
 */
final class ListenerBinder {

    private ListenerBinder() {}

    static void bind(SkyBlockPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new ProfileLifecycle(plugin), plugin);
    }

    static final class ProfileLifecycle implements Listener {

        private final SkyBlockPlugin plugin;

        ProfileLifecycle(SkyBlockPlugin plugin) {
            this.plugin = plugin;
        }

        /**
         * MONITOR so we run after anything that might kick the player (whitelist,
         * ban). Loading is async — the player is never held at the login screen
         * waiting on the database.
         */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onJoin(PlayerJoinEvent event) {
            ProfileResolver.resolve(plugin, event.getPlayer()).thenAccept(profile -> {
                if (profile == null) {
                    plugin.getLogger().warning("could not load or create a profile for "
                            + event.getPlayer().getName());
                    return;
                }
                profile.lastSeen(System.currentTimeMillis());
                plugin.profiles().markDirty(profile);
            });
        }

        /** Flush this player's profile on quit so a crash after they leave is harmless. */
        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            ProfileResolver.resolve(plugin, event.getPlayer()).thenAccept(profile -> {
                if (profile != null) plugin.profiles().evict(profile.profileId());
                ProfileResolver.unbind(event.getPlayer().getUniqueId());
            });
        }
    }
}
