package com.corot2b.core.content;

import com.corot2b.api.model.PlayerStats;
import com.corot2b.api.model.Rarity;
import com.corot2b.core.content.ItemDefinition.Ability;
import com.corot2b.core.content.ItemDefinition.Category;
import com.corot2b.core.content.ItemDefinition.MinionInfo;
import com.corot2b.core.content.ItemDefinition.PetInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The item registry. Every item type in the game is declared here as code, expanded
 * from compact tier tables, so the whole catalogue is type-checked at build time and
 * browsable in an IDE. Roughly 1,100 item types result from the tables below.
 *
 * <p>Content is intentionally not read from JSON/YAML: a typo in a stat key or an
 * unknown rarity becomes a compile error instead of a silent runtime default.
 * Operators tune *balance* (drop rates, taxes, XP) in config.yml; they do not
 * re-author the item catalogue.
 */
public final class ItemRegistry {

    private static final Map<String, ItemDefinition> ITEMS = new LinkedHashMap<>();

    static {
        registerMaterials();
        registerEnchanted();
        registerTools();
        registerArmor();
        registerSignatureWeapons();
        registerDungeonGear();
        registerMinions();
        registerPets();
        registerConsumables();
        registerAccessories();
    }

    private ItemRegistry() {}

    // ------------------------------------------------------------------ lookups
    public static Optional<ItemDefinition> get(String id) {
        return Optional.ofNullable(ITEMS.get(id));
    }

    public static ItemDefinition require(String id) {
        ItemDefinition d = ITEMS.get(id);
        if (d == null) throw new IllegalArgumentException("unknown item: " + id);
        return d;
    }

    public static boolean exists(String id) {
        return ITEMS.containsKey(id);
    }

    public static Collection<ItemDefinition> all() {
        return Collections.unmodifiableCollection(ITEMS.values());
    }

    public static List<ItemDefinition> byCategory(Category c) {
        List<ItemDefinition> out = new ArrayList<>();
        for (ItemDefinition d : ITEMS.values()) if (d.category() == c) out.add(d);
        return out;
    }

    public static List<ItemDefinition> byTag(String tag) {
        List<ItemDefinition> out = new ArrayList<>();
        for (ItemDefinition d : ITEMS.values()) if (d.hasTag(tag)) out.add(d);
        return out;
    }

    public static List<ItemDefinition> armorSet(String setKey) {
        List<ItemDefinition> out = new ArrayList<>();
        for (ItemDefinition d : ITEMS.values()) if (setKey.equals(d.armorSet())) out.add(d);
        return out;
    }

    public static int size() { return ITEMS.size(); }

    private static void register(ItemDefinition d) {
        ItemDefinition prev = ITEMS.putIfAbsent(d.id(), d);
        if (prev != null) throw new IllegalStateException("duplicate item id: " + d.id());
    }

    private static void register(ItemDefinition.Builder b) {
        register(b.build());
    }

    // ----------------------------------------------------------- base materials
    /** id, display name, NPC value, collection/skill tag */
    private static final String[][] MATERIALS = {
            {"wheat", "Wheat", "2", "farming"},
            {"carrot", "Carrot", "2", "farming"},
            {"potato", "Potato", "2", "farming"},
            {"melon_slice", "Melon Slice", "1", "farming"},
            {"pumpkin", "Pumpkin", "4", "farming"},
            {"sugar_cane", "Sugar Cane", "2", "farming"},
            {"cactus", "Cactus", "2", "farming"},
            {"nether_wart", "Nether Wart", "4", "farming"},
            {"cocoa_beans", "Cocoa Beans", "3", "farming"},
            {"mushroom_red", "Red Mushroom", "3", "farming"},
            {"mushroom_brown", "Brown Mushroom", "3", "farming"},
            {"seeds", "Seeds", "1", "farming"},
            {"raw_beef", "Raw Beef", "5", "farming"},
            {"raw_porkchop", "Raw Porkchop", "5", "farming"},
            {"raw_chicken", "Raw Chicken", "5", "farming"},
            {"raw_mutton", "Raw Mutton", "5", "farming"},
            {"rabbit_hide", "Rabbit Hide", "6", "farming"},
            {"leather", "Leather", "8", "farming"},
            {"egg", "Egg", "3", "farming"},
            {"milk_bucket", "Milk Bucket", "12", "farming"},

            {"cobblestone", "Cobblestone", "1", "mining"},
            {"coal", "Coal", "3", "mining"},
            {"iron_ingot", "Iron Ingot", "8", "mining"},
            {"gold_ingot", "Gold Ingot", "12", "mining"},
            {"lapis_lazuli", "Lapis Lazuli", "6", "mining"},
            {"redstone_dust", "Redstone", "5", "mining"},
            {"diamond", "Diamond", "20", "mining"},
            {"emerald", "Emerald", "25", "mining"},
            {"quartz", "Nether Quartz", "14", "mining"},
            {"obsidian", "Obsidian", "30", "mining"},
            {"glowstone_dust", "Glowstone Dust", "10", "mining"},
            {"mithril_ingot", "Mithril Ingot", "90", "mining"},
            {"titanium_ingot", "Titanium Ingot", "320", "mining"},
            {"gemstone_rough", "Rough Gemstone", "45", "mining"},
            {"gemstone_flawed", "Flawed Gemstone", "260", "mining"},
            {"gemstone_fine", "Fine Gemstone", "1600", "mining"},
            {"gemstone_flawless", "Flawless Gemstone", "12000", "mining"},
            {"gemstone_perfect", "Perfect Gemstone", "90000", "mining"},
            {"glacite_ingot", "Glacite Ingot", "4000", "mining"},
            {"divan_alloy", "Divan Alloy", "250000", "mining"},
            {"hard_stone", "Hard Stone", "2", "mining"},

            {"oak_log", "Oak Log", "2", "foraging"},
            {"spruce_log", "Spruce Log", "4", "foraging"},
            {"birch_log", "Birch Log", "6", "foraging"},
            {"dark_oak_log", "Dark Oak Log", "10", "foraging"},
            {"acacia_log", "Acacia Log", "14", "foraging"},
            {"jungle_log", "Jungle Log", "18", "foraging"},
            {"jungle_heart", "Jungle Heart", "40", "foraging"},

            {"raw_fish", "Raw Fish", "4", "fishing"},
            {"raw_salmon", "Raw Salmon", "6", "fishing"},
            {"prismarine_shard", "Prismarine Shard", "12", "fishing"},
            {"prismarine_crystal", "Prismarine Crystal", "18", "fishing"},
            {"clay_ball", "Clay", "3", "fishing"},
            {"sponge", "Wet Sponge", "40", "fishing"},
            {"ink_sac", "Ink Sac", "8", "fishing"},
            {"ancient_claw", "Ancient Claw", "900", "fishing"},

            {"rotten_flesh", "Rotten Flesh", "2", "combat"},
            {"bone", "Bone", "4", "combat"},
            {"spider_eye", "Spider Eye", "6", "combat"},
            {"string", "String", "5", "combat"},
            {"gunpowder", "Gunpowder", "8", "combat"},
            {"ender_pearl", "Ender Pearl", "25", "combat"},
            {"ghast_tear", "Ghast Tear", "60", "combat"},
            {"blaze_rod", "Blaze Rod", "30", "combat"},
            {"magma_cream", "Magma Cream", "24", "combat"},
            {"slime_ball", "Slime Ball", "12", "combat"},
            {"revenant_fang", "Revenant Fang", "2600", "combat"},
            {"undead_catalyst", "Undead Catalyst", "150000", "combat"},
            {"tarantula_silk", "Tarantula Silk", "3200", "combat"},
            {"wolf_tooth", "Wolf Tooth", "2900", "combat"},
            {"red_claw_egg", "Red Claw Egg", "190000", "combat"},
            {"voidwalker_silk", "Voidwalker Silk", "14000", "combat"},

            {"experience_bottle", "Bottle o' Enchanting", "12", "enchanting"},
            {"nether_star", "Nether Star", "2400000", "special"},
            {"dragon_claw", "Dragon Claw", "35000", "special"},
            {"summoning_eye", "Summoning Eye", "400000", "special"},
    };

