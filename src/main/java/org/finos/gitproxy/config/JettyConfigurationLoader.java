package org.finos.gitproxy.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

/**
 * Configuration loader for the Jetty-based GitProxy application. This class reads configuration from YAML files
 * (application.yml, application-local.yml) and provides a structured representation of the configuration that can be
 * used to bootstrap the Jetty server with appropriate providers and filters.
 */
@Slf4j
public class JettyConfigurationLoader {

    private static final String DEFAULT_CONFIG = "application.yml";
    private static final String LOCAL_CONFIG = "application-local.yml";

    private Map<String, Object> config;

    public JettyConfigurationLoader() {
        this.config = loadConfiguration();
    }

    /**
     * Load configuration from YAML files. Loads application.yml first, then overlays application-local.yml if it
     * exists.
     */
    private Map<String, Object> loadConfiguration() {
        Yaml yaml = new Yaml();
        Map<String, Object> baseConfig = new HashMap<>();

        // Load base configuration
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(DEFAULT_CONFIG)) {
            if (is != null) {
                Map<String, Object> loaded = yaml.load(is);
                if (loaded != null) {
                    baseConfig = loaded;
                    log.info("Loaded base configuration from {}", DEFAULT_CONFIG);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to load base configuration from {}: {}", DEFAULT_CONFIG, e.getMessage());
        }

        // Load local configuration and overlay
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(LOCAL_CONFIG)) {
            if (is != null) {
                Map<String, Object> localConfig = yaml.load(is);
                if (localConfig != null) {
                    deepMerge(baseConfig, localConfig);
                    log.info("Loaded and merged local configuration from {}", LOCAL_CONFIG);
                }
            }
        } catch (IOException e) {
            log.debug("No local configuration found at {}", LOCAL_CONFIG);
        }

        return baseConfig;
    }

    /** Deep merge two maps. Values from the overlay map will overwrite or extend values in the base map. */
    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> base, Map<String, Object> overlay) {
        for (Map.Entry<String, Object> entry : overlay.entrySet()) {
            String key = entry.getKey();
            Object overlayValue = entry.getValue();

            if (base.containsKey(key)) {
                Object baseValue = base.get(key);
                if (baseValue instanceof Map && overlayValue instanceof Map) {
                    deepMerge((Map<String, Object>) baseValue, (Map<String, Object>) overlayValue);
                } else {
                    base.put(key, overlayValue);
                }
            } else {
                base.put(key, overlayValue);
            }
        }
    }

    /** Get the git-proxy configuration section. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getGitProxyConfig() {
        return (Map<String, Object>) config.getOrDefault("git-proxy", new HashMap<>());
    }

    /** Get the providers configuration. */
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> getProviders() {
        Map<String, Object> gitProxyConfig = getGitProxyConfig();
        return (Map<String, Map<String, Object>>) gitProxyConfig.getOrDefault("providers", new HashMap<>());
    }

    /** Get the filters configuration. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFilters() {
        Map<String, Object> gitProxyConfig = getGitProxyConfig();
        return (Map<String, Object>) gitProxyConfig.getOrDefault("filters", new HashMap<>());
    }

    /** Get the base path for servlets. */
    public String getBasePath() {
        Map<String, Object> gitProxyConfig = getGitProxyConfig();
        return (String) gitProxyConfig.getOrDefault("base-path", "");
    }

    /** Get server port configuration. */
    @SuppressWarnings("unchecked")
    public int getServerPort() {
        Map<String, Object> serverConfig = (Map<String, Object>) config.getOrDefault("server", new HashMap<>());
        Object port = serverConfig.get("port");
        if (port instanceof Integer) {
            return (Integer) port;
        }
        return 8080; // default
    }

    /** Get the entire configuration map. */
    public Map<String, Object> getConfig() {
        return config;
    }
}
