package org.finos.gitproxy.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.regex.Pattern;
import org.finos.gitproxy.config.CommitConfig;
import org.finos.gitproxy.git.Commit;
import org.finos.gitproxy.git.Contributor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuthorEmailCheckTest {

    private static CommitConfig configWithDomainAllow(String pattern) {
        return CommitConfig.builder()
                .author(CommitConfig.AuthorConfig.builder()
                        .email(CommitConfig.EmailConfig.builder()
                                .domain(CommitConfig.DomainConfig.builder()
                                        .allow(Pattern.compile(pattern))
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private static CommitConfig configWithLocalBlock(String pattern) {
        return CommitConfig.builder()
                .author(CommitConfig.AuthorConfig.builder()
                        .email(CommitConfig.EmailConfig.builder()
                                .local(CommitConfig.LocalConfig.builder()
                                        .block(Pattern.compile(pattern))
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private static Commit commitWithEmail(String email) {
        return Commit.builder()
                .sha("abc123")
                .author(Contributor.builder().name("Test User").email(email).build())
                .committer(Contributor.builder().name("Test User").email(email).build())
                .message("Test commit")
                .build();
    }

    @Test
    void defaultConfig_anyValidEmail_noViolations() {
        AuthorEmailCheck check = new AuthorEmailCheck(CommitConfig.defaultConfig());
        assertTrue(check.check(List.of(commitWithEmail("anyone@anywhere.io"))).isEmpty());
    }

    @Test
    void validEmail_matchingAllowDomain_noViolations() {
        AuthorEmailCheck check = new AuthorEmailCheck(configWithDomainAllow("example\\.com$"));
        assertTrue(check.check(List.of(commitWithEmail("dev@example.com"))).isEmpty());
    }

    @Test
    void domainNotInAllowList_returnsViolation() {
        AuthorEmailCheck check = new AuthorEmailCheck(configWithDomainAllow("company\\.com$"));
        List<Violation> violations = check.check(List.of(commitWithEmail("dev@gmail.com")));
        assertFalse(violations.isEmpty());
        assertTrue(violations.get(0).reason().contains("domain not allowed"));
    }

    @Test
    void blockedLocalPart_returnsViolation() {
        AuthorEmailCheck check = new AuthorEmailCheck(configWithLocalBlock("^(noreply|bot)$"));
        List<Violation> violations = check.check(List.of(commitWithEmail("noreply@example.com")));
        assertFalse(violations.isEmpty());
        assertTrue(violations.get(0).reason().contains("blocked local part"));
    }

    @Test
    void blockedLocalPart_otherEmailsStillPass() {
        AuthorEmailCheck check = new AuthorEmailCheck(configWithLocalBlock("^(noreply|bot)$"));
        assertTrue(check.check(List.of(commitWithEmail("dev@example.com"))).isEmpty());
    }

    @Test
    void invalidEmailFormat_returnsViolation() {
        AuthorEmailCheck check = new AuthorEmailCheck(CommitConfig.defaultConfig());
        assertFalse(check.check(List.of(commitWithEmail("notanemail"))).isEmpty());
    }

    @Test
    void emptyEmail_returnsViolation() {
        AuthorEmailCheck check = new AuthorEmailCheck(CommitConfig.defaultConfig());
        List<Violation> violations = check.check(List.of(commitWithEmail("")));
        assertFalse(violations.isEmpty());
        assertTrue(violations.get(0).reason().contains("empty email"));
    }

    @Test
    void multipleCommits_deduplicatesEmails() {
        // Two commits with the same bad email should produce only one violation
        AuthorEmailCheck check = new AuthorEmailCheck(configWithDomainAllow("example\\.com$"));
        Commit c1 = commitWithEmail("bad@gmail.com");
        Commit c2 = commitWithEmail("bad@gmail.com");
        assertEquals(1, check.check(List.of(c1, c2)).size());
    }

    @Test
    void multipleCommits_differentBadEmails_eachViolationReported() {
        AuthorEmailCheck check = new AuthorEmailCheck(configWithDomainAllow("example\\.com$"));
        Commit c1 = commitWithEmail("a@gmail.com");
        Commit c2 = commitWithEmail("b@yahoo.com");
        assertEquals(2, check.check(List.of(c1, c2)).size());
    }

    @Test
    void emptyCommitList_noViolations() {
        AuthorEmailCheck check = new AuthorEmailCheck(CommitConfig.defaultConfig());
        assertTrue(check.check(List.of()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@example.com", "dev+alias@example.com", "a.b.c@sub.example.com"})
    void variousValidEmails_allowDomainSet_noViolations(String email) {
        AuthorEmailCheck check = new AuthorEmailCheck(configWithDomainAllow("example\\.com$"));
        assertTrue(check.check(List.of(commitWithEmail(email))).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"noreply@example.com", "bot@example.com"})
    void blockedLocalParts_allRejected(String email) {
        AuthorEmailCheck check = new AuthorEmailCheck(configWithLocalBlock("^(noreply|bot)$"));
        assertFalse(check.check(List.of(commitWithEmail(email))).isEmpty());
    }
}
