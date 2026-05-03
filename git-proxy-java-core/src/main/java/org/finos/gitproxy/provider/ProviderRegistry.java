package org.finos.gitproxy.provider;

import java.util.List;
import org.finos.gitproxy.db.UrlRuleRegistry;

/**
 * Registry of configured git proxy providers. Keyed by the user-configured name (the YAML config map key, e.g.
 * {@code github}, {@code internal-gitlab}), which is also the canonical provider ID stored in the database.
 *
 * <p>Consistent with {@link UrlRuleRegistry} — a lookup/discovery mechanism, not a CRUD store.
 */
public interface ProviderRegistry {

    /**
     * Look up a provider by its friendly name (the YAML map key, e.g. {@code "github"}).
     *
     * @return the provider, or {@code null} if not found
     */
    GitProxyProvider getProvider(String name);

    /** Returns all registered providers. */
    List<GitProxyProvider> getProviders();

    /**
     * Resolves a provider by its name (the YAML config map key, e.g. {@code "github"}). Null/blank input returns null
     * (meaning "applies to all providers").
     *
     * @return the provider, or {@code null} if not found
     */
    default GitProxyProvider resolveProvider(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) return null;
        return getProvider(nameOrId);
    }
}
