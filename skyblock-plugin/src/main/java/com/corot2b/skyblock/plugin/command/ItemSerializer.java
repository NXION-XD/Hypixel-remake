package com.corot2b.skyblock.plugin.command;

import com.corot2b.api.model.ItemInstance;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import com.corot2b.skyblock.plugin.SkyBlockPlugin;

/**
 * Bridges {@link ItemInstance} and a Bukkit {@link ItemStack} through the
 * PersistentDataContainer, which is the supported way to attach custom data to items
 * on 1.21.x (no NMS, no deprecated NBT APIs).
 *
 * <p>Layout under the {@code corot2b} namespace:
 * <pre>
 *   id    : String  item type id
 *   uid   : String  instance uid — the anti-duplication key
 *   n     : Integer amount
 *   r     : String  reforge
 *   s     : Integer stars
 *   q     : Integer dungeon quality
 *   h     : Integer hot potato level
 *   e     : String  enchantments as "key:level,key:level"
 * </pre>
 */
public final class ItemSerializer {

    private static NamespacedKey key(String name) {
        return new NamespacedKey(SkyBlockPlugin.get(), name);
    }

    private ItemSerializer() {}

    /** Writes an instance onto a stack, replacing any previous SkyBlock data. */
    public static ItemStack write(ItemInstance instance, org.bukkit.Material fallbackMaterial) {
        ItemStack stack = new ItemStack(fallbackMaterial, Math.min(instance.amount(), fallbackMaterial.getMaxStackSize()));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        pdc.set(key("id"), PersistentDataType.STRING, instance.id());
        pdc.set(key("uid"), PersistentDataType.STRING, instance.uid());
        pdc.set(key("n"), PersistentDataType.INTEGER, instance.amount());
        if (instance.reforge() != null) pdc.set(key("r"), PersistentDataType.STRING, instance.reforge());
        if (instance.stars() > 0) pdc.set(key("s"), PersistentDataType.INTEGER, instance.stars());
        if (instance.quality() > 0) pdc.set(key("q"), PersistentDataType.INTEGER, instance.quality());
        if (instance.hotPotatoLevel() > 0) pdc.set(key("h"), PersistentDataType.INTEGER, instance.hotPotatoLevel());
        if (!instance.enchantments().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            instance.enchantments().forEach((k, v) -> {
                if (sb.length() > 0) sb.append(',');
                sb.append(k).append(':').append(v);
            });
            pdc.set(key("e"), PersistentDataType.STRING, sb.toString());
        }
        stack.setItemMeta(meta);
        stack.setAmount(Math.max(1, Math.min(instance.amount(), stack.getMaxStackSize())));
        return stack;
    }

    /**
     * Reads SkyBlock data off a stack. Returns null when the stack carries no
     * {@code id} tag, so callers can treat plain vanilla items as "nothing".
     */
    public static ItemInstance read(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(key("id"), PersistentDataType.STRING);
        if (id == null) return null;

        String uid = pdc.getOrDefault(key("uid"), PersistentDataType.STRING, ItemInstance.newUid());
        ItemInstance instance = ItemInstance.withUid(uid, id, stack.getAmount(), System.currentTimeMillis());

        String reforge = pdc.get(key("r"), PersistentDataType.STRING);
        if (reforge != null) instance.reforge(reforge);

        Integer stars = pdc.get(key("s"), PersistentDataType.INTEGER);
        if (stars != null) instance.stars(stars);

        Integer quality = pdc.get(key("q"), PersistentDataType.INTEGER);
        if (quality != null) instance.quality(quality);

        Integer hpb = pdc.get(key("h"), PersistentDataType.INTEGER);
        if (hpb != null) instance.hotPotato(hpb);

        String enchants = pdc.get(key("e"), PersistentDataType.STRING);
        if (enchants != null && !enchants.isBlank()) {
            for (String pair : enchants.split(",")) {
                int colon = pair.indexOf(':');
                if (colon <= 0) continue;
                try {
                    instance.enchant(pair.substring(0, colon), Integer.parseInt(pair.substring(colon + 1)));
                } catch (NumberFormatException ignored) {
                    // A malformed pair is dropped rather than failing the whole read.
                }
            }
        }
        return instance;
    }

    /** True when the stack is one of ours, regardless of whether it is readable. */
    public static boolean isSkyBlockItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(key("id"), PersistentDataType.STRING);
    }
}
