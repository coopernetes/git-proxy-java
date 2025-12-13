package org.finos.gitproxy.config;

import java.net.URI;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.finos.gitproxy.git.HttpAuthScheme;
import org.finos.gitproxy.git.HttpOperation;
import org.finos.gitproxy.provider.*;
import org.finos.gitproxy.servlet.filter.*;

/**
 * Builder that creates GitProxy providers and filters from configuration. This class reads the parsed YAML
 * configuration and instantiates the appropriate provider and filter objects for use in the Jetty server.
 */
@Slf4j
public class JettyConfigurationBuilder {

    private final JettyConfigurationLoader configLoader;
    private final String basePath;
    private List<GitProxyProvider> providers;
    private Map<GitProxyProvider, List<GitProxyFilter>> providerFilters;

    public JettyConfigurationBuilder(JettyConfigurationLoader configLoader) {
        this.configLoader = configLoader;
        this.basePath = configLoader.getBasePath();
        this.providers = new ArrayList<>();
        this.providerFilters = new HashMap<>();
    }

    /** Build all providers from configuration. */
    public List<GitProxyProvider> buildProviders() {
        if (!providers.isEmpty()) {
            return providers;
        }

        Map<String, Map<String, Object>> providersConfig = configLoader.getProviders();

        for (Map.Entry<String, Map<String, Object>> entry : providersConfig.entrySet()) {
            String providerName = entry.getKey();
            Map<String, Object> providerConfig = entry.getValue();

            boolean enabled = (Boolean) providerConfig.getOrDefault("enabled", false);
            if (!enabled) {
                log.debug("Provider {} is disabled, skipping", providerName);
                continue;
            }

            GitProxyProvider provider = createProvider(providerName, providerConfig);
            if (provider != null) {
                providers.add(provider);
                log.info("Created provider: {}", provider.getName());
            }
        }

        return providers;
    }

    /** Create a single provider from configuration. */
    private GitProxyProvider createProvider(String name, Map<String, Object> config) {
        String customPath = (String) config.get("servlet-path");
        String uriString = (String) config.get("uri");

        switch (name.toLowerCase()) {
            case GitHubProvider.NAME:
                if (uriString != null) {
                    return new GitHubProvider(URI.create(uriString), basePath, customPath);
                }
                return new GitHubProvider(basePath);

            case GitLabProvider.NAME:
                if (uriString != null) {
                    return new GitLabProvider(URI.create(uriString), basePath, customPath);
                }
                return new GitLabProvider(basePath);

            case BitbucketProvider.NAME:
                if (uriString != null) {
                    return new BitbucketProvider(URI.create(uriString), basePath, customPath);
                }
                return new BitbucketProvider(basePath);

            default:
                // Generic provider
                if (uriString != null) {
                    return GenericProxyProvider.builder()
                            .name(name)
                            .uri(URI.create(uriString))
                            .basePath(basePath)
                            .customPath(customPath)
                            .build();
                } else {
                    log.warn("Unknown provider {} without URI, skipping", name);
                    return null;
                }
        }
    }

    /** Build all filters for a specific provider from configuration. */
    public List<GitProxyFilter> buildFiltersForProvider(GitProxyProvider provider) {
        if (providerFilters.containsKey(provider)) {
            return providerFilters.get(provider);
        }

        List<GitProxyFilter> filters = new ArrayList<>();
        Map<String, Object> filtersConfig = configLoader.getFilters();

        // Add standard filters that all providers need
        filters.add(new ForceGitClientFilter());
        filters.add(new ParseGitRequestFilter(provider));

        // Build GitHub user authenticated filter
        filters.addAll(buildGitHubUserAuthFilter(provider, filtersConfig));

        // Build whitelist filters
        filters.addAll(buildWhitelistFilters(provider, filtersConfig));

        // Add audit filter
        filters.add(new AuditLogFilter());

        providerFilters.put(provider, filters);
        return filters;
    }

