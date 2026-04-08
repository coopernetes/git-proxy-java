package org.finos.gitproxy.user;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link MutableUserStore} that combines a read-only config store (YAML-defined users) with a mutable JDBC store
 * (dynamically created users). Config users serve as break-glass accounts: they are never written to the database, so
 * there are no stale duplicates across restarts and role/credential changes in YAML take effect on restart.
 *
 * <ul>
 *   <li>Reads check the config store first, then fall back to the JDBC store.
 *   <li>Writes ({@link #createUser}, {@link #addEmail}, etc.) delegate only to the JDBC store.
 *   <li>{@link #findAll()} returns a merged list; config users take precedence on username collision.
 * </ul>
 */
public class CompositeUserStore implements MutableUserStore {

    private final UserStore configStore;
    private final JdbcUserStore jdbcStore;

    public CompositeUserStore(UserStore configStore, JdbcUserStore jdbcStore) {
        this.configStore = configStore;
        this.jdbcStore = jdbcStore;
    }

    // ── reads — config first, JDBC fallback ─────────────────────────────────────

    @Override
    public Optional<UserEntry> findByUsername(String username) {
        Optional<UserEntry> fromConfig = configStore.findByUsername(username);
        return fromConfig.isPresent() ? fromConfig : jdbcStore.findByUsername(username);
    }

    @Override
    public Optional<UserEntry> findByEmail(String email) {
        Optional<UserEntry> fromConfig = configStore.findByEmail(email);
        return fromConfig.isPresent() ? fromConfig : jdbcStore.findByEmail(email);
    }

    @Override
    public Optional<UserEntry> findByScmIdentity(String provider, String scmUsername) {
        Optional<UserEntry> fromConfig = configStore.findByScmIdentity(provider, scmUsername);
        return fromConfig.isPresent() ? fromConfig : jdbcStore.findByScmIdentity(provider, scmUsername);
    }

    @Override
    public List<UserEntry> findAll() {
        // Merge: config users take precedence on username collision.
        Map<String, UserEntry> merged = new LinkedHashMap<>();
        for (UserEntry u : configStore.findAll()) {
            merged.put(u.getUsername(), u);
        }
        for (UserEntry u : jdbcStore.findAll()) {
            merged.putIfAbsent(u.getUsername(), u);
        }
        return new ArrayList<>(merged.values());
    }

    // ── enriched queries — JDBC if the user is there, config fallback ────────────

    @Override
    public List<Map<String, Object>> findEmailsWithVerified(String username) {
        var configUser = configStore.findByUsername(username);
        List<Map<String, Object>> result = new ArrayList<>();

        // Config emails are always included as locked
        configUser.ifPresent(u -> u.getEmails()
                .forEach(e -> result.add(Map.of("email", e, "verified", false, "locked", true, "source", "config"))));

        // Supplemental JDBC emails (skip any that overlap with config)
        Set<String> configEmails =
                configUser.<Set<String>>map(u -> new HashSet<>(u.getEmails())).orElse(Set.of());
        if (jdbcStore.findByUsername(username).isPresent()) {
            jdbcStore.findEmailsWithVerified(username).stream()
                    .filter(e -> !configEmails.contains(e.get("email")))
                    .forEach(result::add);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> findScmIdentitiesWithVerified(String username) {
        var configUser = configStore.findByUsername(username);
        List<Map<String, Object>> result = new ArrayList<>();

        // Config identities are always included as locked
        configUser.ifPresent(u -> u.getScmIdentities().stream()
                .filter(id -> !"proxy".equals(id.getProvider()))
                .forEach(id -> result.add(Map.of(
                        "provider",
                        id.getProvider(),
                        "username",
                        id.getUsername(),
                        "verified",
                        false,
                        "source",
                        "config"))));

        // Supplemental JDBC identities (skip any that overlap with config)
        Set<String> configKeys = configUser
                .map(u -> u.getScmIdentities().stream()
                        .map(id -> id.getProvider() + ":" + id.getUsername())
                        .collect(java.util.stream.Collectors.toSet()))
                .orElse(Set.of());
        if (jdbcStore.findByUsername(username).isPresent()) {
            jdbcStore.findScmIdentitiesWithVerified(username).stream()
                    .filter(id -> !configKeys.contains(id.get("provider") + ":" + id.get("username")))
                    .forEach(result::add);
        }

        return result;
    }

    // ── writes ──────────────────────────────────────────────────────────────────
    // Config users can add supplemental emails/identities (stored in JDBC).
    // Config-defined values are locked and cannot be removed.

    @Override
    public void addEmail(String username, String email) {
        var configUser = configStore.findByUsername(username);
        if (configUser.isPresent()) {
            if (configUser.get().getEmails().contains(email)) return; // already present, no-op
            jdbcStore.upsertUser(username); // ensure JDBC row exists for supplemental data
        }
        jdbcStore.addEmail(username, email);
    }

    @Override
    public void removeEmail(String username, String email) {
        configStore.findByUsername(username).ifPresent(u -> {
            if (u.getEmails().contains(email)) throw new LockedEmailException(email);
        });
        jdbcStore.removeEmail(username, email);
    }

    @Override
    public void addScmIdentity(String username, String provider, String scmUsername) {
        var configUser = configStore.findByUsername(username);
        if (configUser.isPresent()) {
            boolean alreadyInConfig = configUser.get().getScmIdentities().stream()
                    .anyMatch(id -> id.getProvider().equals(provider)
                            && id.getUsername().equals(scmUsername));
            if (alreadyInConfig) return; // no-op
            jdbcStore.upsertUser(username);
        }
        jdbcStore.addScmIdentity(username, provider, scmUsername);
    }

    @Override
    public void removeScmIdentity(String username, String provider, String scmUsername) {
        configStore.findByUsername(username).ifPresent(u -> {
            boolean inConfig = u.getScmIdentities().stream()
                    .anyMatch(id -> id.getProvider().equals(provider)
                            && id.getUsername().equals(scmUsername));
            if (inConfig) throw new LockedByConfigException(username);
        });
        jdbcStore.removeScmIdentity(username, provider, scmUsername);
    }

    @Override
    public void createUser(String username, String passwordHash, String roles) {
        jdbcStore.createUser(username, passwordHash, roles);
    }

    @Override
    public void deleteUser(String username) {
        jdbcStore.deleteUser(username);
    }

    @Override
    public void setPassword(String username, String passwordHash) {
        jdbcStore.setPassword(username, passwordHash);
    }

    @Override
    public void upsertUser(String username) {
        jdbcStore.upsertUser(username);
    }

    @Override
    public void upsertLockedEmail(String username, String email, String authSource) {
        jdbcStore.upsertLockedEmail(username, email, authSource);
    }
}
