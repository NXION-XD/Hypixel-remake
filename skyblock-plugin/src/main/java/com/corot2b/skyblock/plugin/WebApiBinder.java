package com.corot2b.skyblock.plugin;

import com.corot2b.core.content.ItemDefinition;
import com.corot2b.core.content.ItemRegistry;
import com.corot2b.core.skills.SkillService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Starts the embedded HTTP API that the admin dashboard talks to.
 *
 * <p>Runs on Javalin's own thread pool, never on the main thread. Reads that touch
 * player data go through the async profile repository, so a slow database cannot
 * stall the server tick.
 */
final class WebApiBinder {

    private static Javalin app;

    private WebApiBinder() {}

    static void start(SkyBlockPlugin plugin) {
        String host = plugin.getConfig().getString("web.api.host", "0.0.0.0");
        int port = plugin.getConfig().getInt("web.api.port", 8090);
        String token = plugin.getConfig().getString("web.api.auth.token", "");

        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.http.defaultContentType = "application/json";
        });

        // Public health/metadata — safe to expose, used by uptime monitors.
        app.get("/api/health", ctx -> ctx.json(Map.of(
                "status", "ok",
                "plugin", "SkyBlock",
                "version", plugin.getDescription().getVersion(),
                "players", plugin.getServer().getOnlinePlayers().size(),
                "tps", tps(plugin),
                "itemTypes", ItemRegistry.size(),
                "dirtyProfiles", plugin.profiles().dirtyCount())));

        // Everything below requires the bearer token.
        app.before("/api/admin/*", ctx -> requireToken(ctx, token));

        app.get("/api/admin/items", ctx -> ctx.json(itemsPage(ctx)));

        app.get("/api/admin/items/{id}", ctx -> {
            String id = ctx.pathParam("id");
            ItemDefinition def = ItemRegistry.get(id).orElse(null);
            if (def == null) {
                ctx.status(404).json(Map.of("error", "unknown item", "id", id));
                return;
            }
            ctx.json(describe(def));
        });

        app.get("/api/admin/skills", ctx -> {
            Map<String, Object> out = new LinkedHashMap<>();
            SkillService.DISPLAY.forEach((key, name) -> out.put(key, Map.of(
                    "name", name,
                    "cap", SkillService.cap(key),
                    "totalXp", SkillService.totalXpFor(SkillService.cap(key)))));
            ctx.json(out);
        });

        app.get("/api/admin/profiles/{id}", ctx -> {
            UUID profileId;
            try {
                profileId = UUID.fromString(ctx.pathParam("id"));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", "profile id must be a UUID"));
                return;
            }
            // Bridges the async repository onto Javalin's future support.
            ctx.future(plugin.profiles().load(profileId).thenAccept(profile -> {
                if (profile == null) {
                    ctx.status(404).json(Map.of("error", "no such profile"));
                    return;
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("profileId", profile.profileId().toString());
                body.put("name", profile.name());
                body.put("coins", profile.coinsCents());
                body.put("bank", profile.bankCents());
                body.put("skillAverage", SkillService.skillAverage(profile));
                Map<String, Integer> levels = new LinkedHashMap<>();
                for (String skill : SkillService.DISPLAY.keySet()) {
                    levels.put(skill, SkillService.levelFromXp(profile.skillXp(skill), skill).level());
                }
                body.put("skills", levels);
                ctx.json(body);
            }));
        });

        app.start(host, port);
        plugin.getLogger().info("Web API listening on " + host + ":" + port);
    }

    static void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    private static void requireToken(Context ctx, String token) {
        if (token == null || token.isBlank()) {
            ctx.status(503).json(Map.of("error", "web api is not configured with a token"));
            return;
        }
        String header = ctx.header("Authorization");
        if (header == null || !header.equals("Bearer " + token)) {
            ctx.status(401).json(Map.of("error", "unauthorized"));
        }
    }

    /** Paginated item catalogue so the dashboard can render 1,000+ items cheaply. */
    private static Map<String, Object> itemsPage(Context ctx) {
        int page = Math.max(1, intParam(ctx, "page", 1));
        int size = Math.min(200, Math.max(1, intParam(ctx, "size", 50)));
        String category = ctx.queryParam("category");

        List<ItemDefinition> all = category == null
                ? List.copyOf(ItemRegistry.all())
                : ItemRegistry.byCategory(ItemDefinition.Category.valueOf(category.toUpperCase()));

        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());

        List<Map<String, Object>> rows = all.subList(from, to).stream()
                .map(WebApiBinder::describe)
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("page", page);
        out.put("size", size);
        out.put("total", all.size());
        out.put("pages", (int) Math.ceil(all.size() / (double) size));
        out.put("items", rows);
        return out;
    }

    private static Map<String, Object> describe(ItemDefinition def) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", def.id());
        m.put("name", def.name());
        m.put("category", def.category().name().toLowerCase());
        m.put("rarity", def.rarity().name().toLowerCase());
        m.put("value", def.npcValue());
        m.put("maxStack", def.maxStack());
        m.put("stats", def.stats());
        m.put("lore", def.lore());
        if (def.hasAbility()) {
            m.put("ability", Map.of(
                    "name", def.ability().name(),
                    "mana", def.ability().manaCost(),
                    "cooldown", def.ability().cooldownSeconds(),
                    "text", def.ability().description()));
        }
        if (def.minion() != null) {
            m.put("minion", Map.of("type", def.minion().type(), "tier", def.minion().tier()));
        }
        return m;
    }

    private static int intParam(Context ctx, String name, int fallback) {
        try {
            String v = ctx.queryParam(name);
            return v == null ? fallback : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double tps(SkyBlockPlugin plugin) {
        try {
            double[] t = plugin.getServer().getTPS();
            return t.length > 0 ? Math.round(t[0] * 100) / 100.0 : 20.0;
        } catch (Throwable t) {
            return 20.0;
        }
    }
}