    /** Build GitHub user authentication filter if configured. */
    @SuppressWarnings("unchecked")
    private List<GitProxyFilter> buildGitHubUserAuthFilter(
            GitProxyProvider provider, Map<String, Object> filtersConfig) {
        List<GitProxyFilter> filters = new ArrayList<>();

        if (!(provider instanceof GitHubProvider)) {
            return filters;
        }

        Object githubUserAuthConfig = filtersConfig.get("github-user-authenticated");
        if (githubUserAuthConfig == null) {
            return filters;
        }

        Map<String, Object> authConfig = (Map<String, Object>) githubUserAuthConfig;
        boolean enabled = (Boolean) authConfig.getOrDefault("enabled", false);
        if (!enabled) {
            return filters;
        }

        // Check if this provider is in the list
        List<String> providerList = (List<String>) authConfig.get("providers");
        if (providerList != null && !providerList.contains(provider.getName())) {
            return filters;
        }

        int order = (Integer) authConfig.getOrDefault("order", 1);
        Object authSchemesObj = authConfig.get("required-auth-schemes");

        Set<HttpAuthScheme> schemes = new HashSet<>();
        if (authSchemesObj != null) {
            List<String> authSchemes;
            if (authSchemesObj instanceof List) {
                authSchemes = (List<String>) authSchemesObj;
            } else if (authSchemesObj instanceof String) {
                // Handle comma-separated string
                String authSchemesStr = (String) authSchemesObj;
                authSchemes = Arrays.asList(authSchemesStr.split("\\s*,\\s*"));
            } else {
                authSchemes = new ArrayList<>();
            }

            for (String scheme : authSchemes) {
                switch (scheme.toLowerCase().trim()) {
                    case "basic":
                        schemes.add(HttpAuthScheme.BASIC);
                        break;
                    case "bearer":
                        schemes.add(HttpAuthScheme.BEARER);
                        break;
                    case "token":
                        schemes.add(HttpAuthScheme.TOKEN);
                        break;
                }
            }
        } else {
            schemes.add(HttpAuthScheme.BEARER);
        }

        GitHubUserAuthenticatedFilter filter =
                new GitHubUserAuthenticatedFilter(order, (GitHubProvider) provider, schemes);
        filters.add(filter);
        log.info("Created GitHubUserAuthenticatedFilter for provider: {}", provider.getName());

        return filters;
    }

    /** Build whitelist filters if configured. */
    @SuppressWarnings("unchecked")
    private List<GitProxyFilter> buildWhitelistFilters(GitProxyProvider provider, Map<String, Object> filtersConfig) {
        List<GitProxyFilter> filters = new ArrayList<>();

        Object whitelistsConfig = filtersConfig.get("whitelists");
        if (whitelistsConfig == null) {
            return filters;
        }

        List<Map<String, Object>> whitelistsList = (List<Map<String, Object>>) whitelistsConfig;
        List<WhitelistByUrlFilter> whitelistFilters = new ArrayList<>();

        for (Map<String, Object> whitelistConfig : whitelistsList) {
            boolean enabled = (Boolean) whitelistConfig.getOrDefault("enabled", false);
            if (!enabled) {
                continue;
            }

            // Check if this provider is in the list
            List<String> providerList = (List<String>) whitelistConfig.get("providers");
            if (providerList != null && !providerList.contains(provider.getName())) {
                continue;
            }

            int order = (Integer) whitelistConfig.getOrDefault("order", 0);

            // Parse operations
            Set<HttpOperation> operations = parseOperations((List<String>) whitelistConfig.get("operations"));

            // Build filters for each target type
            List<String> slugs = (List<String>) whitelistConfig.get("slugs");
            if (slugs != null && !slugs.isEmpty()) {
                WhitelistByUrlFilter filter =
                        new WhitelistByUrlFilter(order, operations, provider, slugs, AuthorizedByUrlFilter.Target.SLUG);
                whitelistFilters.add(filter);
            }

            List<String> owners = (List<String>) whitelistConfig.get("owners");
            if (owners != null && !owners.isEmpty()) {
                WhitelistByUrlFilter filter = new WhitelistByUrlFilter(
                        order + 1, operations, provider, owners, AuthorizedByUrlFilter.Target.OWNER);
                whitelistFilters.add(filter);
            }

            List<String> names = (List<String>) whitelistConfig.get("names");
            if (names != null && !names.isEmpty()) {
                WhitelistByUrlFilter filter = new WhitelistByUrlFilter(
                        order + 2, operations, provider, names, AuthorizedByUrlFilter.Target.NAME);
                whitelistFilters.add(filter);
            }
        }

        // If we have whitelist filters, wrap them in an aggregate filter
        if (!whitelistFilters.isEmpty()) {
            int minOrder = whitelistFilters.stream()
                    .mapToInt(WhitelistByUrlFilter::getOrder)
                    .min()
                    .orElse(0);
            WhitelistAggregateFilter aggregateFilter =
                    new WhitelistAggregateFilter(minOrder, provider, whitelistFilters);
            filters.add(aggregateFilter);
            log.info(
                    "Created WhitelistAggregateFilter with {} filters for provider: {}",
                    whitelistFilters.size(),
                    provider.getName());
        }

        return filters;
    }

    /** Parse operation strings to HttpOperation enums. */
    private Set<HttpOperation> parseOperations(List<String> operationStrings) {
        if (operationStrings == null || operationStrings.isEmpty()) {
            return Set.of(HttpOperation.PUSH, HttpOperation.FETCH);
        }

        Set<HttpOperation> operations = new HashSet<>();
        for (String op : operationStrings) {
            try {
                operations.add(HttpOperation.valueOf(op.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown operation: {}", op);
            }
        }
        return operations;
    }

    public List<GitProxyProvider> getProviders() {
        return providers;
    }
}