    private static void registerMaterials() {
        for (String[] m : MATERIALS) {
            register(ItemDefinition.builder(m[0], m[1])
                    .category(Category.MATERIAL)
                    .value(Long.parseLong(m[2]))
                    .tags(m[3], "material")
                    .collection(m[0])
                    .lore("A " + m[1].toLowerCase(Locale.ROOT) + "."));
        }
    }

    // ------------------------------------------------------ enchanted materials
    // One enchanted unit condenses 160 base; tier-2 condenses 160 enchanted.
    private static final String[][] ENCHANTABLE = {
            {"wheat", "Enchanted Wheat"}, {"carrot", "Enchanted Carrot"}, {"potato", "Enchanted Potato"},
            {"melon_slice", "Enchanted Melon"}, {"pumpkin", "Enchanted Pumpkin"},
            {"sugar_cane", "Enchanted Sugar Cane"}, {"cactus", "Enchanted Cactus"},
            {"nether_wart", "Enchanted Nether Wart"}, {"cocoa_beans", "Enchanted Cocoa Beans"},
            {"mushroom_red", "Enchanted Red Mushroom"}, {"mushroom_brown", "Enchanted Brown Mushroom"},
            {"raw_beef", "Enchanted Raw Beef"}, {"raw_porkchop", "Enchanted Pork"},
            {"raw_chicken", "Enchanted Raw Chicken"}, {"raw_mutton", "Enchanted Mutton"},
            {"leather", "Enchanted Leather"},
            {"cobblestone", "Enchanted Cobblestone"}, {"coal", "Enchanted Coal"},
            {"iron_ingot", "Enchanted Iron"}, {"gold_ingot", "Enchanted Gold"},
            {"lapis_lazuli", "Enchanted Lapis Lazuli"}, {"redstone_dust", "Enchanted Redstone"},
            {"diamond", "Enchanted Diamond"}, {"emerald", "Enchanted Emerald"},
            {"quartz", "Enchanted Quartz"}, {"obsidian", "Enchanted Obsidian"},
            {"glowstone_dust", "Enchanted Glowstone Dust"},
            {"mithril_ingot", "Enchanted Mithril"}, {"titanium_ingot", "Enchanted Titanium"},
            {"oak_log", "Enchanted Oak Wood"}, {"spruce_log", "Enchanted Spruce Wood"},
            {"birch_log", "Enchanted Birch Wood"}, {"dark_oak_log", "Enchanted Dark Oak Wood"},
            {"acacia_log", "Enchanted Acacia Wood"}, {"jungle_log", "Enchanted Jungle Wood"},
            {"raw_fish", "Enchanted Raw Fish"}, {"raw_salmon", "Enchanted Raw Salmon"},
            {"prismarine_shard", "Enchanted Prismarine Shard"},
            {"prismarine_crystal", "Enchanted Prismarine Crystal"},
            {"clay_ball", "Enchanted Clay"}, {"ink_sac", "Enchanted Ink Sac"},
            {"rotten_flesh", "Enchanted Rotten Flesh"}, {"bone", "Enchanted Bone"},
            {"spider_eye", "Enchanted Spider Eye"}, {"string", "Enchanted String"},
            {"gunpowder", "Enchanted Gunpowder"}, {"ender_pearl", "Enchanted Ender Pearl"},
            {"blaze_rod", "Enchanted Blaze Rod"}, {"magma_cream", "Enchanted Magma Cream"},
            {"slime_ball", "Enchanted Slime Ball"}, {"ghast_tear", "Enchanted Ghast Tear"},
            {"seeds", "Enchanted Seeds"}, {"egg", "Enchanted Egg"}, {"hard_stone", "Enchanted Hard Stone"},
    };

    private static void registerEnchanted() {
        for (String[] e : ENCHANTABLE) {
            long base = ITEMS.containsKey(e[0]) ? ITEMS.get(e[0]).npcValue() : 5L;
            register(ItemDefinition.builder("enchanted_" + e[0], e[1])
                    .category(Category.MATERIAL)
                    .rarity(Rarity.UNCOMMON)
                    .value(Math.round(base * 160 * 1.15))
                    .tags("enchanted", "material")
                    .collection(e[0])
                    .lore("Crafted from 160 " + ITEMS.get(e[0]).name() + ".",
                            "Used in advanced recipes."));

            register(ItemDefinition.builder("enchanted_" + e[0] + "_2", "Enchanted Block of " + e[1].substring(10))
                    .category(Category.MATERIAL)
                    .rarity(Rarity.RARE)
                    .value(Math.round(base * 160L * 160L * 1.1))
                    .tags("enchanted", "material")
                    .collection(e[0])
                    .lore("An extremely condensed block of material."));
        }
    }

    // ------------------------------------------------------------------- tools
    private record ToolMaterial(String key, String label, Rarity rarity, int tier, double damage, double speed) {}

