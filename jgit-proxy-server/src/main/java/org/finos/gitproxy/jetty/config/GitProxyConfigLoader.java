package org.finos.gitproxy.jetty.config;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.github.gestalt.config.Gestalt;
import org.github.gestalt.config.builder.GestaltBuilder;
import org.github.gestalt.config.exceptions.GestaltException;
import org.github.gestalt.config.source.ClassPathConfigSourceBuilder;
import org.github.gestalt.config.source.MapConfigSourceBuilder;

/**
 * Loads {@link GitProxyConfig} from YAML files and environment variable overrides using Gestalt.
 *
 * <p>Source priority (lowest → highest):
 *
 * <ol>
 *   <li>{@code git-proxy.yml} — base defaults shipped with the jar
 *   <li>{@code git-proxy-local.yml} — local/deployment overrides (optional, silently skipped if absent)
 *   <li>Environment variables with {@code GITPROXY_} prefix (highest priority)
 * </ol>
 *
 * <p>Environment variable naming: strip the {@code GITPROXY_} prefix, lowercase, replace {@code _} with {@code .} to
 * get the config path. Examples:
 *
 * <ul>
 *   <li>{@code GITPROXY_SERVER_PORT=9090} → {@code server.port}
 *   <li>{@code GITPROXY_DATABASE_TYPE=postgres} → {@code database.type}
 *   <li>{@code GITPROXY_PROVIDERS_GITHUB_ENABLED=false} → {@code providers.github.enabled}
 * </ul>
 */
@Slf4j
public final class GitProxyConfigLoader {

    private static final String BASE_CONFIG = "git-proxy.yml";
    private static final String LOCAL_CONFIG = "git-proxy-local.yml";
    private static final String ENV_PREFIX = "GITPROXY_";

    private GitProxyConfigLoader() {}

    /**
     * Loads and merges configuration from all sources.
     *
     * @return fully-populated {@link GitProxyConfig}
     * @throws GestaltException if the base config cannot be parsed
     */
    public static GitProxyConfig load() throws GestaltException {
        var builder = new GestaltBuilder()
                .setTreatMissingValuesAsErrors(false)
                .setTreatMissingDiscretionaryValuesAsErrors(false);

        builder.addSource(
                ClassPathConfigSourceBuilder.builder().setResource(BASE_CONFIG).build());
        log.info("Loaded base configuration from {}", BASE_CONFIG);

        // Local overrides are optional — skip silently if the resource is absent
        if (GitProxyConfigLoader.class.getClassLoader().getResource(LOCAL_CONFIG) != null) {
            builder.addSource(ClassPathConfigSourceBuilder.builder()
                    .setResource(LOCAL_CONFIG)
                    .build());
            log.info("Loaded local configuration overrides from {}", LOCAL_CONFIG);
        } else {
            log.debug("No local configuration file {} found (this is normal)", LOCAL_CONFIG);
        }

        // Env var overrides: GITPROXY_SERVER_PORT → server.port
        Map<String, String> envOverrides = buildEnvOverrides();
        if (!envOverrides.isEmpty()) {
            builder.addSource(MapConfigSourceBuilder.builder()
                    .setCustomConfig(envOverrides)
                    .build());
            log.info("Applied {} environment variable override(s) with prefix {}", envOverrides.size(), ENV_PREFIX);
        }

        Gestalt gestalt = builder.build();
        gestalt.loadConfigs();

        return gestalt.getConfig("", GitProxyConfig.class);
    }

    private static Map<String, String> buildEnvOverrides() {
        Map<String, String> overrides = new HashMap<>();
        System.getenv().forEach((key, value) -> {
            if (key.startsWith(ENV_PREFIX)) {
                String configPath =
                        key.substring(ENV_PREFIX.length()).toLowerCase().replace('_', '.');
                overrides.put(configPath, value);
                log.debug("Env override: {} → {}", key, configPath);
            }
        });
        return overrides;
    }
}
