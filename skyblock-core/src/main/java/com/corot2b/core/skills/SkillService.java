package com.corot2b.core.skills;

import com.corot2b.api.model.SkyBlockProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill XP curve, level resolution and per-level rewards.
 *
 * <p>The XP table below is the standard 1-60 progression (levels 21-44 step by
 * 100k, 45-50 accelerate, 51-60 step by 300k). It is plain numeric data.
 */
public final class SkillService {

    private static final long[] XP = {
            50, 125, 200, 300, 500, 750, 1_000, 1_500, 2_000, 3_500,
            5_000, 7_500, 10_000, 15_000, 20_000, 30_000, 50_000, 75_000, 100_000, 200_000,
            300_000, 400_000, 500_000, 600_000, 700_000, 800_000, 900_000, 1_000_000, 1_100_000, 1_200_000,
            1_300_000, 1_400_000, 1_500_000, 1_600_000, 1_700_000, 1_800_000, 1_900_000, 2_000_000, 2_100_000, 2_200_000,
            2_300_000, 2_400_000, 2_500_000, 2_600_000, 2_750_000, 2_900_000, 3_100_000, 3_400_000, 3_700_000, 4_000_000,
            4_300_000, 4_600_000, 4_900_000, 5_200_000, 5_500_000, 5_800_000, 6_100_000, 6_400_000, 6_700_000, 7_000_000
    };

    public static final int ABSOLUTE_MAX = XP.length; // 60

    /** Skills and their level caps. Social/Runecrafting/Dungeoneering cap lower. */
    public static final Map<String, Integer> CAPS = new LinkedHashMap<>();

    static {
        CAPS.put("farming", 60);
        CAPS.put("mining", 60);
        CAPS.put("combat", 60);
        CAPS.put("foraging", 50);
        CAPS.put("fishing", 50);
        CAPS.put("enchanting", 50);
        CAPS.put("alchemy", 50);
        CAPS.put("carpentry", 50);
        CAPS.put("taming", 50);
        CAPS.put("runecrafting", 25);
        CAPS.put("social", 25);
        CAPS.put("catacombs", 50);
    }

    /** Display names, in the order the Skills menu lists them. */
    public static final Map<String, String> DISPLAY = new LinkedHashMap<>();

    static {
        DISPLAY.put("farming", "Farming");
        DISPLAY.put("mining", "Mining");
        DISPLAY.put("combat", "Combat");
        DISPLAY.put("foraging", "Foraging");
        DISPLAY.put("fishing", "Fishing");
        DISPLAY.put("enchanting", "Enchanting");
        DISPLAY.put("alchemy", "Alchemy");
        DISPLAY.put("carpentry", "Carpentry");
        DISPLAY.put("runecrafting", "Runecrafting");
        DISPLAY.put("taming", "Taming");
        DISPLAY.put("social", "Social");
        DISPLAY.put("catacombs", "Dungeoneering");
    }

    private SkillService() {}

    public static long xpToReach(int level) {
        if (level < 1) return 0;
        if (level > ABSOLUTE_MAX) return Long.MAX_VALUE;
        return XP[level - 1];
    }

    public static int cap(String skill) {
        return CAPS.getOrDefault(skill.toLowerCase(), 50);
    }

    public static long totalXpFor(int maxLevel) {
        long sum = 0;
        for (int l = 1; l <= Math.min(maxLevel, ABSOLUTE_MAX); l++) sum += xpToReach(l);
        return sum;
    }

    /** Resolves an XP amount into a level plus progress towards the next one. */
    public static LevelInfo levelFromXp(long xp, int cap) {
        if (xp < 0) xp = 0;
        int max = Math.min(cap, ABSOLUTE_MAX);
        int level = 0;
        long acc = 0;
        for (int l = 1; l <= max; l++) {
            long need = xpToReach(l);
            if (acc + need > xp) break;
            acc += need;
            level = l;
        }
        boolean maxed = level >= max;
        long need = maxed ? 0 : xpToReach(level + 1);
        long into = xp - acc;
        double pct = maxed || need == 0 ? 1.0 : (double) into / need;
        return new LevelInfo(level, into, need, acc, maxed, pct);
    }

    public static LevelInfo levelFromXp(long xp, String skill) {
        return levelFromXp(xp, cap(skill));
    }

    public record LevelInfo(int level, long xpIntoLevel, long xpForNext, long xpTotal,
                            boolean maxed, double percent) {}

    /**
     * Awards XP and reports every level crossed so the caller can grant rewards,
     * fire events and announce level-ups. Wisdom is applied here so no caller can
     * forget it.
     */
    public static Award award(SkyBlockProfile profile, String skill, double baseXp, double wisdomPercent) {
        String key = skill.toLowerCase();
        int cap = cap(key);
        LevelInfo before = levelFromXp(profile.skillXp(key), cap);

        double w = Math.max(0, Math.min(200, wisdomPercent)) / 100.0;
        long gained = Math.max(0, Math.round(baseXp * (1 + w)));
        profile.skillXp(key, profile.skillXp(key) + gained);

        LevelInfo after = levelFromXp(profile.skillXp(key), cap);
        List<Integer> levels = new ArrayList<>();
        for (int l = before.level() + 1; l <= after.level(); l++) levels.add(l);
        return new Award(before.level(), after.level(), levels, gained, after);
    }

    public record Award(int before, int after, List<Integer> levelsCrossed, long xpGained, LevelInfo info) {
        public boolean leveledUp() { return !levelsCrossed.isEmpty(); }
    }

    /**
     * Skill average excludes Social, Runecrafting and Dungeoneering — the same set
     * the game excludes from its displayed average.
     */
    public static double skillAverage(SkyBlockProfile profile) {
        double sum = 0;
        int count = 0;
        for (String skill : CAPS.keySet()) {
            if (skill.equals("social") || skill.equals("runecrafting") || skill.equals("catacombs")) continue;
            sum += levelFromXp(profile.skillXp(skill), skill).level();
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    /** Stat bonuses granted by skill levels. */
    public static Map<String, Double> statBonuses(SkyBlockProfile profile) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String skill : CAPS.keySet()) {
            int lvl = levelFromXp(profile.skillXp(skill), skill).level();
            if (lvl == 0) continue;
            switch (skill) {
                case "farming" -> add(out, "farming_fortune", lvl * 4.0);
                case "mining" -> add(out, "defense", lvl);
                case "combat" -> {
                    add(out, "crit_damage", lvl * 4.0);
                    add(out, "damage_percent", lvl * 0.5);
                }
                case "foraging" -> add(out, "strength", lvl);
                case "fishing" -> {
                    add(out, "defense", lvl);
                    add(out, "health", lvl * 2.0);
                }
                case "enchanting", "alchemy" -> add(out, "intelligence", lvl);
                case "taming" -> add(out, "pet_luck", lvl);
                default -> { }
            }
        }
        return out;
    }

    private static void add(Map<String, Double> map, String key, double v) {
        map.merge(key, v, Double::sum);
    }

    /** Coins awarded per skill level-up (flat, as in the live game). */
    public static long coinsReward(int newLevel) {
        return 25L * newLevel;
    }
}