    private static final List<ToolMaterial> TOOL_MATERIALS = List.of(
            new ToolMaterial("wood", "Wooden", Rarity.COMMON, 1, 4, 120),
            new ToolMaterial("stone", "Stone", Rarity.COMMON, 1, 7, 130),
            new ToolMaterial("iron", "Iron", Rarity.COMMON, 2, 12, 145),
            new ToolMaterial("gold", "Golden", Rarity.COMMON, 3, 9, 150),
            new ToolMaterial("diamond", "Diamond", Rarity.RARE, 3, 20, 165),
            new ToolMaterial("netherite", "Netherite", Rarity.EPIC, 5, 32, 185),
            new ToolMaterial("mithril", "Mithril", Rarity.EPIC, 5, 38, 200),
            new ToolMaterial("titanium", "Titanium", Rarity.LEGENDARY, 6, 46, 220),
            new ToolMaterial("gemstone", "Gemstone", Rarity.LEGENDARY, 7, 58, 245),
            new ToolMaterial("glacite", "Glacite", Rarity.MYTHIC, 7, 72, 270),
            new ToolMaterial("divan", "Divan", Rarity.DIVINE, 8, 90, 300)
    );

    private record ToolKind(String key, String label, String skill, String blurb) {}

    private static final List<ToolKind> TOOL_KINDS = List.of(
            new ToolKind("pickaxe", "Pickaxe", "mining", "Grants Mining Speed and breaks ores faster."),
            new ToolKind("drill", "Drill", "mining", "A fuel-powered drill. Refuel with Biofuel."),
            new ToolKind("axe", "Axe", "foraging", "Chops logs quickly. Grants Foraging Fortune."),
            new ToolKind("hoe", "Hoe", "farming", "Harvests crops. Grants Farming Fortune."),
            new ToolKind("shovel", "Shovel", "mining", "Digs soil and sand fast."),
            new ToolKind("rod", "Fishing Rod", "fishing", "Catches fish and sea creatures."),
            new ToolKind("sword", "Sword", "combat", "A sturdy blade."),
            new ToolKind("shears", "Shears", "farming", "Shears sheep for wool.")
    );

    private static boolean materialAllowed(String material, String kind) {
        return switch (kind) {
            case "rod" -> material.equals("wood") || material.equals("iron")
                    || material.equals("diamond") || material.equals("netherite");
            case "shears" -> material.equals("iron") || material.equals("diamond") || material.equals("netherite");
            case "drill" -> material.equals("mithril") || material.equals("titanium")
                    || material.equals("gemstone") || material.equals("glacite") || material.equals("divan");
            default -> true;
        };
    }

    private static void registerTools() {
        for (ToolMaterial m : TOOL_MATERIALS) {
            for (ToolKind k : TOOL_KINDS) {
                if (!materialAllowed(m.key(), k.key())) continue;

                ItemDefinition.Builder b = ItemDefinition
                        .builder(m.key() + "_" + k.key(), m.label() + " " + k.label())
                        .category(k.key().equals("sword") ? Category.WEAPON : Category.TOOL)
                        .rarity(m.rarity())
                        .maxStack(1)
                        .tier(m.tier())
                        .toolKind(k.key())
                        .skill(k.skill())
                        .tags(k.key(), m.key(), k.skill())
                        .value(Math.round(20 * Math.pow(4.1, m.tier())))
                        .lore(k.blurb())
                        .reforgable(!k.key().equals("shears"))
                        .enchantable(k.key().equals("sword") || k.key().equals("pickaxe")
                                || k.key().equals("axe") || k.key().equals("rod") || k.key().equals("hoe"));

                switch (k.key()) {
                    case "sword" -> b.stat(PlayerStats.Stat.DAMAGE, m.damage() * 2.1)
                            .stat(PlayerStats.Stat.STRENGTH, m.tier() * 5)
                            .stat(PlayerStats.Stat.CRIT_DAMAGE, m.tier() * 4)
                            .stat(PlayerStats.Stat.CRIT_CHANCE, Math.min(40, m.tier() * 4));
                    case "pickaxe", "drill" -> b.stat(PlayerStats.Stat.MINING_SPEED, m.speed() + m.tier() * 40)
                            .stat(PlayerStats.Stat.MINING_FORTUNE, m.tier() * 4)
                            .stat(PlayerStats.Stat.DAMAGE, m.damage() * 0.6);
                    case "axe" -> b.stat(PlayerStats.Stat.FORAGING_FORTUNE, m.tier() * 6)
                            .stat(PlayerStats.Stat.MINING_SPEED, m.speed() * 0.5)
                            .stat(PlayerStats.Stat.DAMAGE, m.damage() * 0.7);
                    case "hoe" -> b.stat(PlayerStats.Stat.FARMING_FORTUNE, m.tier() * 8)
                            .stat(PlayerStats.Stat.DAMAGE, m.damage() * 0.4);
                    case "rod" -> b.stat(PlayerStats.Stat.SEA_CREATURE_CHANCE, Math.min(45, 5 + m.tier() * 4))
                            .stat(PlayerStats.Stat.DAMAGE, m.damage() * 0.5)
                            .stat(PlayerStats.Stat.STRENGTH, m.tier() * 2);
                    case "shovel" -> b.stat(PlayerStats.Stat.MINING_SPEED, m.speed() * 0.8);
                    case "shears" -> b.stat(PlayerStats.Stat.FARMING_FORTUNE, m.tier() * 3);
                    default -> { }
                }
                register(b);
            }
        }
    }

    // ------------------------------------------------------------------- armor
    private record ArmorSet(String key, String label, Rarity rarity, int tier,
                            double health, double defense, double extra1, String extra1Key,
                            double extra2, String extra2Key) {}

