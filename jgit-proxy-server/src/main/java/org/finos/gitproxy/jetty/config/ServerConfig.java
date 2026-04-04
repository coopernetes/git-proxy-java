package org.finos.gitproxy.jetty.config;

import lombok.Data;

/** Binds the {@code server:} block in git-proxy.yml. */
@Data
public class ServerConfig {

    private int port = 8080;

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
