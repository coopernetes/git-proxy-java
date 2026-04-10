package org.finos.gitproxy.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.finos.gitproxy.approval.UiApprovalGateway;
import org.finos.gitproxy.permission.InMemoryRepoPermissionStore;
import org.finos.gitproxy.permission.RepoPermission;
import org.finos.gitproxy.permission.RepoPermissionService;
import org.finos.gitproxy.service.ConfigPushIdentityResolver;
import org.finos.gitproxy.user.StaticUserStore;
import org.finos.gitproxy.user.UserEntry;
import org.junit.jupiter.api.*;

/**
 * End-to-end tests for the repository permission system in transparent proxy mode.
 *
 * <p>Uses {@link ConfigPushIdentityResolver} (maps HTTP Basic-auth username directly to a proxy user) paired with
 * {@link InMemoryRepoPermissionStore} to exercise
 * {@link org.finos.gitproxy.servlet.filter.CheckUserPushPermissionFilter} without any external SCM API calls.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Literal path grants — exact {@code /owner/repo} match
 *   <li>Glob path grants — {@code /owner/*} wildcard
 *   <li>Regex path grants — full Java regex against the path
 *   <li>Fail-closed semantics — no grant → push blocked
 *   <li>Unregistered user → push blocked with "Identity Not Linked"
 * </ul>
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionE2ETest {

    static final String PROXY_USER = "dev1";
    static final String UNREGISTERED_USER = "unknown-user";

    static GiteaContainer gitea;
    static JettyProxyFixture proxy;
    static InMemoryRepoPermissionStore permissionStore;
    static Path tempDir;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        gitea = new GiteaContainer();
        gitea.start();
        gitea.createAdminUser();
        gitea.createTestRepo();

        permissionStore = new InMemoryRepoPermissionStore();
        var permissionService = new RepoPermissionService(permissionStore);

        var userStore = new StaticUserStore(List.of(UserEntry.builder()
                .username(PROXY_USER)
                .emails(List.of(GiteaContainer.VALID_AUTHOR_EMAIL))
                .scmIdentities(List.of())
                .build()));
        var identityResolver = new ConfigPushIdentityResolver(userStore);

        proxy = new JettyProxyFixture(gitea.getBaseUri(), UiApprovalGateway::new, identityResolver, permissionService);
        tempDir = Files.createTempDirectory("git-proxy-java-perm-e2e-");
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (proxy != null) proxy.close();
        if (gitea != null) gitea.stop();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    /** URL with {@code dev1} credentials — the registered proxy user. */
    private String authorisedUrl() {
        String creds = URLEncoder.encode(PROXY_USER, StandardCharsets.UTF_8)
                + ":"
                + URLEncoder.encode(GiteaContainer.ADMIN_PASSWORD, StandardCharsets.UTF_8);
        return "http://" + creds + "@localhost:" + proxy.getPort()
                + "/proxy/localhost/"
                + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + ".git";
    }

    /** URL with an unregistered username — should always produce "Identity Not Linked". */
    private String unauthorisedUrl() {
        String creds = URLEncoder.encode(UNREGISTERED_USER, StandardCharsets.UTF_8)
                + ":"
                + URLEncoder.encode(GiteaContainer.ADMIN_PASSWORD, StandardCharsets.UTF_8);
        return "http://" + creds + "@localhost:" + proxy.getPort()
                + "/proxy/localhost/"
                + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + ".git";
    }

    private GitHelper.PushResult cloneCommitPush(String url, String dirSuffix) throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repo = git.clone(url, dirSuffix);
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.writeAndStage(repo, "test-file.txt", dirSuffix + " - " + Instant.now());
        git.commit(repo, "feat: permission test commit");
        return git.pushWithResult(repo);
    }

    // ── tests ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void noGrant_registered_user_blocked() throws Exception {
        // No grants in the store at all — fail-closed should block even a registered user.
        var result = cloneCommitPush(authorisedUrl(), "perm-no-grant");
        assertFalse(result.succeeded(), "push should be blocked when no grants exist (fail-closed)");
        assertTrue(
                result.output().contains("not allowed to push")
                        || result.output().contains("Identity Not Linked")
                        || result.output().contains("Unauthorized"),
                "output should indicate authorization failure. Output:\n" + result.output());
    }

    @Test
    @Order(2)
    void unregistered_user_blocked_with_identity_not_linked() throws Exception {
        var result = cloneCommitPush(unauthorisedUrl(), "perm-unregistered");
        assertFalse(result.succeeded(), "push should be blocked for unregistered user");
        assertTrue(
                result.output().contains("Identity Not Linked"),
                "output should indicate identity not linked. Output:\n" + result.output());
    }

    @Test
    @Order(10)
    void literal_grant_allows_push() throws Exception {
        String path = "/" + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO;
        permissionStore.save(RepoPermission.builder()
                .username(PROXY_USER)
                .provider("gitea-e2e")
                .path(path)
                .pathType(RepoPermission.PathType.LITERAL)
                .operations(RepoPermission.Operations.PUSH)
                .build());

        var result = cloneCommitPush(authorisedUrl(), "perm-literal");
        // The push is valid → should be blocked pending review (not rejected outright)
        assertFalse(result.succeeded(), "valid push should be blocked pending review (not rejected)");
        // A pending-review block contains a push ID, not an authorization error
        assertDoesNotThrow(
                result::extractPushId,
                "a pending-review block should contain a push ID, not an auth error. Output:\n" + result.output());
    }

    @Test
    @Order(11)
    void glob_grant_allows_push() throws Exception {
        // Remove the literal grant first, replace with a glob covering all repos under TEST_ORG
        permissionStore.findAll().stream()
                .filter(p -> PROXY_USER.equals(p.getUsername()))
                .forEach(p -> permissionStore.delete(p.getId()));

        String globPath = "/" + GiteaContainer.TEST_ORG + "/*";
        permissionStore.save(RepoPermission.builder()
                .username(PROXY_USER)
                .provider("gitea-e2e")
                .path(globPath)
                .pathType(RepoPermission.PathType.GLOB)
                .operations(RepoPermission.Operations.PUSH)
                .build());

        var result = cloneCommitPush(authorisedUrl(), "perm-glob");
        assertFalse(result.succeeded(), "valid push should be blocked pending review");
        assertDoesNotThrow(
                result::extractPushId,
                "should be blocked pending review (not auth error). Output:\n" + result.output());
    }

    @Test
    @Order(12)
    void regex_grant_allows_push() throws Exception {
        permissionStore.findAll().stream()
                .filter(p -> PROXY_USER.equals(p.getUsername()))
                .forEach(p -> permissionStore.delete(p.getId()));

        String regexPath = "^/" + GiteaContainer.TEST_ORG + "/.+";
        permissionStore.save(RepoPermission.builder()
                .username(PROXY_USER)
                .provider("gitea-e2e")
                .path(regexPath)
                .pathType(RepoPermission.PathType.REGEX)
                .operations(RepoPermission.Operations.PUSH)
                .build());

        var result = cloneCommitPush(authorisedUrl(), "perm-regex");
        assertFalse(result.succeeded(), "valid push should be blocked pending review");
        assertDoesNotThrow(
                result::extractPushId,
                "should be blocked pending review (not auth error). Output:\n" + result.output());
    }

    @Test
    @Order(20)
    void glob_grant_does_not_match_different_owner() throws Exception {
        permissionStore.findAll().stream()
                .filter(p -> PROXY_USER.equals(p.getUsername()))
                .forEach(p -> permissionStore.delete(p.getId()));

        // Grant only for "other-owner/*" — should not match TEST_ORG
        permissionStore.save(RepoPermission.builder()
                .username(PROXY_USER)
                .provider("gitea-e2e")
                .path("/other-owner/*")
                .pathType(RepoPermission.PathType.GLOB)
                .operations(RepoPermission.Operations.PUSH)
                .build());

        var result = cloneCommitPush(authorisedUrl(), "perm-glob-wrong-owner");
        assertFalse(result.succeeded(), "push should be blocked — grant is for a different owner");
        assertFalse(
                result.output().contains("pending review"),
                "should be an auth denial, not a pending-review block. Output:\n" + result.output());
    }
}