    private static final List<ArmorSet> ARMOR_SETS = List.of(
            new ArmorSet("leather", "Leather", Rarity.COMMON, 1, 20, 5, 0, "", 0, ""),
            new ArmorSet("chainmail", "Chainmail", Rarity.COMMON, 2, 30, 12, 0, "", 0, ""),
            new ArmorSet("iron", "Iron", Rarity.COMMON, 3, 45, 22, 0, "", 0, ""),
            new ArmorSet("gold", "Golden", Rarity.COMMON, 3, 40, 18, 0, "", 0, ""),
            new ArmorSet("diamond", "Diamond", Rarity.RARE, 4, 70, 40, 0, "", 0, ""),
            new ArmorSet("netherite", "Netherite", Rarity.EPIC, 5, 100, 65, 0, "", 0, ""),
            new ArmorSet("farm", "Farm Suit", Rarity.UNCOMMON, 3, 60, 20, 25, "farming_fortune", 0, ""),
            new ArmorSet("miner", "Miner's Outfit", Rarity.UNCOMMON, 3, 70, 30, 25, "mining_speed", 0, ""),
            new ArmorSet("angler", "Angler's Gear", Rarity.UNCOMMON, 3, 65, 28, 8, "sea_creature_chance", 0, ""),
            new ArmorSet("spider", "Spider Slayer Set", Rarity.EPIC, 5, 130, 80, 20, "strength", 0, ""),
            new ArmorSet("revenant", "Revenant Slayer Set", Rarity.EPIC, 6, 170, 105, 30, "health_regen", 0, ""),
            new ArmorSet("ender", "Ender Slayer Set", Rarity.LEGENDARY, 6, 190, 120, 15, "crit_damage", 0, ""),
            new ArmorSet("strong_dragon", "Strong Dragon", Rarity.LEGENDARY, 6, 180, 115, 45, "strength", 0, ""),
            new ArmorSet("superior_dragon", "Superior Dragon", Rarity.LEGENDARY, 7, 210, 140, 35, "strength", 12, "speed"),
            new ArmorSet("wise_dragon", "Wise Dragon", Rarity.LEGENDARY, 6, 175, 110, 175, "intelligence", 0, ""),
            new ArmorSet("young_dragon", "Young Dragon", Rarity.LEGENDARY, 6, 175, 112, 20, "speed", 0, ""),
            new ArmorSet("unstable_dragon", "Unstable Dragon", Rarity.LEGENDARY, 6, 178, 118, 12, "crit_chance", 0, ""),
            new ArmorSet("old_dragon", "Old Dragon", Rarity.LEGENDARY, 6, 230, 150, 30, "defense", 0, ""),
            new ArmorSet("protector_dragon", "Protector Dragon", Rarity.LEGENDARY, 6, 200, 165, 0, "", 0, ""),
            new ArmorSet("necron", "Necron's Plate", Rarity.MYTHIC, 8, 260, 190, 60, "strength", 25, "crit_damage"),
            new ArmorSet("storm", "Storm's Regalia", Rarity.MYTHIC, 8, 240, 170, 500, "intelligence", 0, ""),
            new ArmorSet("goldor", "Goldor's Bulwark", Rarity.MYTHIC, 8, 340, 260, 60, "health_regen", 0, ""),
            new ArmorSet("maxor", "Maxor's Swiftness", Rarity.MYTHIC, 8, 250, 180, 35, "speed", 30, "crit_damage"),
            new ArmorSet("divan", "Divan's Vestments", Rarity.DIVINE, 9, 300, 230, 180, "mining_speed", 45, "mining_fortune")
    );

    private static final String[][] ARMOR_PIECES = {
            {"helmet", "Helmet", "1.0"},
            {"chestplate", "Chestplate", "1.6"},
            {"leggings", "Leggings", "1.4"},
            {"boots", "Boots", "0.8"},
    };

    private static void registerArmor() {
        for (ArmorSet s : ARMOR_SETS) {
            for (String[] p : ARMOR_PIECES) {
                double w = Double.parseDouble(p[2]);
                ItemDefinition.Builder b = ItemDefinition
                        .builder(s.key() + "_" + p[0], s.label() + " " + p[1])
                        .category(Category.ARMOR)
                        .rarity(s.rarity())
                        .maxStack(1)
                        .tier(s.tier())
                        .armorSlot(p[0])
                        .armorSet(s.key())
                        .reforgable(true)
                        .enchantable(true)
                        .tags("armor", p[0], "set:" + s.key())
                        .value(Math.round(40 * Math.pow(3.6, s.tier())))
                        .lore("Part of the " + s.label() + " set.")
                        .stat(PlayerStats.Stat.HEALTH, Math.round(s.health() * w))
                        .stat(PlayerStats.Stat.DEFENSE, Math.round(s.defense() * w));
                if (s.extra1() > 0) {
                    PlayerStats.Stat st = PlayerStats.byKey(s.extra1Key());
                    if (st != null) b.stat(st, Math.round(s.extra1() * w));
                }
                if (s.extra2() > 0) {
                    PlayerStats.Stat st = PlayerStats.byKey(s.extra2Key());
                    if (st != null) b.stat(st, Math.round(s.extra2() * w));
                }
                register(b);
            }
        }
    }

    /** Full-set bonuses applied by the stat engine when all four pieces are worn. */
    public static final Map<String, String> SET_BONUS_TEXT = Map.ofEntries(
            Map.entry("farm", "Crops grow faster on your island."),
            Map.entry("miner", "Gain Mining Fortune while underground."),
            Map.entry("angler", "Sea creatures appear more often."),
            Map.entry("spider", "Deal +25% damage to Spiders."),
            Map.entry("revenant", "Heal 2% of max health on kill."),
            Map.entry("ender", "Gain mana on Enderman kills."),
            Map.entry("superior_dragon", "All stats improved."),
            Map.entry("necron", "Gain 5% damage per Catacombs level."),
            Map.entry("storm", "Ability damage scaled by Intelligence."),
            Map.entry("goldor", "Tank class abilities cost less mana."),
            Map.entry("maxor", "Movement speed increases damage."),
            Map.entry("divan", "Mines gemstone veins instantly.")
    );

    /** Flat stat bonus granted by a complete set. */
    public static Map<PlayerStats.Stat, Double> setBonus(String setKey) {
        Map<PlayerStats.Stat, Double> m = new java.util.EnumMap<>(PlayerStats.Stat.class);
        switch (setKey) {
            case "farm" -> m.put(PlayerStats.Stat.FARMING_FORTUNE, 40.0);
            case "miner" -> m.put(PlayerStats.Stat.MINING_FORTUNE, 30.0);
            case "angler" -> m.put(PlayerStats.Stat.SEA_CREATURE_CHANCE, 12.0);
            case "spider" -> m.put(PlayerStats.Stat.STRENGTH, 25.0);
            case "superior_dragon" -> {
                m.put(PlayerStats.Stat.STRENGTH, 30.0);
                m.put(PlayerStats.Stat.DEFENSE, 30.0);
                m.put(PlayerStats.Stat.HEALTH, 60.0);
                m.put(PlayerStats.Stat.INTELLIGENCE, 60.0);
            }
            case "necron" -> m.put(PlayerStats.Stat.STRENGTH, 40.0);
            case "storm" -> m.put(PlayerStats.Stat.INTELLIGENCE, 400.0);
            case "goldor" -> {
                m.put(PlayerStats.Stat.HEALTH, 200.0);
                m.put(PlayerStats.Stat.DEFENSE, 100.0);
            }
            case "maxor" -> {
                m.put(PlayerStats.Stat.SPEED, 20.0);
                m.put(PlayerStats.Stat.CRIT_DAMAGE, 20.0);
            }
            case "divan" -> {
                m.put(PlayerStats.Stat.MINING_SPEED, 120.0);
                m.put(PlayerStats.Stat.MINING_FORTUNE, 30.0);
            }
            default -> { }
        }
        return m;
    }

