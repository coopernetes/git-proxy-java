package org.finos.gitproxy.git;

import static org.finos.gitproxy.git.GitClientUtils.AnsiColor.*;
import static org.finos.gitproxy.git.GitClientUtils.SymbolCodes.*;
import static org.finos.gitproxy.git.GitClientUtils.sym;

import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.finos.gitproxy.db.RepoRegistry;
import org.finos.gitproxy.db.model.AccessRule;
import org.finos.gitproxy.db.model.PushStep;
import org.finos.gitproxy.db.model.StepStatus;
import org.finos.gitproxy.provider.GitProxyProvider;
import org.finos.gitproxy.servlet.filter.UrlRuleFilter;

/**
 * Pre-receive hook that enforces URL allow/deny rules in store-and-forward mode. Mirrors the behaviour of
 * {@link org.finos.gitproxy.servlet.filter.UrlRuleAggregateFilter} for the JGit hook chain.
 *
 * <p>Evaluation order:
 *
 * <ol>
 *   <li>Config deny rules — any match immediately blocks the push.
 *   <li>DB deny rules — any match immediately blocks the push.
 *   <li>Config allow rules — first match permits the push.
 *   <li>DB allow rules — first match permits the push.
 *   <li>If allow rules exist but none match — push is blocked.
 *   <li>If no allow rules are configured at all — push is permitted (open mode).
 * </ol>
 *
 * <p>When {@code urlRuleFilters} is empty and {@code repoRegistry} is {@code null}, the hook records a PASS
 * unconditionally (open/permissive mode — no rules configured).
 */
@Slf4j
public class RepositoryUrlRuleHook implements GitProxyHook {

    private static final int ORDER = 100;

    private final List<UrlRuleFilter> urlRuleFilters;
    private final RepoRegistry repoRegistry;
    private final GitProxyProvider provider;
    private final ValidationContext validationContext;
    private final PushContext pushContext;

    /** Open-mode constructor — no rules configured; always passes. Used in tests and simple setups. */
    public RepositoryUrlRuleHook(PushContext pushContext) {
        this(List.of(), null, null, null, pushContext);
    }

    public RepositoryUrlRuleHook(
            List<UrlRuleFilter> urlRuleFilters,
            RepoRegistry repoRegistry,
            GitProxyProvider provider,
            ValidationContext validationContext,
            PushContext pushContext) {
        this.urlRuleFilters = urlRuleFilters != null ? urlRuleFilters : List.of();
        this.repoRegistry = repoRegistry;
        this.provider = provider;
        this.validationContext = validationContext;
        this.pushContext = pushContext;
    }

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        // Open mode: no rules configured at all
        if (urlRuleFilters.isEmpty() && repoRegistry == null) {
            log.debug("No URL rules configured — allowing push (open mode)");
            recordPass();
            return;
        }

        String repoSlug = rp.getRepository().getConfig().getString("gitproxy", null, "repoSlug");
        if (repoSlug == null || repoSlug.isBlank()) {
            log.warn("No repoSlug in repo config — cannot evaluate URL rules, blocking push (fail-closed)");
            blockPush(rp, commands, "Repository path unavailable");
            return;
        }

        // Parse owner and name from /owner/name slug
        String[] parts = repoSlug.split("/", 4);
        String owner = parts.length >= 2 ? parts[1] : null;
        String name = parts.length >= 3 ? parts[2] : null;
        String normSlug = (owner != null && name != null) ? owner + "/" + name : strip(repoSlug);

        String providerId = provider != null ? provider.getProviderId() : null;

        // ── Step 1: deny rules ─────────────────────────────────────────────────
        for (UrlRuleFilter f : urlRuleFilters) {
            if (f.getAccess() == AccessRule.Access.DENY && f.matchesRepo(normSlug, owner, name)) {
                log.debug("Push blocked by config deny rule: {}", f);
                blockPush(rp, commands, "Repository denied by configuration rule");
                return;
            }
        }
        if (repoRegistry != null && providerId != null) {
            for (AccessRule rule : repoRegistry.findEnabledForProvider(providerId)) {
                if (rule.getAccess() == AccessRule.Access.DENY && matchesDbRule(rule, normSlug, owner, name)) {
                    log.debug("Push blocked by DB deny rule: id={}", rule.getId());
                    blockPush(rp, commands, "Repository denied by access rule");
                    return;
                }
            }
        }

