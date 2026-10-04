-- SkyBlock Remake — baseline schema (PostgreSQL).
-- MySQL variants live in V1__init_mysql.sql; the MigrationRunner picks by dialect.
-- All money is BIGINT cents. All ids are UUID. Every mutation that moves value is
-- wrapped in a transaction by the repository layer and mirrored into sb_audit_log.

CREATE TABLE IF NOT EXISTS sb_schema_version (
    version      INTEGER PRIMARY KEY,
    applied_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    checksum     TEXT NOT NULL,
    description  TEXT NOT NULL
);

-- --------------------------------------------------------------- player profiles
CREATE TABLE IF NOT EXISTS sb_profiles (
    profile_id      UUID PRIMARY KEY,
    owner_uuid      UUID NOT NULL,
    name            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen       TIMESTAMPTZ,
    selected        BOOLEAN NOT NULL DEFAULT FALSE,
    coins_cents     BIGINT NOT NULL DEFAULT 0,
    bank_cents      BIGINT NOT NULL DEFAULT 0,
    purse_cents     BIGINT NOT NULL DEFAULT 0,
    fairy_souls     INTEGER NOT NULL DEFAULT 0,
    first_join      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- denormalised for fast leaderboards / dashboard, recomputed on save
    skill_average   DOUBLE PRECISION NOT NULL DEFAULT 0,
    catacombs_level INTEGER NOT NULL DEFAULT 0,
    slayer_xp       BIGINT NOT NULL DEFAULT 0,
    networth_cents  BIGINT NOT NULL DEFAULT 0,
    version         INTEGER NOT NULL DEFAULT 0,
    data            JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_owner_selected ON sb_profiles (owner_uuid) WHERE selected;
CREATE INDEX IF NOT EXISTS idx_profiles_owner ON sb_profiles (owner_uuid);
CREATE INDEX IF NOT EXISTS idx_profiles_networth ON sb_profiles (networth_cents DESC);
CREATE INDEX IF NOT EXISTS idx_profiles_skill_avg ON sb_profiles (skill_average DESC);

CREATE TABLE IF NOT EXISTS sb_accounts (
    uuid           UUID PRIMARY KEY,
    username       TEXT NOT NULL,
    first_login    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login     TIMESTAMPTZ,
    last_ip        INET,
    selected_profile UUID REFERENCES sb_profiles(profile_id) ON DELETE SET NULL,
    muted_until    TIMESTAMPTZ,
    banned_until   TIMESTAMPTZ,
    ban_reason     TEXT,
    flags          TEXT[] NOT NULL DEFAULT '{}'
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_username_lower ON sb_accounts (lower(username));

-- Skill XP is a row per skill so leaderboards are plain SQL.
CREATE TABLE IF NOT EXISTS sb_skills (
    profile_id UUID NOT NULL REFERENCES sb_profiles(profile_id) ON DELETE CASCADE,
    skill      TEXT NOT NULL,
    xp         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (profile_id, skill)
);

CREATE TABLE IF NOT EXISTS sb_collections (
    profile_id UUID NOT NULL REFERENCES sb_profiles(profile_id) ON DELETE CASCADE,
    item_id    TEXT NOT NULL,
    amount     BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (profile_id, item_id)
);

CREATE TABLE IF NOT EXISTS sb_achievements (
    profile_id      UUID NOT NULL REFERENCES sb_profiles(profile_id) ON DELETE CASCADE,
    achievement_id  TEXT NOT NULL,
    tier            INTEGER NOT NULL DEFAULT 0,
    progress        BIGINT NOT NULL DEFAULT 0,
    completed_at    TIMESTAMPTZ,
    PRIMARY KEY (profile_id, achievement_id)
);

CREATE TABLE IF NOT EXISTS sb_quests (
    profile_id  UUID NOT NULL REFERENCES sb_profiles(profile_id) ON DELETE CASCADE,
    quest_id    TEXT NOT NULL,
    state       TEXT NOT NULL DEFAULT 'active',   -- active | complete | rewarded
    progress    JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    PRIMARY KEY (profile_id, quest_id)
);

-- ------------------------------------------------------------------ inventories
-- Inventories are stored as JSONB item arrays; item instances carry their own uid
-- so a duplicate is detectable even when every attribute matches.
CREATE TABLE IF NOT EXISTS sb_inventories (
    profile_id UUID NOT NULL REFERENCES sb_profiles(profile_id) ON DELETE CASCADE,
    kind       TEXT NOT NULL,      -- inventory | armor | accessories | wardrobe | storage:<n> | sacks
    slot_index INTEGER NOT NULL DEFAULT 0,
    items      JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (profile_id, kind, slot_index)
);

CREATE TABLE IF NOT EXISTS sb_pets (
    pet_id     UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES sb_profiles(profile_id) ON DELETE CASCADE,
    type       TEXT NOT NULL,
    rarity     TEXT NOT NULL,
    xp         BIGINT NOT NULL DEFAULT 0,
    held_item  TEXT,
    active     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_pets_profile ON sb_pets (profile_id);

-- -------------------------------------------------------------------- islands
CREATE TABLE IF NOT EXISTS sb_islands (
    island_id    UUID PRIMARY KEY,
    owner_uuid   UUID NOT NULL,
    name         TEXT NOT NULL,
    size         INTEGER NOT NULL DEFAULT 5,
    world_name   TEXT NOT NULL,
    center_x     INTEGER NOT NULL DEFAULT 0,
    center_z     INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    data         JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_islands_owner ON sb_islands (owner_uuid);

CREATE TABLE IF NOT EXISTS sb_island_members (
    island_id UUID NOT NULL REFERENCES sb_islands(island_id) ON DELETE CASCADE,
    uuid      UUID NOT NULL,
    role      TEXT NOT NULL DEFAULT 'member',  -- owner | coowner | member | guest
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (island_id, uuid)
);

CREATE TABLE IF NOT EXISTS sb_minions (
    minion_id  UUID PRIMARY KEY,
    island_id  UUID NOT NULL REFERENCES sb_islands(island_id) ON DELETE CASCADE,
    type       TEXT NOT NULL,
    tier       INTEGER NOT NULL,
    x          INTEGER NOT NULL,
    y          INTEGER NOT NULL,
    z          INTEGER NOT NULL,
    -- last_collect_at + action_seconds lets offline progress be computed exactly
    last_collect_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    fuel_until      TIMESTAMPTZ,
    storage         JSONB NOT NULL DEFAULT '[]'::jsonb,
    upgrades        JSONB NOT NULL DEFAULT '[]'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_minions_island ON sb_minions (island_id);

-- -------------------------------------------------------------------- economy
CREATE TABLE IF NOT EXISTS sb_transactions (
    tx_id      BIGSERIAL PRIMARY KEY,
    profile_id UUID NOT NULL,
    kind       TEXT NOT NULL,       -- earn | spend | transfer | tax | refund | admin
    amount     BIGINT NOT NULL,     -- signed cents
    balance_after BIGINT NOT NULL,
    reason     TEXT NOT NULL,
    counterparty UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tx_profile_time ON sb_transactions (profile_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tx_kind_time ON sb_transactions (kind, created_at DESC);

CREATE TABLE IF NOT EXISTS sb_auctions (
    auction_id   UUID PRIMARY KEY,
    seller_uuid  UUID NOT NULL,
    profile_id   UUID NOT NULL,
    item         JSONB NOT NULL,
    starting_bid BIGINT NOT NULL,
    highest_bid  BIGINT NOT NULL DEFAULT 0,
    highest_bidder UUID,
    bin          BOOLEAN NOT NULL DEFAULT FALSE,
    ends_at      TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed      BOOLEAN NOT NULL DEFAULT FALSE,
    -- item fingerprint guards against re-listing an item that is already listed
    fingerprint  TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_auctions_active ON sb_auctions (ends_at) WHERE NOT claimed;
CREATE INDEX IF NOT EXISTS idx_auctions_seller ON sb_auctions (seller_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS idx_auctions_live_fingerprint ON sb_auctions (fingerprint) WHERE NOT claimed;

CREATE TABLE IF NOT EXISTS sb_bids (
    bid_id     BIGSERIAL PRIMARY KEY,
    auction_id UUID NOT NULL REFERENCES sb_auctions(auction_id) ON DELETE CASCADE,
    bidder     UUID NOT NULL,
    amount     BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_bids_auction ON sb_bids (auction_id, amount DESC);

-- Bazaar order book. One row per side per product; matched by price/time priority.
CREATE TABLE IF NOT EXISTS sb_bazaar_orders (
    order_id   UUID PRIMARY KEY,
    product    TEXT NOT NULL,
    side       TEXT NOT NULL CHECK (side IN ('buy','sell')),
    owner_uuid UUID NOT NULL,
    amount     BIGINT NOT NULL,
    remaining  BIGINT NOT NULL,
    unit_price BIGINT NOT NULL,      -- cents per unit
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    filled_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_bazaar_open ON sb_bazaar_orders (product, side, unit_price, created_at) WHERE remaining > 0;

CREATE TABLE IF NOT EXISTS sb_bazaar_state (
    product      TEXT PRIMARY KEY,
    buy_volume   BIGINT NOT NULL DEFAULT 0,
    sell_volume  BIGINT NOT NULL DEFAULT 0,
    last_price   BIGINT NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------------- dungeons
CREATE TABLE IF NOT EXISTS sb_dungeon_runs (
    run_id     UUID PRIMARY KEY,
    floor      INTEGER NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at   TIMESTAMPTZ,
    completed  BOOLEAN NOT NULL DEFAULT FALSE,
    score      INTEGER NOT NULL DEFAULT 0,
    members    JSONB NOT NULL DEFAULT '[]'::jsonb,
    result     JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_runs_floor_time ON sb_dungeon_runs (floor, started_at DESC);

CREATE TABLE IF NOT EXISTS sb_dungeon_classes (
    profile_id UUID NOT NULL REFERENCES sb_profiles(profile_id) ON DELETE CASCADE,
    class_key  TEXT NOT NULL,       -- berserk | archer | mage | tank | healer
    xp         BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (profile_id, class_key)
);

-- ----------------------------------------------------------------- moderation
CREATE TABLE IF NOT EXISTS sb_audit_log (
    id         BIGSERIAL PRIMARY KEY,
    actor      UUID,
    action     TEXT NOT NULL,
    target     TEXT,
    detail     JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip         INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_action_time ON sb_audit_log (action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON sb_audit_log (actor, created_at DESC);

CREATE TABLE IF NOT EXISTS sb_mutes (
    uuid       UUID NOT NULL,
    moderator  UUID NOT NULL,
    reason     TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (uuid, created_at)
);

-- Duplicate-item defence: every item instance ever created is fingerprinted once.
CREATE TABLE IF NOT EXISTS sb_item_registry (
    uid        TEXT PRIMARY KEY,
    profile_id UUID,
    item_id    TEXT NOT NULL,
    fingerprint TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    destroyed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_item_registry_profile ON sb_item_registry (profile_id) WHERE destroyed_at IS NULL;