    // --------------------------------------------------- signature weapons
    private record SignatureWeapon(String id, String name, Rarity rarity, int tier, long value,
                                   double damage, double strength, double critDamage, double critChance,
                                   double intelligence, double attackSpeed, double health,
                                   double miningSpeed, double miningFortune, double farmingFortune,
                                   String abilityName, int mana, double cooldown, String abilityText,
                                   String... lore) {}

    private static final List<SignatureWeapon> SIGNATURES = List.of(
            new SignatureWeapon("edge_of_the_void", "Edge of the Void", Rarity.EPIC, 5, 45000,
                    105, 20, 15, 0, 60, 0, 0, 0, 0, 0,
                    "Voidstep", 50, 0, "Teleport 8 blocks forward, damaging everything you pass through.",
                    "A blade tempered in the space between worlds."),
            new SignatureWeapon("thiefs_shiv", "Thief's Shiv", Rarity.RARE, 4, 12000,
                    45, 25, 0, 0, 0, 25, 0, 0, 0, 0,
                    "Cheap Shot", 0, 0, "Hits land faster than your enemies can react.",
                    "Stolen from a very surprised merchant."),
            new SignatureWeapon("silentsword", "Silentsword", Rarity.RARE, 4, 20000,
                    60, 10, 40, 20, 0, 0, 0, 0, 0, 0,
                    "Silent Cut", 25, 1, "Your next hit is guaranteed to critically strike.",
                    "It makes no sound. Neither will you."),
            new SignatureWeapon("starforged_blade", "Starforged Blade", Rarity.MYTHIC, 9, 9000000,
                    280, 130, 55, 0, 400, 10, 0, 0, 0, 0,
                    "Supernova", 300, 20, "Channel a collapsing star for massive area damage.",
                    "Forged in the heart of a dying sun.", "The final word in dungeon weaponry."),
            new SignatureWeapon("ember_rod", "Ember Rod", Rarity.EPIC, 6, 300000,
                    90, 0, 0, 0, 180, 0, 0, 0, 0, 0,
                    "Fireball", 40, 3, "Launch a fireball dealing 300% weapon damage plus burn.",
                    "Still warm."),
            new SignatureWeapon("yeti_rod", "Yeti Rod", Rarity.LEGENDARY, 7, 800000,
                    120, 0, 0, 0, 0, 0, 150, 0, 0, 0,
                    "Earth Shout", 150, 30, "Roar, damaging and slowing everything nearby.",
                    "Pulled from the frozen deep."),
            new SignatureWeapon("aspect_of_the_grove", "Aspect of the Grove", Rarity.RARE, 4, 25000,
                    55, 0, 0, 0, 0, 0, 60, 0, 0, 30,
                    "Harvest", 20, 2, "Instantly harvest crops in a 4 block radius.",
                    "The grove provides."),
            new SignatureWeapon("pickaxe_of_the_depths", "Pickaxe of the Depths", Rarity.LEGENDARY, 7, 700000,
                    40, 0, 0, 0, 0, 0, 0, 400, 40, 0,
                    "Veinfinder", 60, 10, "Reveals and instantly breaks the nearest gemstone vein.",
                    "It hums when gemstones are near."),
            new SignatureWeapon("grappling_hook", "Grappling Hook", Rarity.UNCOMMON, 3, 5000,
                    12, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    "Grapple", 10, 2, "Fire a hook to swing across gaps.",
                    "Great for caves. Great for showing off.")
    );

    private static void registerSignatureWeapons() {
        for (SignatureWeapon w : SIGNATURES) {
            ItemDefinition.Builder b = ItemDefinition.builder(w.id(), w.name())
                    .category(Category.WEAPON)
                    .rarity(w.rarity())
                    .maxStack(1)
                    .tier(w.tier())
                    .toolKind("sword")
                    .value(w.value())
                    .tags("weapon", "signature")
                    .reforgable(true)
                    .enchantable(true)
                    .lore(w.lore())
                    .ability(new Ability(w.abilityName(), w.mana(), w.cooldown(), w.abilityText()))
                    .stat(PlayerStats.Stat.DAMAGE, w.damage())
                    .stat(PlayerStats.Stat.STRENGTH, w.strength())
                    .stat(PlayerStats.Stat.CRIT_DAMAGE, w.critDamage())
                    .stat(PlayerStats.Stat.CRIT_CHANCE, w.critChance())
                    .stat(PlayerStats.Stat.INTELLIGENCE, w.intelligence())
                    .stat(PlayerStats.Stat.ATTACK_SPEED, w.attackSpeed())
                    .stat(PlayerStats.Stat.HEALTH, w.health())
                    .stat(PlayerStats.Stat.MINING_SPEED, w.miningSpeed())
                    .stat(PlayerStats.Stat.MINING_FORTUNE, w.miningFortune())
                    .stat(PlayerStats.Stat.FARMING_FORTUNE, w.farmingFortune());
            register(b);
        }
    }

    // ------------------------------------------------------------ dungeon gear
    private record DungeonItem(String id, String name, Rarity rarity, int floor, long value,
                               double damage, double strength, double critDamage, double critChance,
                               double intelligence, double attackSpeed, double health, double defense) {}

    private static final List<DungeonItem> DUNGEON_ITEMS = List.of(
            new DungeonItem("bonzo_staff", "Bonzo Staff", Rarity.RARE, 3, 200000, 90, 0, 0, 0, 130, 0, 0, 0),
            new DungeonItem("dreadlord_sword", "Dreadlord Sword", Rarity.RARE, 4, 900000, 120, 30, 0, 0, 0, 0, 0, 0),
            new DungeonItem("leaping_sword", "Leaping Sword", Rarity.EPIC, 4, 1500000, 145, 40, 0, 0, 0, 0, 0, 0),
            new DungeonItem("spirit_sceptre", "Spirit Sceptre", Rarity.LEGENDARY, 5, 5000000, 130, 0, 0, 0, 260, 0, 0, 0),
            new DungeonItem("silent_death", "Silent Death", Rarity.EPIC, 5, 4000000, 165, 0, 0, 0, 0, 30, 0, 0),
            new DungeonItem("shadow_assassin", "Shadow Assassin", Rarity.LEGENDARY, 6, 18000000, 210, 60, 25, 0, 0, 0, 0, 0),
            new DungeonItem("juju_shortbow", "Juju Shortbow", Rarity.LEGENDARY, 6, 22000000, 190, 0, 40, 20, 0, 0, 0, 0),
            new DungeonItem("flower_of_truth", "Flower of Truth", Rarity.LEGENDARY, 6, 16000000, 200, 70, 0, 0, 0, 0, 80, 0),
            new DungeonItem("yeti_sword", "Yeti Sword", Rarity.LEGENDARY, 6, 14000000, 200, 0, 0, 0, 0, 0, 180, 60),
            new DungeonItem("soul_whip", "Soul Whip", Rarity.LEGENDARY, 6, 20000000, 180, 0, 0, 0, 200, 15, 0, 0),
            new DungeonItem("necromancer_lord", "Necromancer Lord", Rarity.MYTHIC, 7, 90000000, 240, 40, 0, 0, 300, 0, 0, 0),
            new DungeonItem("term_shortbow", "Terminator Shortbow", Rarity.MYTHIC, 7, 120000000, 320, 0, 60, 25, 0, 20, 0, 0),
            new DungeonItem("dark_claymore", "Dark Claymore", Rarity.MYTHIC, 7, 100000000, 260, 90, 30, 0, 0, 0, 0, 0)
    );

