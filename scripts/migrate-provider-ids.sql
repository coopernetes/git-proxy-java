-- One-time data fixup: rename provider column values from `type/host` to `name`.
--
-- Run this against your database before deploying the new version.
-- Replace 'old_value' and 'new_value' with the actual mappings for your deployment.
--
-- To find what values are currently stored, run:
--   SELECT DISTINCT provider FROM user_scm_identities;
--   SELECT DISTINCT provider FROM repo_permissions;
--   SELECT DISTINCT provider FROM access_rules WHERE provider IS NOT NULL;
--   SELECT DISTINCT provider FROM push_records;
--   SELECT DISTINCT provider FROM fetch_records;
--   SELECT DISTINCT provider FROM scm_token_cache;
--
-- Example: rename 'github/github.com' -> 'github'
--   Replace 'github/github.com' with 'github' in the statements below.

-- user_scm_identities has a composite PK that includes provider, so rows must be
-- inserted with the new key and old rows deleted (UPDATE would violate the PK constraint).
INSERT INTO user_scm_identities (username, provider, scm_username, verified, source)
    SELECT username, 'new_value', scm_username, verified, source
    FROM user_scm_identities
    WHERE provider = 'old_value';
DELETE FROM user_scm_identities WHERE provider = 'old_value';

-- scm_token_cache also has a composite PK including provider
INSERT INTO scm_token_cache (token_hash, provider, username, cached_at, expires_at)
    SELECT token_hash, 'new_value', username, cached_at, expires_at
    FROM scm_token_cache
    WHERE provider = 'old_value';
DELETE FROM scm_token_cache WHERE provider = 'old_value';

-- Simple UPDATE for tables where provider is not part of the PK
UPDATE repo_permissions  SET provider = 'new_value' WHERE provider = 'old_value';
UPDATE access_rules      SET provider = 'new_value' WHERE provider = 'old_value';
UPDATE push_records      SET provider = 'new_value' WHERE provider = 'old_value';
UPDATE fetch_records     SET provider = 'new_value' WHERE provider = 'old_value';
