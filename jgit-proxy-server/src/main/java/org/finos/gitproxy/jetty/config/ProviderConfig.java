package org.finos.gitproxy.jetty.config;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Attestation questions presented to reviewers before they can approve a push for this provider. Questions are
     * rendered dynamically in the dashboard approval form. Required questions block submission until answered.
     */
    private List<AttestationQuestion> attestationQuestions = new ArrayList<>();
}
