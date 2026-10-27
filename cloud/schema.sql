CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  name TEXT,
  client_id TEXT,
  refresh_token TEXT,
  access_token TEXT,
  access_exp INTEGER DEFAULT 0,
  enabled INTEGER DEFAULT 1,
  settings TEXT DEFAULT '{}',
  state TEXT DEFAULT '{}',
  queued_for TEXT,
  last_tick INTEGER DEFAULT 0,
  created INTEGER,
  updated INTEGER
);
CREATE TABLE IF NOT EXISTS sessions (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  created INTEGER
);
CREATE INDEX IF NOT EXISTS sessions_user ON sessions(user_id);
CREATE TABLE IF NOT EXISTS cache (
  k TEXT PRIMARY KEY,
  v TEXT,
  ts INTEGER
);