    private static void registerDungeonGear() {
        for (DungeonItem d : DUNGEON_ITEMS) {
            register(ItemDefinition.builder("dungeon_" + d.id(), d.name())
                    .category(Category.WEAPON)
                    .rarity(d.rarity())
                    .maxStack(1)
                    .toolKind("sword")
                    .dungeonFloor(d.floor())
                    .value(d.value())
                    .tags("weapon", "dungeon", "floor:" + d.floor())
                    .reforgable(true)
                    .enchantable(true)
                    .lore("Found on Catacombs Floor " + roman(d.floor()) + ".",
                            "Upgrade with Essence at the Dungeon Smith.")
                    .stat(PlayerStats.Stat.DAMAGE, d.damage())
                    .stat(PlayerStats.Stat.STRENGTH, d.strength())
                    .stat(PlayerStats.Stat.CRIT_DAMAGE, d.critDamage())
                    .stat(PlayerStats.Stat.CRIT_CHANCE, d.critChance())
                    .stat(PlayerStats.Stat.INTELLIGENCE, d.intelligence())
                    .stat(PlayerStats.Stat.ATTACK_SPEED, d.attackSpeed())
                    .stat(PlayerStats.Stat.HEALTH, d.health())
                    .stat(PlayerStats.Stat.DEFENSE, d.defense()));
        }
    }

    // ----------------------------------------------------------------- minions
    private record MinionType(String key, String label, String skill, long baseValue) {}

    private static final List<MinionType> MINION_TYPES = List.of(
            new MinionType("oak", "Oak Wood", "foraging", 3),
            new MinionType("spruce", "Spruce Wood", "foraging", 4),
            new MinionType("birch", "Birch Wood", "foraging", 5),
            new MinionType("dark_oak", "Dark Oak Wood", "foraging", 8),
            new MinionType("cobblestone", "Cobblestone", "mining", 3),
            new MinionType("hard_stone", "Hard Stone", "mining", 4),
            new MinionType("coal", "Coal", "mining", 5),
            new MinionType("iron", "Iron", "mining", 12),
            new MinionType("gold", "Gold", "mining", 20),
            new MinionType("diamond", "Diamond", "mining", 45),
            new MinionType("emerald", "Emerald", "mining", 60),
            new MinionType("redstone", "Redstone", "mining", 9),
            new MinionType("lapis", "Lapis", "mining", 9),
            new MinionType("quartz", "Quartz", "mining", 22),
            new MinionType("obsidian", "Obsidian", "mining", 70),
            new MinionType("glowstone", "Glowstone", "mining", 18),
            new MinionType("mithril", "Mithril", "mining", 200),
            new MinionType("titanium", "Titanium", "mining", 700),
            new MinionType("gemstone", "Gemstone", "mining", 900),
            new MinionType("glacite", "Glacite", "mining", 4000),
            new MinionType("wheat", "Wheat", "farming", 3),
            new MinionType("carrot", "Carrot", "farming", 4),
            new MinionType("potato", "Potato", "farming", 4),
            new MinionType("melon", "Melon", "farming", 3),
            new MinionType("pumpkin", "Pumpkin", "farming", 6),
            new MinionType("sugar_cane", "Sugar Cane", "farming", 5),
            new MinionType("cactus", "Cactus", "farming", 5),
            new MinionType("nether_wart", "Nether Wart", "farming", 8),
            new MinionType("cocoa", "Cocoa", "farming", 6),
            new MinionType("mushroom", "Mushroom", "farming", 7),
            new MinionType("cow", "Cow", "farming", 14),
            new MinionType("pig", "Pig", "farming", 14),
            new MinionType("chicken", "Chicken", "farming", 12),
            new MinionType("sheep", "Sheep", "farming", 16),
            new MinionType("rabbit", "Rabbit", "farming", 18),
            new MinionType("fishing", "Fishing", "fishing", 15),
            new MinionType("salmon", "Salmon", "fishing", 22),
            new MinionType("clay", "Clay", "farming", 10),
            new MinionType("zombie", "Zombie", "combat", 8),
            new MinionType("skeleton", "Skeleton", "combat", 10),
            new MinionType("spider", "Spider", "combat", 12),
            new MinionType("creeper", "Creeper", "combat", 14),
            new MinionType("slime", "Slime", "combat", 16),
            new MinionType("enderman", "Enderman", "combat", 40),
            new MinionType("blaze", "Blaze", "combat", 55),
            new MinionType("ghast", "Ghast", "combat", 90),
            new MinionType("revenant", "Revenant", "combat", 2400),
            new MinionType("tarantula", "Tarantula", "combat", 3000),
            new MinionType("inferno", "Inferno", "combat", 12000),
            new MinionType("snow", "Snow", "mining", 4),
            new MinionType("ice", "Ice", "mining", 6)
    );

    public static final int MAX_MINION_TIER = 12;

    private static void registerMinions() {
        for (MinionType t : MINION_TYPES) {
            for (int tier = 1; tier <= MAX_MINION_TIER; tier++) {
                Rarity r = tier >= 11 ? Rarity.MYTHIC : tier >= 9 ? Rarity.LEGENDARY
                        : tier >= 6 ? Rarity.EPIC : tier >= 3 ? Rarity.RARE : Rarity.UNCOMMON;
                register(ItemDefinition.builder("minion_" + t.key() + "_" + tier,
                                t.label() + " Minion " + roman(tier))
                        .category(Category.MINION)
                        .rarity(r)
                        .maxStack(1)
                        .minion(new MinionInfo(t.key(), tier, t.skill()))
                        .value(Math.round(t.baseValue() * Math.pow(2.35, tier)))
                        .tags("minion", t.skill())
                        .lore("Tier " + roman(tier) + " " + t.label() + " Minion.",
                                "Place it on your island to generate resources."));
            }
        }
    }

