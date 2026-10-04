package com.corot2b.skyblock.plugin;

import com.corot2b.core.content.ItemRegistry;
import com.corot2b.core.skills.SkillService;
import com.corot2b.core.storage.Database;
import com.corot2b.core.storage.MigrationRunner;
import com.corot2b.core.storage.ProfileRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Plugin entry point. Owns the startup/shutdown order, which matters:
 *
 * <pre>
 *   enable : config -> database -> migrations -> repositories -> services -> commands -> listeners -> web
 *   disable: web -> listeners -> flush profiles (bounded) -> close database
 * </pre>
 *
 * <p>Anything that can fail in a way that would leave players with lost progress
 * (database, migrations) fails the enable hard and disables the plugin, rather than
 * half-starting.
 */
public final class SkyBlockPlugin extends JavaPlugin {

    private Database database;
    private ProfileRepository profiles;
    private MigrationRunner migrations;

    private long enableStartedAt;

    @Override
    public void onEnable() {
        enableStartedAt = System.currentTimeMillis();
        saveDefaultConfig();

        getLogger().info("SkyBlock starting — content: " + ItemRegistry.size() + " item types, "
                + SkillService.CAPS.size() + " skills");

        try {
            initDatabase();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Database initialisation failed; disabling plugin. "
                    + "No player data will be touched until this is fixed.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Order matters: repositories before services, services before commands.
        this.profiles = new ProfileRepository(
                database,
                getLogger(),
                getConfig().getInt("database.save.batch-size", 64),
                getConfig().getInt("database.save.interval-seconds", 60));

        registerCommands();
        registerListeners();
        startWebApi();

        getLogger().info("SkyBlock enabled in " + (System.currentTimeMillis() - enableStartedAt) + "ms");
    }

    private void initDatabase() throws Exception {
        String type = getConfig().getString("database.type", "postgres").toLowerCase();
        String host = getConfig().getString("database.host", "127.0.0.1");
        int port = getConfig().getInt("database.port", type.equals("mysql") ? 3306 : 5432);
        String name = getConfig().getString("database.name", "skyblock");
        String user = getConfig().getString("database.user", "skyblock");
        String pass = getConfig().getString("database.password", "");

        Database.Config cfg = type.equals("mysql")
                ? Database.Config.mysql(host, port, name, user, pass)
                : Database.Config.postgres(host, port, name, user, pass);

        this.database = new Database(cfg, getLogger());
        this.migrations = new MigrationRunner(database, getLogger());
        migrations.run();
    }

    private void registerCommands() {
        // Command classes live in the gameplay/gui modules and register themselves
        // against the Bukkit command map here, keeping this class free of gameplay code.
        CommandBinder.bind(this);
    }

    private void registerListeners() {
        ListenerBinder.bind(this);
    }

    private void startWebApi() {
        if (!getConfig().getBoolean("web.api.enabled", false)) return;
        WebApiBinder.start(this);
    }

    @Override
    public void onDisable() {
        getLogger().info("SkyBlock shutting down — flushing profiles...");
        if (profiles != null) {
            profiles.shutdown(getConfig().getInt("database.save.shutdown-timeout-seconds", 30));
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("SkyBlock disabled cleanly.");
    }

    // ------------------------------------------------------------------ accessors
    public Database database() { return database; }
    public ProfileRepository profiles() { return profiles; }
    public MigrationRunner migrations() { return migrations; }

    /** Single accessor for other modules so nothing holds a stale reference. */
    public static SkyBlockPlugin get() {
        return getPlugin(SkyBlockPlugin.class);
    }
}
