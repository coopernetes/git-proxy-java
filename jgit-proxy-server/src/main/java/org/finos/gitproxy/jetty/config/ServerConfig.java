package org.finos.gitproxy.jetty.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** Binds the {@code server:} block in git-proxy.yml. */
@Data
public class ServerConfig {

    private int port = 8080;

    /**
     * Origins permitted for CORS requests to the dashboard REST API. Used when the frontend is served from a different
     * hostname or port than the backend (e.g. behind a load balancer with a separate public hostname).
     *
     * <p>Empty list (default) restricts to same-origin only. Set {@code ["*"]} to allow all origins (not recommended
     * for production). Configurable at runtime via {@code GITPROXY_SERVER_ALLOWEDORIGINS} env var (comma-separated) or
     * the {@code server.allowed-origins} YAML key.
     *
     * <p>Example: {@code ["https://dashboard.example.com", "https://proxy.example.com"]}
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * Approval mode for store-and-forward pushes. Values: {@code auto} (default), {@code ui}, {@code servicenow}.
     *
     * <p>Note: {@code GitProxyWithDashboardApplication} always uses {@code ui} regardless of this setting.
     */
    private String approvalMode = "auto";

    /**
     * Sideband keepalive interval in seconds. Sends periodic progress packets during long operations (secret scanning,
     * approval polling) to prevent idle-timeout disconnects. Set to 0 to disable.
     */
    private int heartbeatIntervalSeconds = 10;
}
