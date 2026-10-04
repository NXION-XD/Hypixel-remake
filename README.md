# SkyBlock Remake — Paper/Purpur 1.21.9+

A modular SkyBlock server for modern Paper. Java 21, Maven multi-module, PostgreSQL/MySQL,
Redis-ready, with an embedded HTTP API for an admin dashboard.

**All code, item names, NPC dialogue and lore in this repository are original.** It is an
independent implementation of SkyBlock-style mechanics — it contains no copied text,
assets or code from Hypixel.

---

## Status — read this first

This is a **working foundation, not a finished clone.** Be clear-eyed about the split:

| Layer | State |
|---|---|
| Maven multi-module build (8 modules) | Written, **not compiled** — see below |
| Item registry (~1,100 item types, in Java) | Complete |
| Skill XP curve (1–60), level maths, rewards | Complete |
| Stat aggregation (skills → armor → sets → weapon → accessories → pet → buffs) | Complete |
| Damage model (melee, crit, defense reduction, ability, ferocity, fortune) | Complete |
| Persistence (Hikari pool, dialect-aware, async batched saves, optimistic locking, migrations) | Complete |
| PostgreSQL schema + migration runner with checksum verification | Complete |
| Plugin lifecycle, commands (`/skyblock`, `/skills`, `/skillsadmin`), join/quit profile lifecycle | Complete |
| Item ⇄ ItemStack serialisation via PersistentDataContainer | Complete |
| Embedded web API (health, item catalogue, skills, profile lookup, bearer auth) | Complete |
| Economy (AH, bazaar order book, trading, taxes, audit log) | **Schema only** — SQL written, Java services not |
| GUI/menu framework and the ~15 player menus | **Not started** |
| NPCs, dialogue engine, quest chains | **Not started** |
| Islands, minions, pets, farming, fishing, mining commissions/forge | **Modelled, not wired** |
| Dungeons (parties, classes, rooms, bosses, scoring) | **Module + schema only** |
| Admin dashboard web app | **Not started** (the API it would consume exists) |
| Unit tests | **Not started** |

### Not compiled

There is no JDK, Maven, or access to Maven Central in the environment this was written in, so
**none of this Java has been through `javac`.** Treat it as reviewed-but-unbuilt. Expect a
handful of import and signature fixes on first build.

What *was* machine-checked: brace balance and type-name consistency across all 21 files
(a string/comment-aware scanner), filename↔type matching, and resolution of every
`com.corot2b.*` reference. All pass.

---

## Build

Requires **JDK 21+** and **Maven 3.9+**.

```bash
mvn clean package
# -> skyblock-plugin/target/SkyBlock-1.0.0.jar
```

Copy that jar into `plugins/`, then:

1. Start the server once to generate `plugins/SkyBlock/config.yml`.
2. Create the database and user:
   ```sql
   CREATE DATABASE skyblock;
   CREATE USER skyblock WITH PASSWORD 'change-me';
   GRANT ALL PRIVILEGES ON DATABASE skyblock TO skyblock;
   ```
3. Put credentials in `config.yml`. Migrations apply automatically on first enable.
4. Set `web.api.auth.token` before exposing the HTTP API.

To use MySQL instead, set `database.type: mysql` and add
`db/migrations/mysql/V1__init.sql` (the Postgres version is the reference; the
MySQL translation is not written yet — the runner already selects by dialect).

---

## Layout

```
pom.xml                     parent, dependency + plugin management
skyblock-api/               model: Rarity, ItemInstance, PlayerStats, PetInstance, SkyBlockProfile
skyblock-core/              content registry, skills, stats, combat maths, storage
skyblock-economy/           (schema exists; services pending)
skyblock-gameplay/          (pending)
skyblock-dungeons/          (pending)
skyblock-gui/               (pending)
skyblock-webapi/            (bridge currently lives in skyblock-plugin)
skyblock-plugin/            distribution: plugin.yml, main class, command/listener/web binders
db/migrations/postgres/     V1__init.sql
```

Dependency direction is strict: `api ← core ← {economy, gameplay, dungeons, gui, webapi} ← plugin`.

---

## Design notes

**Content is code, not data files.** Every item is declared in `ItemRegistry` and expanded
from compact tier tables. A typo in a stat key or an unknown rarity is a compile error
rather than a silent runtime default. Operators tune balance (drop rates, taxes, XP) in
`config.yml`; they do not re-author the catalogue.

**No JDBC on the main thread.** `ProfileRepository.load()` returns a `CompletableFuture`
completed on the IO pool. Saves are queued and flushed in batches on an interval, on quit,
and again during `onDisable` under a bounded deadline.

**Writes are optimistic-locked.** `UPDATE ... WHERE version = ?` and bump. If another
server already wrote, the update matches zero rows and the profile is reloaded instead of
clobbered — which is what makes sharing one database across servers safe.

**Money is `BIGINT` cents.** No floats anywhere near coin arithmetic.

**Migrations are checksummed.** Editing a migration that already ran fails startup instead
of silently diverging two servers.

---

## Damage model

```
melee   = (5 + weaponDamage)
        × (1 + strength / 5)
        × (1 + critDamage / 100)        [on crit — base 30% chance, 50% damage]
        × (1 + damagePercent / 100)
        × Π multipliers
        × (1 − defense / (defense + 100))

ehp     = health × (1 + defense / 100)
ability = base × (1 + intelligence / 100)
fortune = 1 extra drop per 100 fortune, fractional part rolled
```

Implemented in `skyblock-core/.../combat/DamageCalculator.java` as pure static functions
with an injectable RNG, so it is deterministically testable.

---

## Web API

| Route | Auth | Purpose |
|---|---|---|
| `GET /api/health` | none | status, player count, TPS, dirty-profile count |
| `GET /api/admin/items?page=&size=&category=` | bearer | paginated item catalogue |
| `GET /api/admin/items/{id}` | bearer | one item definition |
| `GET /api/admin/skills` | bearer | skill caps and total XP |
| `GET /api/admin/profiles/{id}` | bearer | profile summary + skill levels |

`Authorization: Bearer <token>`, token from `web.api.auth.token`.
