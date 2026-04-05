package org.finos.gitproxy.jetty.config;

import lombok.Data;

/** Binds a single entry under {@code providers:} in git-proxy.yml. */
@Data
public class ProviderConfig {

    private boolean enabled = true;

    /** Additional URL prefix for this provider's servlet path. */
    private String servletPath = "";

    /** Upstream base URI. Required for custom providers; omit for built-ins (github, gitlab, bitbucket). */
    private String uri = "";

    /**
     * Provider type. Required for custom-named providers; omit only for the built-in default names ({@code github},
     * {@code gitlab}, {@code bitbucket}, {@code codeberg}, {@code forgejo}). Supported values: {@code github},
     * {@code gitlab}, {@code bitbucket}, {@code codeberg}, {@code forgejo}, {@code gitea}.
     */
    private String type = "";
}
