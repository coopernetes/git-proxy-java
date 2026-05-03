package org.finos.gitproxy.jetty.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.finos.gitproxy.approval.AutoApprovalGateway;
import org.finos.gitproxy.approval.UiApprovalGateway;
import org.finos.gitproxy.db.PushStoreFactory;
import org.finos.gitproxy.db.model.AccessRule;
import org.finos.gitproxy.permission.RepoPermission;
import org.finos.gitproxy.provider.GitHubProvider;
import org.finos.gitproxy.provider.GitProxyProvider;
import org.junit.jupiter.api.Test;

class JettyConfigurationBuilderTest {

    // ---- buildApprovalGateway ----

    @Test
    void buildApprovalGateway_defaultConfig_returnsAutoApprovalGateway() {
        var builder = new JettyConfigurationBuilder(configWithApprovalMode("auto"));
        var gateway = builder.buildApprovalGateway(PushStoreFactory.inMemory());
        assertInstanceOf(AutoApprovalGateway.class, gateway);
    }

    @Test
    void buildApprovalGateway_uiMode_returnsUiApprovalGateway() {
        var builder = new JettyConfigurationBuilder(configWithApprovalMode("ui"));
        var gateway = builder.buildApprovalGateway(PushStoreFactory.inMemory());
        assertInstanceOf(UiApprovalGateway.class, gateway);
    }

    @Test
    void buildApprovalGateway_unknownMode_fallsBackToAuto() {
        var builder = new JettyConfigurationBuilder(configWithApprovalMode("bogus"));
        var gateway = builder.buildApprovalGateway(PushStoreFactory.inMemory());
        assertInstanceOf(AutoApprovalGateway.class, gateway);
    }

    // ---- validateProviderReferences ----

    @Test
    void validateProviderReferences_validConfig_doesNotThrow() {
        var config = configWithGithub();
        var permission = new PermissionConfig();
        permission.setUsername("alice");
        permission.setProvider("github");
        permission.setPath("/org/repo");
        config.setPermissions(List.of(permission));

        assertDoesNotThrow(() -> new JettyConfigurationBuilder(config).validateProviderReferences());
    }

    @Test
    void validateProviderReferences_unknownPermissionProvider_throws() {
        var config = configWithGithub();
        var permission = new PermissionConfig();
        permission.setUsername("alice");
        permission.setProvider("not-a-provider");
        permission.setPath("/org/repo");
        config.setPermissions(List.of(permission));

        var builder = new JettyConfigurationBuilder(config);
        var ex = assertThrows(IllegalStateException.class, builder::validateProviderReferences);
        assertTrue(ex.getMessage().contains("not-a-provider"));
        // Should list the configured providers in the error
        assertTrue(ex.getMessage().contains("github"));
    }

    @Test
    void validateProviderReferences_unknownRuleProvider_throws() {
        var config = configWithGithub();
        var ruleConfig = new RuleConfig();
        ruleConfig.setProviders(List.of("typo-provider"));
        ruleConfig.setSlugs(List.of("/org/repo"));
        config.getRules().setAllow(List.of(ruleConfig));

        var builder = new JettyConfigurationBuilder(config);
        assertThrows(IllegalStateException.class, builder::validateProviderReferences);
    }

    @Test
    void validateProviderReferences_emptyConfig_doesNotThrow() {
        // No providers, no cross-references → nothing to validate
        assertDoesNotThrow(() -> new JettyConfigurationBuilder(new GitProxyConfig()).validateProviderReferences());
    }

    // ---- buildProviderRegistry (#127) ----

    @Test
    void buildProviderRegistry_keyedByFriendlyName() {
        var builder = new JettyConfigurationBuilder(configWithGithub());
        var registry = builder.buildProviderRegistry();

        // Lookup by name
        assertTrue(registry.getProvider("github").isPresent());
        assertInstanceOf(GitHubProvider.class, registry.getProvider("github").orElseThrow());
        // Lookup by name via resolveProvider
        assertTrue(registry.resolveProvider("github").isPresent());
        assertSame(
                registry.getProvider("github").orElseThrow(),
                registry.resolveProvider("github").orElseThrow());
        // Name resolves to a stable provider ID
        assertEquals(
                registry.resolveProvider("github").orElseThrow().getProviderId(),
                registry.resolveProvider("github").orElseThrow().getProviderId());
    }

    // ---- buildConfigPermissions — provider name resolution ----

    @Test
    void buildConfigPermissions_name_stored_on_permission() {
        var config = configWithGithub();
        var permission = new PermissionConfig();
        permission.setUsername("alice");
        permission.setProvider("github");
        permission.setPath("/org/repo");
        config.setPermissions(List.of(permission));

        List<RepoPermission> perms = new JettyConfigurationBuilder(config).buildConfigPermissions(config);

        assertEquals(1, perms.size());

        assertEquals("github", perms.get(0).getProvider());
        assertEquals("alice", perms.get(0).getUsername());
    }

    @Test
    void buildConfigPermissions_unknownProvider_throwsWithHelpfulMessage() {
        var config = configWithGithub();
        var permission = new PermissionConfig();
        permission.setUsername("carol");
        permission.setProvider("nonexistent");
        permission.setPath("/org/repo");
        config.setPermissions(List.of(permission));

        var builder = new JettyConfigurationBuilder(config);
        var ex = assertThrows(IllegalStateException.class, () -> builder.buildConfigPermissions(config));
        assertTrue(ex.getMessage().contains("nonexistent"), "error should name the unknown provider");
        assertTrue(ex.getMessage().contains("github"), "error should list configured providers");
    }

    // ---- buildConfigRules — provider name resolution ----

