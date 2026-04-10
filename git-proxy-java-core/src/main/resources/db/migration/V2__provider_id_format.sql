-- Provider columns now store type/host (e.g. "github/github.com") instead of
-- bare type (e.g. "github").  Update existing rows to the new format.

UPDATE push_records         SET provider = 'github/github.com' WHERE provider = 'github';
UPDATE user_scm_identities  SET provider = 'github/github.com' WHERE provider = 'github';
UPDATE access_rules         SET provider = 'github/github.com' WHERE provider = 'github';
UPDATE fetch_records        SET provider = 'github/github.com' WHERE provider = 'github';
UPDATE scm_token_cache      SET provider = 'github/github.com' WHERE provider = 'github';
UPDATE repo_permissions     SET provider = 'github/github.com' WHERE provider = 'github';
