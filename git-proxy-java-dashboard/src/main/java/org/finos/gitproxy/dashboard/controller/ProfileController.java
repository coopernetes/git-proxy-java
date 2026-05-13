package org.finos.gitproxy.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import org.finos.gitproxy.user.EmailConflictException;
import org.finos.gitproxy.user.LockedByConfigException;
import org.finos.gitproxy.user.LockedEmailException;
import org.finos.gitproxy.user.ReadOnlyUserStore;
import org.finos.gitproxy.user.ScmIdentityConflictException;
import org.finos.gitproxy.user.UserStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service profile management. Authenticated users can add and remove their own email claims and SCM identity
 * associations.
 *
 * <p>All endpoints return {@code 501 Not Implemented} when the active {@link UserStore} is read-only (e.g.
 * {@code StaticUserStore} used with a memory or mongo database backend).
 */
@Tag(name = "Profile", description = "Self-service profile management for the authenticated user")
@RestController
@RequestMapping("/api/me")
public class ProfileController {

    private static final ResponseEntity<Map<String, String>> NOT_MUTABLE = ResponseEntity.status(
                    HttpStatus.NOT_IMPLEMENTED)
            .body(Map.of("error", "Profile mutations are not supported with the current user store backend"));

    private static final ResponseEntity<Map<String, String>> LOCKED_BY_CONFIG = ResponseEntity.status(
                    HttpStatus.FORBIDDEN)
            .body(Map.of("error", "This profile is defined in configuration and cannot be modified at runtime"));

    @Autowired
    private ReadOnlyUserStore userStore;

    // ---- email claims ----

    @Operation(operationId = "addEmail", summary = "Add an email claim to the current user's profile")
    @PostMapping("/emails")
    public ResponseEntity<?> addEmail(@RequestBody Map<String, String> body) {
        if (!(userStore instanceof UserStore mutable)) return NOT_MUTABLE;
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }
        email = email.strip().toLowerCase();
        String currentUser = currentUsername();
        try {
            mutable.addEmail(currentUser, email);
        } catch (LockedByConfigException e) {
            return LOCKED_BY_CONFIG;
        } catch (EmailConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Email is already registered to another user"));
        }
        return ResponseEntity.ok(Map.of("email", email));
    }

    @Operation(operationId = "removeEmail", summary = "Remove an email claim from the current user's profile")
    @DeleteMapping("/emails/{email}")
    public ResponseEntity<?> removeEmail(@PathVariable String email) {
        if (!(userStore instanceof UserStore mutable)) return NOT_MUTABLE;
        try {
            mutable.removeEmail(currentUsername(), email.toLowerCase());
        } catch (LockedByConfigException e) {
            return LOCKED_BY_CONFIG;
        } catch (LockedEmailException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot remove an email address locked by the identity provider"));
        }
        return ResponseEntity.noContent().build();
    }

    // ---- SCM identity claims ----

    @Operation(operationId = "addScmIdentity", summary = "Add an SCM identity to the current user's profile")
    @PostMapping("/identities")
    public ResponseEntity<?> addScmIdentity(@RequestBody Map<String, String> body) {
        if (!(userStore instanceof UserStore mutable)) return NOT_MUTABLE;
        String provider = body.get("provider");
        String scmUsername = body.get("username");
        if (provider == null || provider.isBlank() || scmUsername == null || scmUsername.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "provider and username are required"));
        }
        provider = provider.strip();
        scmUsername = scmUsername.strip();

        String currentUser = currentUsername();
        try {
            mutable.addScmIdentity(currentUser, provider, scmUsername);
        } catch (LockedByConfigException e) {
            return LOCKED_BY_CONFIG;
        } catch (ScmIdentityConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "SCM identity is already claimed by another user"));
        }
        return ResponseEntity.ok(Map.of("provider", provider, "username", scmUsername));
    }

    @Operation(operationId = "removeScmIdentity", summary = "Remove an SCM identity from the current user's profile")
    @DeleteMapping("/identities/{provider}/{scmUsername}")
    public ResponseEntity<?> removeScmIdentity(@PathVariable String provider, @PathVariable String scmUsername) {
        if (!(userStore instanceof UserStore mutable)) return NOT_MUTABLE;
        try {
            mutable.removeScmIdentity(currentUsername(), provider, scmUsername);
        } catch (LockedByConfigException e) {
            return LOCKED_BY_CONFIG;
        }
        return ResponseEntity.noContent().build();
    }

    // ---- API key management ----

    @Operation(operationId = "generateApiKey", summary = "Generate a proxy-native API key for the current user")
    @PostMapping("/api-key")
    public ResponseEntity<?> generateApiKey() {
        if (!(userStore instanceof UserStore mutable)) return NOT_MUTABLE;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SELF_CERTIFY"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "API key generation requires the SELF_CERTIFY role"));
        }
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String key = "gp_" + HexFormat.of().formatHex(raw);
        mutable.setApiKey(currentUsername(), sha256(key));
        return ResponseEntity.ok(Map.of("key", key));
    }

    @Operation(operationId = "revokeApiKey", summary = "Revoke the current user's proxy API key")
    @DeleteMapping("/api-key")
    public ResponseEntity<?> revokeApiKey() {
        if (!(userStore instanceof UserStore mutable)) return NOT_MUTABLE;
        mutable.revokeApiKey(currentUsername());
        return ResponseEntity.noContent().build();
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
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