        // ── Step 2: allow rules ────────────────────────────────────────────────
        List<UrlRuleFilter> configAllow = urlRuleFilters.stream()
                .filter(f -> f.getAccess() == AccessRule.Access.ALLOW)
                .toList();
        List<AccessRule> dbAllow = List.of();
        if (repoRegistry != null && providerId != null) {
            dbAllow = repoRegistry.findEnabledForProvider(providerId).stream()
                    .filter(r -> r.getAccess() == AccessRule.Access.ALLOW)
                    .toList();
        }

        if (configAllow.isEmpty() && dbAllow.isEmpty()) {
            // No allow rules configured anywhere — open/permissive mode
            log.debug("No allow rules configured for {} — allowing push (open mode)", providerId);
            recordPass();
            return;
        }

        for (UrlRuleFilter f : configAllow) {
            if (f.matchesRepo(normSlug, owner, name)) {
                log.debug("Push allowed by config allow rule: {}", f);
                recordPass();
                return;
            }
        }
        for (AccessRule rule : dbAllow) {
            if (matchesDbRule(rule, normSlug, owner, name)) {
                log.debug("Push allowed by DB allow rule: id={}", rule.getId());
                recordPass();
                return;
            }
        }

        log.debug("Push to {}/{} matched no allow rule — blocking", providerId, normSlug);
        blockPush(rp, commands, "Repository is not in the allow list");
    }

    private void blockPush(ReceivePack rp, Collection<ReceiveCommand> commands, String reason) {
        String detail = GitClientUtils.format(
                sym(NO_ENTRY) + "  Push Blocked - Repository Not Allowed",
                sym(CROSS_MARK)
                        + "  "
                        + reason
                        + ".\n\nContact an administrator to add this repository to the allow rules.",
                RED,
                null);
        if (validationContext != null) {
            validationContext.addIssue("RepositoryUrlRuleHook", reason, detail);
        } else {
            rp.sendMessage(detail);
            for (ReceiveCommand cmd : commands) {
                if (cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
                    cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, reason);
                }
            }
        }
        pushContext.addStep(PushStep.builder()
                .stepName("checkUrlRules")
                .stepOrder(ORDER)
                .status(StepStatus.FAIL)
                .content(reason)
                .build());
    }

    private void recordPass() {
        pushContext.addStep(PushStep.builder()
                .stepName("checkUrlRules")
                .stepOrder(ORDER)
                .status(StepStatus.PASS)
                .build());
    }

    private static boolean matchesDbRule(AccessRule rule, String slug, String owner, String name) {
        if (rule.getSlug() != null) return matchPattern(strip(rule.getSlug()), slug);
        if (rule.getOwner() != null) return matchPattern(strip(rule.getOwner()), owner);
        if (rule.getName() != null) return matchPattern(strip(rule.getName()), name);
        return false;
    }

    private static String strip(String s) {
        return (s != null && s.startsWith("/")) ? s.substring(1) : s;
    }

    private static boolean matchPattern(String pattern, String value) {
        if (pattern == null || value == null) return false;
        if (pattern.equals(value)) return true;
        if (pattern.startsWith("regex:")) {
            return java.util.regex.Pattern.compile(pattern.substring(6))
                    .matcher(value)
                    .matches();
        }
        if (pattern.contains("*") || pattern.contains("?") || pattern.contains("[")) {
            return java.nio.file.FileSystems.getDefault()
                    .getPathMatcher("glob:" + pattern)
                    .matches(java.nio.file.Paths.get(value));
        }
        return false;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getName() {
        return "RepositoryUrlRuleHook";
    }
}