    @Test
    void buildConfigRules_name_stored_on_rule() {
        var config = configWithGithub();
        var ruleConfig = new RuleConfig();
        ruleConfig.setProviders(List.of("github"));
        ruleConfig.setSlugs(List.of("/org/repo"));
        config.getRules().setAllow(List.of(ruleConfig));

        List<AccessRule> rules = new JettyConfigurationBuilder(config).buildConfigRules(config);

        assertFalse(rules.isEmpty());

        assertEquals("github", rules.get(0).getProvider());
    }

    @Test
    void buildConfigRules_noProviderFilter_storesNullProvider() {
        var config = configWithGithub();
        var ruleConfig = new RuleConfig();
        // no providers → applies to all
        ruleConfig.setSlugs(List.of("/org/repo"));
        config.getRules().setAllow(List.of(ruleConfig));

        List<AccessRule> rules = new JettyConfigurationBuilder(config).buildConfigRules(config);

        assertFalse(rules.isEmpty());
        assertNull(rules.get(0).getProvider()); // null = all providers
    }

    // ---- buildConfigRules — provider name scoping ----

    @Test
    void buildConfigRules_name_scopes_rule_to_provider() {
        var config = configWithGithub();
        var ruleConfig = new RuleConfig();
        ruleConfig.setOrder(110);
        ruleConfig.setProviders(List.of("github"));
        ruleConfig.setSlugs(List.of("/org/repo"));
        config.getRules().setAllow(List.of(ruleConfig));

        var builder = new JettyConfigurationBuilder(config);
        var githubProviderId = builder.buildProviderRegistry()
                .getProvider("github")
                .orElseThrow()
                .getProviderId();
        var rules = builder.buildConfigRules();

        assertFalse(rules.isEmpty(), "rule scoped to 'github' should produce at least one AccessRule");
        assertTrue(
                rules.stream().anyMatch(r -> githubProviderId.equals(r.getProvider())),
                "rule should be scoped to the GitHub provider ID");
    }

    @Test
    void buildConfigRules_name_excludes_other_provider() {
        var config = configWithGithubAndGitlab();
        var ruleConfig = new RuleConfig();
        ruleConfig.setOrder(110);
        ruleConfig.setProviders(List.of("github")); // only github
        ruleConfig.setSlugs(List.of("/org/repo"));
        config.getRules().setAllow(List.of(ruleConfig));

        var builder = new JettyConfigurationBuilder(config);
        var gitlabProviderId = builder.buildProviderRegistry()
                .getProvider("gitlab")
                .orElseThrow()
                .getProviderId();
        var rules = builder.buildConfigRules();

        assertTrue(
                rules.stream().noneMatch(r -> gitlabProviderId.equals(r.getProvider())),
                "rule scoped to 'github' should not produce a rule for the GitLab provider");
    }

    // ---- multi-provider: same type, different hostnames ----

    @Test
    void twoProvidersOfSameType_differentNames_haveDistinctProviderIds() {
        var config = configWithTwoGitHubProviders();
        var builder = new JettyConfigurationBuilder(config);
        var providers = builder.buildProviders();

        assertEquals(2, providers.size());
        var ids = providers.stream().map(GitProxyProvider::getProviderId).toList();
        assertTrue(ids.contains("github"), "public GitHub should have id 'github'");
        assertTrue(ids.contains("internal-github"), "internal GHES should have id 'internal-github'");
        assertNotEquals(
                providers.get(0).getProviderId(),
                providers.get(1).getProviderId(),
                "two providers of the same type must have distinct IDs");
    }

    @Test
    void twoProvidersOfSameType_permissionsAreKeptSeparate() {
        var config = configWithTwoGitHubProviders();
        var publicPerm = new PermissionConfig();
        publicPerm.setUsername("alice");
        publicPerm.setProvider("github");
        publicPerm.setPath("/org/public-repo");
        var internalPerm = new PermissionConfig();
        internalPerm.setUsername("bob");
        internalPerm.setProvider("internal-github");
        internalPerm.setPath("/corp/internal-repo");
        config.setPermissions(List.of(publicPerm, internalPerm));

        var perms = new JettyConfigurationBuilder(config).buildConfigPermissions(config);

        assertEquals(2, perms.size());
        var alicePerm = perms.stream()
                .filter(p -> "alice".equals(p.getUsername()))
                .findFirst()
                .orElseThrow();
        var bobPerm = perms.stream()
                .filter(p -> "bob".equals(p.getUsername()))
                .findFirst()
                .orElseThrow();
        assertEquals("github", alicePerm.getProvider());
        assertEquals("internal-github", bobPerm.getProvider());
    }

    // ---- helpers ----

    private static GitProxyConfig configWithApprovalMode(String mode) {
        var config = new GitProxyConfig();
        config.getServer().setApprovalMode(mode);
        return config;
    }

    private static GitProxyConfig configWithGithub() {
        var config = new GitProxyConfig();
        var providerConfig = new ProviderConfig();
        providerConfig.setEnabled(true);
        config.setProviders(Map.of("github", providerConfig));
        return config;
    }

    private static GitProxyConfig configWithGithubAndGitlab() {
        var config = new GitProxyConfig();
        var github = new ProviderConfig();
        github.setEnabled(true);
        var gitlab = new ProviderConfig();
        gitlab.setEnabled(true);
        config.setProviders(Map.of("github", github, "gitlab", gitlab));
        return config;
    }

    private static GitProxyConfig configWithTwoGitHubProviders() {
        var config = new GitProxyConfig();
        var publicGitHub = new ProviderConfig();
        publicGitHub.setEnabled(true);
        var internalGitHub = new ProviderConfig();
        internalGitHub.setEnabled(true);
        internalGitHub.setType("github");
        internalGitHub.setUri("https://github.internal.example.com");
        config.setProviders(Map.of("github", publicGitHub, "internal-github", internalGitHub));
        return config;
    }
}
