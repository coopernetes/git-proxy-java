package org.finos.gitproxy.dashboard.e2e;

import static org.junit.jupiter.api.Assertions.*;

import org.finos.gitproxy.jetty.config.GitProxyConfig;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the Spring Security context startup fails fast on misconfigured auth providers. Uses
 * {@link DashboardFixture} to start the real Spring Security stack without a git proxy, so these tests catch
 * context-wiring bugs without needing containers.
 */
class SecurityConfigStartupTest {

    @Test
    void unknownAuthProvider_failsToStart() {
        var config = new GitProxyConfig();
        config.getAuth().setProvider("bogus");

        // Expect startup to fail rather than silently falling back to local auth
        assertThrows(Exception.class, () -> new DashboardFixture(config));
    }

    @Test
    void localAuthProvider_startsSuccessfully() throws Exception {
        var config = new GitProxyConfig();
        config.getAuth().setProvider("local");

        // Sanity check: the fixture should start and be closeable without error
        try (var dashboard = new DashboardFixture(config)) {
            assertNotNull(dashboard.getBaseUrl());
        }
    }
}
