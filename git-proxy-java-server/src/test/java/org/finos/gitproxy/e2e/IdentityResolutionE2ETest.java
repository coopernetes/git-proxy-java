package org.finos.gitproxy.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.finos.gitproxy.approval.UiApprovalGateway;
import org.finos.gitproxy.config.CommitConfig;
import org.finos.gitproxy.db.model.Attestation;
import org.finos.gitproxy.permission.InMemoryRepoPermissionStore;
import org.finos.gitproxy.permission.RepoPermission;
import org.finos.gitproxy.permission.RepoPermissionService;
import org.finos.gitproxy.service.ConfigPushIdentityResolver;
import org.finos.gitproxy.servlet.filter.IdentityVerificationFilter;
import org.finos.gitproxy.user.StaticUserStore;
import org.finos.gitproxy.user.UserEntry;
import org.junit.jupiter.api.*;

/**
 * End-to-end tests for identity resolution in transparent proxy mode.
 *
 * <p>Two scenarios are covered, mirroring {@code test/demo-proxy-identity.sh}:
 *
 * <ul>
 *   <li><strong>Linked user</strong> — HTTP Basic-auth username maps to a registered proxy account. The push is blocked
 *       pending review (not rejected by identity check), and can be approved.
 *   <li><strong>Unlinked user</strong> — HTTP Basic-auth username is not in the user store. The push is rejected
 *       immediately with an "Identity Not Linked" error.
 * </ul>
 *
 * <p>A third scenario validates {@link IdentityVerificationFilter} in STRICT mode: commit email must match a registered
 * email for the push user, otherwise the push is rejected.
 *
 * <p>Uses {@link ConfigPushIdentityResolver} which maps the HTTP Basic-auth username directly to a proxy user — no
 * external SCM API calls required.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdentityResolutionE2ETest {

    static final String LINKED_USER = "dev1";
    static final String UNLINKED_USER = "unlinked-user";

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

        // Seed permission grant for the linked user
        permissionStore.save(RepoPermission.builder()
                .username(LINKED_USER)
                .provider("gitea-e2e")
                .path("/" + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO)
                .pathType(RepoPermission.PathType.LITERAL)
                .operations(RepoPermission.Operations.PUSH)
                .build());

        // Registered proxy user with a known email
        var userStore = new StaticUserStore(List.of(UserEntry.builder()
                .username(LINKED_USER)
                .emails(List.of(GiteaContainer.VALID_AUTHOR_EMAIL))
                .scmIdentities(List.of())
                .build()));
        var identityResolver = new ConfigPushIdentityResolver(userStore);

        proxy = new JettyProxyFixture(gitea.getBaseUri(), UiApprovalGateway::new, identityResolver, permissionService);
        tempDir = Files.createTempDirectory("git-proxy-java-ident-e2e-");
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (proxy != null) proxy.close();
        if (gitea != null) gitea.stop();
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private String urlFor(String username) {
        String creds = URLEncoder.encode(username, StandardCharsets.UTF_8)
                + ":"
                + URLEncoder.encode(GiteaContainer.ADMIN_PASSWORD, StandardCharsets.UTF_8);
        return "http://" + creds + "@localhost:" + proxy.getPort()
                + "/proxy/localhost/"
                + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + ".git";
    }

    // ── Scenario A: linked user ───────────────────────────────────────────────────

    @Test
    @Order(1)
    void linkedUser_blockedPendingReview_then_approved() throws Exception {
        // Clone using the linked user's credentials
        GitHelper git = new GitHelper(tempDir);
        Path repo = git.clone(urlFor(LINKED_USER), "ident-linked");
        // Commit with the registered email — identity check should pass
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.writeAndStage(repo, "test-file.txt", "identity test - " + Instant.now());
        git.commit(repo, "feat: identity demo - linked user");

        // First push: clean push → blocked pending review (not rejected outright)
        var firstPush = git.pushWithResult(repo);
        assertFalse(firstPush.succeeded(), "first push should be blocked pending review");
        String pushId = firstPush.extractPushId();
        assertNotNull(pushId, "push ID should be present in blocked message");

        // Verify push record status
        var record = proxy.getPushStore().findById(pushId);
        assertTrue(record.isPresent(), "push record should exist");

        // Approve and re-push
        proxy.getPushStore()
                .approve(
                        pushId,
                        Attestation.builder()
                                .pushId(pushId)
                                .type(Attestation.Type.APPROVAL)
                                .reviewerUsername("e2e-reviewer")
                                .reason("identity demo approval")
                                .build());

        var rePush = git.pushWithResult(repo);
        assertTrue(rePush.succeeded(), "re-push after approval should succeed. Output:\n" + rePush.output());
    }

    // ── Scenario B: unlinked user ─────────────────────────────────────────────────

    @Test
    @Order(2)
    void unlinkedUser_blocked_with_identity_not_linked() throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repo = git.clone(urlFor(UNLINKED_USER), "ident-unlinked");
        git.setAuthor(repo, "Unlinked Developer", GiteaContainer.VALID_AUTHOR_EMAIL);
        git.writeAndStage(repo, "test-file.txt", "unlinked identity test - " + Instant.now());
        git.commit(repo, "feat: identity demo - unlinked user");

        var result = git.pushWithResult(repo);
        assertFalse(result.succeeded(), "push by unlinked user should be blocked");
        assertTrue(
                result.output().contains("Identity Not Linked"),
                "output should mention 'Identity Not Linked'. Output:\n" + result.output());
    }

    // ── Scenario C: email mismatch (STRICT mode) ──────────────────────────────────

    @Test
    @Order(3)
    void linkedUser_wrongCommitEmail_strictMode_rejected() throws Exception {
        // Spin up a separate fixture with STRICT identity verification mode
        var strictPermissionStore = new InMemoryRepoPermissionStore();
        strictPermissionStore.save(RepoPermission.builder()
                .username(LINKED_USER)
                .provider("gitea-e2e")
                .path("/" + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO)
                .pathType(RepoPermission.PathType.LITERAL)
                .operations(RepoPermission.Operations.PUSH)
                .build());
        var strictPermissionService = new RepoPermissionService(strictPermissionStore);
        var strictUserStore = new StaticUserStore(List.of(UserEntry.builder()
                .username(LINKED_USER)
                .emails(List.of(GiteaContainer.VALID_AUTHOR_EMAIL))
                .scmIdentities(List.of())
                .build()));
        var strictResolver = new ConfigPushIdentityResolver(strictUserStore);

        try (var strictProxy = new JettyProxyFixture(
                gitea.getBaseUri(),
                UiApprovalGateway::new,
                strictResolver,
                strictPermissionService,
                CommitConfig.IdentityVerificationMode.STRICT)) {

            String strictUrl = URLEncoder.encode(LINKED_USER, StandardCharsets.UTF_8)
                    + ":"
                    + URLEncoder.encode(GiteaContainer.ADMIN_PASSWORD, StandardCharsets.UTF_8);
            strictUrl = "http://" + strictUrl + "@localhost:" + strictProxy.getPort()
                    + "/proxy/localhost/"
                    + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + ".git";

            GitHelper git = new GitHelper(tempDir);
            Path repo = git.clone(strictUrl, "ident-wrong-email");
            // Commit with an email NOT registered to dev1
            git.setAuthor(repo, "Dev One", "wrong-email@other-domain.com");
            git.writeAndStage(repo, "test-file.txt", "email mismatch test - " + Instant.now());
            git.commit(repo, "feat: commit from mismatched email");

            var result = git.pushWithResult(repo);
            assertFalse(result.succeeded(), "push with mismatched commit email should be rejected in STRICT mode");
            assertTrue(
                    result.output().contains("Commit Identity")
                            || result.output().contains("identity"),
                    "output should mention identity issue. Output:\n" + result.output());
        }
    }
}
