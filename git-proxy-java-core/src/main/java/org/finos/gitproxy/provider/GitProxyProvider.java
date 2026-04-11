package org.finos.gitproxy.provider;

import java.net.URI;

public interface GitProxyProvider {

    /** User-facing label from the YAML config key. Used for display and logging. */
    String getName();

    /**
     * Provider type identifier (e.g. "github", "gitlab", "bitbucket", "forgejo"). Used for API behavior dispatch.
     * Multiple providers can share the same type with different URIs.
     */
    String getType();

    /**
     * Unique provider identity combining type and host (e.g. "github/github.com", "github/github.corp.example.com").
     * Used for SCM identity resolution, permission matching, token caching, and DB storage. Two providers of the same
     * type but different hosts (e.g. public GitHub and internal GHES) have distinct provider IDs — tokens, identities,
     * and permissions are never shared across hosts.
     */
    default String getProviderId() {
        return getType() + "/" + getUri().getHost();
    }

    URI getUri();

    String servletPath();

    String servletMapping();

    /**
     * HTTP status code to return when a {@code /info/refs} discovery request is blocked by URL rules. Defaults to
     * {@code 403 Forbidden} — unambiguous, helps clients distinguish a proxy denial from a missing repo. Operators may
     * configure {@code 404} to obscure whether a repository exists at all.
     */
    default int getBlockedInfoRefsStatus() {
        return 403;
    }
}
