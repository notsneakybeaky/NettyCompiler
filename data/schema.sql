-- Netty Runtime — SQLite schema
-- Two tables. That's it.

CREATE TABLE IF NOT EXISTS simulation_ticks (
    tick_id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    state_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS node_events (
    event_id INTEGER PRIMARY KEY AUTOINCREMENT,
    tick_id INTEGER NOT NULL,
    node_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    payload_json TEXT NOT NULL
);
