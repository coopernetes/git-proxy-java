package org.finos.gitproxy.jetty.config;

import lombok.Data;

/** Binds the {@code auth.oidc} block in git-proxy.yml. */
@Data
public class OidcAuthConfig {

    /**
     * OIDC provider issuer URI. Spring Security will fetch {@code {issuerUri}/.well-known/openid-configuration} at
     * startup to discover authorization, token, and JWKS endpoints. Example: {@code https://accounts.example.com} or
     * {@code http://localhost:9090/default} (mock server).
     *
     * <p><b>Entra ID note:</b> Entra ID tokens are issued by {@code https://sts.windows.net/{tenant}/} rather than the
     * discovery URL, so issuer validation fails with the standard discovery path. Set {@code jwk-set-uri} to bypass
     * issuer validation (see below).
     */
    private String issuerUri = "";

    /** OAuth2 client ID registered with the provider. */
    private String clientId = "";

    /**
     * OAuth2 client secret. Not required when {@code private-key-path} is set (the server authenticates via signed JWT
     * assertion instead).
     */
    private String clientSecret = "";

    /**
     * Optional JWK Set URI for JWT signature verification. When set, the client registration is built from explicit
     * endpoint URLs (derived from {@code issuerUri}) rather than OIDC discovery, and issuer claim validation is
     * disabled. Useful when the provider's reported issuer URL does not match the URL used to reach it.
     *
     * <p><b>Required for Entra ID</b> because tokens carry {@code iss=https://sts.windows.net/{tenant}/} rather than
     * the discovery base URL. Set this to {@code https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys}.
     *
     * <p>Example: {@code https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys}
     */
    private String jwkSetUri = "";

    /**
     * Path to a PKCS#8 PEM-encoded RSA private key file for {@code private_key_jwt} client authentication. When set,
     * the client presents a signed JWT assertion to the token endpoint instead of a {@code client_secret}. This is the
     * preferred authentication method for confidential applications (no shared secret, less frequent rotation).
     *
     * <p>Generate a key pair with:
     *
     * <pre>
     * openssl genrsa -out private.pem 2048
     * openssl pkcs8 -topk8 -nocrypt -in private.pem -out private-pkcs8.pem
     * openssl rsa -in private.pem -pubout -out public.pem   # register the public key with your IDP
     * </pre>
     *
     * <p>Example: {@code /run/secrets/gitproxy-oidc-private-key.pem}
     */
    private String privateKeyPath = "";
}