    // -------------------------------------------------------------------- pets
    private record PetType(String key, String name, Rarity rarity, String skill,
                           double stat1, String key1, double stat2, String key2,
                           double stat3, String key3) {}

    private static final List<PetType> PETS = List.of(
            new PetType("rabbit", "Rabbit", Rarity.COMMON, "farming", 8, "farming_fortune", 20, "health", 0, ""),
            new PetType("chicken", "Chicken", Rarity.COMMON, "farming", 10, "farming_fortune", 3, "strength", 0, ""),
            new PetType("cow", "Cow", Rarity.UNCOMMON, "farming", 15, "farming_fortune", 40, "health", 0, ""),
            new PetType("sheep", "Sheep", Rarity.RARE, "farming", 22, "farming_fortune", 40, "intelligence", 0, ""),
            new PetType("elephant", "Elephant", Rarity.EPIC, "farming", 35, "farming_fortune", 15, "strength", 0, ""),
            new PetType("mooshroom_cow", "Mooshroom Cow", Rarity.LEGENDARY, "farming", 50, "farming_fortune", 120, "health", 0, ""),
            new PetType("polar_bear", "Polar Bear", Rarity.EPIC, "farming", 30, "farming_fortune", 60, "defense", 0, ""),
            new PetType("pig", "Pig", Rarity.COMMON, "mining", 12, "mining_speed", 30, "health", 0, ""),
            new PetType("mole", "Mole", Rarity.UNCOMMON, "mining", 20, "mining_speed", 6, "mining_fortune", 0, ""),
            new PetType("armadillo", "Armadillo", Rarity.RARE, "mining", 28, "mining_speed", 70, "defense", 0, ""),
            new PetType("wither_skeleton", "Wither Skeleton", Rarity.EPIC, "mining", 35, "mining_speed", 12, "mining_fortune", 15, "strength"),
            new PetType("divan_miner", "Divan Miner", Rarity.MYTHIC, "mining", 60, "mining_speed", 25, "mining_fortune", 100, "defense"),
            new PetType("bat", "Bat", Rarity.RARE, "combat", 80, "intelligence", 5, "speed", 0, ""),
            new PetType("enderman_pet", "Enderman", Rarity.EPIC, "combat", 20, "crit_damage", 90, "health", 20, "strength"),
            new PetType("ender_dragon", "Ender Dragon", Rarity.LEGENDARY, "combat", 40, "strength", 30, "crit_damage", 10, "crit_chance"),
            new PetType("tarantula_pet", "Tarantula", Rarity.LEGENDARY, "combat", 25, "crit_damage", 20, "attack_speed", 10, "strength"),
            new PetType("golden_dragon", "Golden Dragon", Rarity.LEGENDARY, "combat", 60, "strength", 40, "crit_damage", 15, "crit_chance"),
            new PetType("baby_yeti", "Baby Yeti", Rarity.LEGENDARY, "combat", 130, "defense", 160, "health", 25, "strength"),
            new PetType("blue_whale", "Blue Whale", Rarity.LEGENDARY, "fishing", 250, "health", 60, "defense", 0, ""),
            new PetType("dolphin", "Dolphin", Rarity.EPIC, "fishing", 12, "sea_creature_chance", 60, "intelligence", 0, ""),
            new PetType("squid", "Squid", Rarity.RARE, "fishing", 8, "sea_creature_chance", 40, "intelligence", 0, ""),
            new PetType("ocelot", "Ocelot", Rarity.RARE, "foraging", 12, "speed", 12, "foraging_fortune", 0, ""),
            new PetType("monkey", "Monkey", Rarity.EPIC, "foraging", 22, "foraging_fortune", 8, "speed", 50, "intelligence"),
            new PetType("silverfish", "Silverfish", Rarity.COMMON, "mining", 10, "mining_speed", 20, "defense", 0, ""),
            new PetType("wither_skull", "Wither Skull", Rarity.MYTHIC, "combat", 200, "health", 120, "defense", 30, "strength")
    );

    private static void registerPets() {
        for (PetType p : PETS) {
            Map<PlayerStats.Stat, Double> base = new java.util.EnumMap<>(PlayerStats.Stat.class);
            putIf(base, p.key1(), p.stat1());
            putIf(base, p.key2(), p.stat2());
            putIf(base, p.key3(), p.stat3());

            register(ItemDefinition.builder("pet_" + p.key(), p.name() + " (Pet)")
                    .category(Category.PET)
                    .rarity(p.rarity())
                    .maxStack(1)
                    .pet(new PetInfo(p.key(), p.skill(), Collections.unmodifiableMap(base)))
                    .value(25000)
                    .tags("pet", p.skill())
                    .lore("A " + p.name().toLowerCase(Locale.ROOT) + " pet. Levels with " + p.skill() + " XP."));

            for (Rarity r : new Rarity[]{Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY}) {
                register(ItemDefinition.builder("pet_item_" + p.key() + "_" + r.name().toLowerCase(Locale.ROOT),
                                p.name() + " Pet Core (" + r.label() + ")")
                        .category(Category.PET_ITEM)
                        .rarity(r)
                        .maxStack(1)
                        .value(5000)
                        .tags("pet_item")
                        .lore("Upgrade a pet's rarity with this core."));
            }
        }
    }

    private static void putIf(Map<PlayerStats.Stat, Double> map, String key, double value) {
        if (key == null || key.isEmpty() || value == 0) return;
        PlayerStats.Stat s = PlayerStats.byKey(key);
        if (s != null) map.put(s, value);
    }

    // ------------------------------------------------------------- consumables
    private record Food(String id, String name, long value, double health, double extra, String extraKey) {}

    private static final List<Food> FOODS = List.of(
            new Food("bread", "Bread", 8, 4, 0, ""),
            new Food("cooked_beef", "Steak", 18, 8, 1, "strength"),
            new Food("cooked_porkchop", "Cooked Porkchop", 18, 8, 0, ""),
            new Food("cooked_chicken", "Cooked Chicken", 16, 7, 0, ""),
            new Food("golden_carrot", "Golden Carrot", 60, 12, 3, "farming_fortune"),
            new Food("enchanted_golden_carrot", "Enchanted Golden Carrot", 3000, 30, 12, "farming_fortune"),
            new Food("mushroom_stew", "Mushroom Stew", 25, 10, 0, ""),
            new Food("pumpkin_pie", "Pumpkin Pie", 30, 14, 0, ""),
            new Food("cake_slice", "Slice of Cake", 90, 25, 2, "speed"),
            new Food("enchanted_cookie", "Enchanted Cookie", 5200, 45, 5, "speed")
    );

    private record Potion(String id, String name, long value, double amount, String statKey, int duration) {}

