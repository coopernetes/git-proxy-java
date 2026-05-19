package org.finos.gitproxy.dashboard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.finos.gitproxy.user.UserStore;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates REST API requests that carry an {@code X-Api-Key} header matching a per-user proxy API key. Resolves
 * the user from the stored SHA-256 hash and sets up their full Spring {@link Authentication} with their actual roles,
 * so all downstream permission checks behave identically to session-based auth.
 *
 * <p>Only activates when no session authentication is already present. Runs after the operator-level
 * {@link ApiKeyAuthFilter} so the break-glass key takes precedence.
 */
@Slf4j
@RequiredArgsConstructor
public class UserApiKeyAuthFilter extends OncePerRequestFilter {

    private final UserStore userStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(ApiKeyAuthFilter.HEADER);
        if (provided != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            String hash = sha256(provided);
            userStore.findByApiKey(hash).ifPresent(user -> {
                List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
                var auth = new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("Authenticated API request for user '{}' via user API key", user.getUsername());
            });
        }
        chain.doFilter(request, response);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
