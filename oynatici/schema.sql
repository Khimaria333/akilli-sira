-- Ahenk Oynatıcı: yalnızca ortak önbellek (Deezer ayrıntıları, YouTube eşleşmeleri). Kullanıcı verisi telefonda durur.
CREATE TABLE IF NOT EXISTS cache (
  k TEXT PRIMARY KEY,
  v TEXT,
  ts INTEGER
);
