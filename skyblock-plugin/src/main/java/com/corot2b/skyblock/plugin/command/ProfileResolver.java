package com.corot2b.skyblock.plugin.command;

import com.corot2b.api.model.ItemInstance;
import com.corot2b.api.model.SkyBlockProfile;
import com.corot2b.skyblock.plugin.SkyBlockPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the SkyBlock profile belonging to an online player.
 *
 * <p>The uuid-to-profile mapping is cached on join so command handling never waits on
 * the database. If the mapping is missing (e.g. right after a reload) it falls back
 * to an async load and creates the profile on first use.
 */
public final class ProfileResolver {

    private static final ConcurrentHashMap<UUID, UUID> PLAYER_TO_PROFILE = new ConcurrentHashMap<>();

    private ProfileResolver() {}

    public static void bind(UUID playerUuid, UUID profileId) {
        PLAYER_TO_PROFILE.put(playerUuid, profileId);
    }

    public static void unbind(UUID playerUuid) {
        PLAYER_TO_PROFILE.remove(playerUuid);
    }

    public static CompletableFuture<SkyBlockProfile> resolve(SkyBlockPlugin plugin, Player player) {
        UUID profileId = PLAYER_TO_PROFILE.get(player.getUniqueId());
        if (profileId != null) return plugin.profiles().load(profileId);
        // No profile yet: create one. This is the only path that writes on first join.
        return plugin.profiles().create(player.getUniqueId(), player.getName()).thenApply(p -> {
            PLAYER_TO_PROFILE.put(player.getUniqueId(), p.profileId());
            return p;
        });
    }

    /**
     * Wraps the item in the player's main hand as an {@link ItemInstance}.
     * Returns null for an empty hand or a vanilla item the registry does not know.
     */
    public static ItemInstance heldItem(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack.getType().isAir()) return null;
        return ItemSerializer.read(stack);
    }
}
