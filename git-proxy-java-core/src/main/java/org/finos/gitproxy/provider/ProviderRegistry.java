package org.finos.gitproxy.provider;

import java.util.List;
import java.util.Optional;
import org.finos.gitproxy.db.UrlRuleRegistry;

/**
 * Registry of configured git proxy providers. Keyed by the user-configured name (the YAML config map key, e.g.
 * {@code github}, {@code internal-gitlab}), which is also the canonical provider ID stored in the database.
 *
 * <p>Consistent with {@link UrlRuleRegistry} — a lookup/discovery mechanism, not a CRUD store.
 */
public interface ProviderRegistry {

    /**
     * Look up a provider by name (the YAML map key, e.g. {@code "github"}).
     *
     * @return the provider, or {@link Optional#empty()} if not found
     */
    Optional<GitProxyProvider> getProvider(String name);

    /** Returns all registered providers. */
    List<GitProxyProvider> getProviders();

    /**
     * Resolves a provider by name (the YAML config map key, e.g. {@code "github"}).
     *
     * @return the provider, or {@link Optional#empty()} if not found
     */
    default Optional<GitProxyProvider> resolveProvider(String name) {
        return getProvider(name);
    }
}
