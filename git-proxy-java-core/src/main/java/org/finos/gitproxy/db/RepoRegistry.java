package org.finos.gitproxy.db;

import java.util.List;
import java.util.Optional;
import org.finos.gitproxy.db.model.AccessRule;

/**
 * Storage abstraction for repository access rules. Implementations may be backed by JDBC or an in-memory store.
 *
 * <p>Rules seeded from YAML configuration have {@code source = CONFIG}; rules created via the REST API have
 * {@code source = DB}. Both are stored together and evaluated identically by the filter chain.
 */
public interface RepoRegistry {

    /** Persist a new rule. */
    void save(AccessRule rule);

    /** Update an existing rule by ID. No-op if not found. */
    void update(AccessRule rule);

    /** Delete a rule by ID. */
    void delete(String id);

    /** Find a rule by ID. */
    Optional<AccessRule> findById(String id);

    /** Return all rules, ordered by {@code rule_order} ascending. */
    List<AccessRule> findAll();

    /**
     * Return all enabled rules applicable to the given provider, ordered by {@code rule_order} ascending. Includes
     * rules with a null provider (which apply to all providers).
     */
    List<AccessRule> findEnabledForProvider(String provider);

    /** Initialize the store (run migrations). Called once at startup. */
    void initialize();
}
