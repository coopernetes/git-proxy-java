package org.finos.gitproxy.jetty.config;

import static org.junit.jupiter.api.Assertions.*;

import org.github.gestalt.config.exceptions.GestaltException;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GitProxyConfigLoader}.
 *
 * <p>Verifies that the base config ({@code git-proxy.yml}) is loaded and merged with local overrides
 * ({@code git-proxy-local.yml}) correctly. Environment variable overrides are exercised in the e2e suite.
 */
class GitProxyConfigLoaderTest {

    // --- server defaults ---

    @Test
    void defaultPort_is8080() throws GestaltException {
        GitProxyConfig config = GitProxyConfigLoader.load();
        assertEquals(8080, config.getServer().getPort());
    }

    @Test
    void defaultApprovalMode_isAuto() throws GestaltException {
        GitProxyConfig config = GitProxyConfigLoader.load();
        assertEquals("auto", config.getServer().getApprovalMode());
    }

    @Test
    void defaultHeartbeatInterval_is10() throws GestaltException {
        GitProxyConfig config = GitProxyConfigLoader.load();
        assertEquals(10, config.getServer().getHeartbeatIntervalSeconds());
    }

    // --- database defaults ---

    @Test
    void databaseType_isConfigured() throws GestaltException {
        String type = GitProxyConfigLoader.load().getDatabase().getType();
        assertNotNull(type);
        assertFalse(type.isBlank());
    }

    // --- providers ---

    @Test
    void defaultProviders_includesGitHub() throws GestaltException {
        assertTrue(GitProxyConfigLoader.load().getProviders().containsKey("github"));
    }

    @Test
    void defaultProviders_includesGitLab() throws GestaltException {
        assertTrue(GitProxyConfigLoader.load().getProviders().containsKey("gitlab"));
    }

    @Test
    void defaultProviders_includesBitbucket() throws GestaltException {
        assertTrue(GitProxyConfigLoader.load().getProviders().containsKey("bitbucket"));
    }

    @Test
    void defaultProviders_githubIsEnabled() throws GestaltException {
        ProviderConfig github = GitProxyConfigLoader.load().getProviders().get("github");
        assertNotNull(github);
        assertTrue(github.isEnabled());
    }

    @Test
    void deepMerge_localOverride_doesNotWipeOtherProviders() throws GestaltException {
        // Even if a local override touches only one key, all three built-in providers must survive.
        var providers = GitProxyConfigLoader.load().getProviders();
        assertTrue(providers.containsKey("github"));
        assertTrue(providers.containsKey("gitlab"));
        assertTrue(providers.containsKey("bitbucket"));
    }

    // --- commit config presence ---

    @Test
    void commitConfig_secretScanning_hasDefault() throws GestaltException {
        var ss = GitProxyConfigLoader.load().getCommit().getSecretScanning();
        assertNotNull(ss);
        // base config ships with secret scanning disabled
        assertFalse(ss.isEnabled());
    }

    // --- whitelists ---

    @Test
    void whitelistFilters_returnsNonNullList() throws GestaltException {
        assertNotNull(GitProxyConfigLoader.load().getFilters().getWhitelists());
    }
}
