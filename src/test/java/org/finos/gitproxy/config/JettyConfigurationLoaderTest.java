package org.finos.gitproxy.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.finos.gitproxy.provider.GitProxyProvider;
import org.junit.jupiter.api.Test;

class JettyConfigurationLoaderTest {

    @Test
    void testLoadDefaultConfiguration() {
        JettyConfigurationLoader loader = new JettyConfigurationLoader();
        assertNotNull(loader.getConfig());
        assertNotNull(loader.getGitProxyConfig());
    }

    @Test
    void testGetProviders() {
        JettyConfigurationLoader loader = new JettyConfigurationLoader();
        Map<String, Map<String, Object>> providers = loader.getProviders();
        assertNotNull(providers);
        assertTrue(providers.containsKey("github"));
    }

    @Test
    void testGetServerPort() {
        JettyConfigurationLoader loader = new JettyConfigurationLoader();
        int port = loader.getServerPort();
        assertEquals(8080, port);
    }

    @Test
    void testBuildProviders() {
        JettyConfigurationLoader loader = new JettyConfigurationLoader();
        JettyConfigurationBuilder builder = new JettyConfigurationBuilder(loader);
        List<GitProxyProvider> providers = builder.buildProviders();
        assertNotNull(providers);
        assertTrue(providers.size() >= 1); // At least github in test config
    }
}