    private static final List<Potion> POTIONS = List.of(
            new Potion("speed", "Speed Potion", 200, 20, "speed", 300),
            new Potion("strength", "Strength Potion", 260, 15, "strength", 300),
            new Potion("healing", "Healing Potion", 240, 60, "health_regen", 300),
            new Potion("mana", "Mana Potion", 220, 30, "intelligence", 300),
            new Potion("archery", "Archery Potion", 280, 20, "crit_damage", 300),
            new Potion("mining_speed", "Mining Speed Potion", 300, 60, "mining_speed", 300),
            new Potion("farming", "Farming Potion", 300, 25, "farming_fortune", 300),
            new Potion("fishing", "Fishing Potion", 300, 15, "sea_creature_chance", 300),
            new Potion("wither", "Wither Potion", 900, 40, "strength", 600),
            new Potion("titan", "Titan Potion", 1400, 200, "health", 600),
            new Potion("spirit", "Spirit Potion", 1600, 30, "speed", 600)
    );

    private static void registerConsumables() {
        for (Food f : FOODS) {
            ItemDefinition.Builder b = ItemDefinition.builder(f.id(), f.name())
                    .category(Category.FOOD)
                    .rarity(f.value() > 1000 ? Rarity.RARE : Rarity.UNCOMMON)
                    .maxStack(64)
                    .value(f.value())
                    .consumable(true)
                    .tags("food")
                    .lore("Eat to restore health and gain a temporary buff.")
                    .stat(PlayerStats.Stat.HEALTH, f.health());
            if (f.extra() > 0) {
                PlayerStats.Stat s = PlayerStats.byKey(f.extraKey());
                if (s != null) b.stat(s, f.extra());
            }
            register(b);
        }
        for (Potion p : POTIONS) {
            PlayerStats.Stat s = PlayerStats.byKey(p.statKey());
            register(ItemDefinition.builder("potion_" + p.id(), p.name())
                    .category(Category.CONSUMABLE)
                    .rarity(p.value() > 800 ? Rarity.RARE : Rarity.UNCOMMON)
                    .maxStack(16)
                    .value(p.value())
                    .consumable(true)
                    .tags("potion", "alchemy")
                    .lore("Brewed at a Brewing Stand. Lasts " + (p.duration() / 60) + " minutes.")
                    .stat(s, p.amount()));
        }
    }

    // ------------------------------------------------------------- accessories
    private record Accessory(String key, String name, Rarity rarity, double amount, String statKey, String note) {}

    private static final List<Accessory> ACCESSORIES = List.of(
            new Accessory("speed_talisman", "Speed Talisman", Rarity.UNCOMMON, 2, "speed", null),
            new Accessory("health_talisman", "Health Talisman", Rarity.UNCOMMON, 15, "health", null),
            new Accessory("defense_talisman", "Defense Talisman", Rarity.UNCOMMON, 8, "defense", null),
            new Accessory("strength_talisman", "Strength Talisman", Rarity.UNCOMMON, 2, "strength", null),
            new Accessory("mining_talisman", "Mining Talisman", Rarity.RARE, 6, "mining_speed", null),
            new Accessory("farming_talisman", "Farming Talisman", Rarity.RARE, 5, "farming_fortune", null),
            new Accessory("fishing_talisman", "Fishing Talisman", Rarity.RARE, 3, "sea_creature_chance", null),
            new Accessory("critical_talisman", "Critical Talisman", Rarity.RARE, 4, "crit_chance", null),
            new Accessory("speed_ring", "Speed Ring", Rarity.RARE, 3, "speed", null),
            new Accessory("health_ring", "Health Ring", Rarity.RARE, 30, "health", null),
            new Accessory("defense_ring", "Defense Ring", Rarity.RARE, 15, "defense", null),
            new Accessory("strength_ring", "Strength Ring", Rarity.RARE, 4, "strength", null),
            new Accessory("mining_ring", "Mining Ring", Rarity.EPIC, 10, "mining_speed", null),
            new Accessory("critical_ring", "Critical Ring", Rarity.EPIC, 6, "crit_chance", null),
            new Accessory("speed_artifact", "Speed Artifact", Rarity.EPIC, 5, "speed", null),
            new Accessory("health_artifact", "Health Artifact", Rarity.EPIC, 60, "health", null),
            new Accessory("defense_artifact", "Defense Artifact", Rarity.EPIC, 25, "defense", null),
            new Accessory("strength_artifact", "Strength Artifact", Rarity.EPIC, 7, "strength", null),
            new Accessory("mining_artifact", "Mining Artifact", Rarity.LEGENDARY, 16, "mining_speed", null),
            new Accessory("critical_artifact", "Critical Artifact", Rarity.LEGENDARY, 9, "crit_chance", null),
            new Accessory("bat_artifact", "Bat Artifact", Rarity.LEGENDARY, 120, "intelligence", null),
            new Accessory("treasure_artifact", "Treasure Artifact", Rarity.MYTHIC, 12, "magic_find", null),
            new Accessory("hegemony", "Hegemony Artifact", Rarity.MYTHIC, 12, "strength", "Only the strongest stat of your best accessory applies."),
            new Accessory("wither_artifact", "Wither Artifact", Rarity.MYTHIC, 20, "strength", null),
            new Accessory("auto_recombobulator", "Auto-Recombobulator", Rarity.LEGENDARY, 0, "",
                    "Dungeon rewards are automatically upgraded one rarity.")
    );

    private static void registerAccessories() {
        for (Accessory a : ACCESSORIES) {
            PlayerStats.Stat s = PlayerStats.byKey(a.statKey());
            long value = switch (a.rarity()) {
                case MYTHIC -> 2_000_000;
                case LEGENDARY -> 600_000;
                case EPIC -> 150_000;
                case RARE -> 40_000;
                default -> 10_000;
            };
            ItemDefinition.Builder b = ItemDefinition.builder("accessory_" + a.key(), a.name())
                    .category(Category.ACCESSORY)
                    .rarity(a.rarity())
                    .maxStack(1)
                    .value(value)
                    .reforgable(true)
                    .tags("accessory")
                    .lore(a.note() != null ? a.note() : "Wear in your accessory bag to gain its stats.");
            if (s != null) b.stat(s, a.amount());
            if (a.key().equals("auto_recombobulator")) {
                b.ability(new Ability("Auto-Recombobulate", 0, 0,
                        "Dungeon rewards are automatically upgraded one rarity."));
            }
            register(b);
        }
    }

    // ------------------------------------------------------------------ helpers
    public static String roman(int n) {
        int[] vals = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] syms = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.length; i++) {
            while (n >= vals[i]) {
                sb.append(syms[i]);
                n -= vals[i];
            }
        }
        return sb.toString();
    }
}
