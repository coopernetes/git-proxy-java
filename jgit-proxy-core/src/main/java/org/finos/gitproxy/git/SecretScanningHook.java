package org.finos.gitproxy.git;

import static org.finos.gitproxy.git.GitClient.AnsiColor.*;
import static org.finos.gitproxy.git.GitClient.SymbolCodes.*;
import static org.finos.gitproxy.git.GitClient.color;
import static org.finos.gitproxy.git.GitClient.sym;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.finos.gitproxy.config.CommitConfig;
import org.finos.gitproxy.db.model.PushStep;
import org.finos.gitproxy.db.model.StepStatus;

/**
 * Pre-receive hook that scans the diff of incoming pushes for secrets using gitleaks. Runs after
 * {@link DiffGenerationHook} in the store-and-forward pipeline.
 *
 * <p>Each ref's diff is generated independently using {@link CommitInspectionService#getFormattedDiff} and piped to
 * gitleaks. Findings are reported via the sideband channel and recorded in the shared {@link ValidationContext}.
 *
 * <p>If the gitleaks binary is unavailable or execution fails, the hook logs a warning and continues (fail-open).
 * Pushes are never blocked because the scanner is misconfigured.
 */
@Slf4j
@RequiredArgsConstructor
public class SecretScanningHook implements PreReceiveHook {

    private static final String STEP_NAME = "scanSecrets";

    private final CommitConfig.SecretScanningConfig config;
    private final ValidationContext validationContext;
    private final PushContext pushContext;
    private final GitleaksRunner runner;

    /** Convenience constructor; creates a default {@link GitleaksRunner} instance. */
    public SecretScanningHook(
            CommitConfig.SecretScanningConfig config, ValidationContext validationContext, PushContext pushContext) {
        this(config, validationContext, pushContext, new GitleaksRunner());
    }

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        if (!config.isEnabled()) {
            log.debug("Secret scanning disabled — skipping");
            return;
        }

        rp.sendMessage(color(CYAN, "[git-proxy] " + sym(KEY) + "  Scanning for secrets..."));

        Repository repo = rp.getRepository();
        List<String> logs = new ArrayList<>();
        boolean anyFailed = false;

        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) {
                continue;
            }

            try {
                String diff = CommitInspectionService.getFormattedDiff(
                        repo, cmd.getOldId().name(), cmd.getNewId().name());

                if (diff.isBlank()) {
                    rp.sendMessage(color(
                            GREEN,
                            "[git-proxy]   " + sym(HEAVY_CHECK_MARK) + "  " + cmd.getRefName()
                                    + " — empty diff, nothing to scan"));
                    logs.add("SKIP: " + cmd.getRefName() + " — empty diff");
                    continue;
                }

                Optional<List<GitleaksRunner.Finding>> result = runner.scan(diff, config);

                if (result.isEmpty()) {
                    // Fail-open: scanner unavailable or errored
                    rp.sendMessage(color(
                            YELLOW,
                            "[git-proxy]   " + sym(WARNING) + "  " + cmd.getRefName()
                                    + " — secret scanner unavailable, skipped"));
                    logs.add("WARN: " + cmd.getRefName() + " — scanner unavailable");
                    continue;
                }

                List<GitleaksRunner.Finding> findings = result.get();
                if (findings.isEmpty()) {
                    rp.sendMessage(color(
                            GREEN, "[git-proxy]   " + sym(HEAVY_CHECK_MARK) + "  " + cmd.getRefName() + " — clean"));
                    logs.add("PASS: " + cmd.getRefName());
                } else {
                    for (GitleaksRunner.Finding finding : findings) {
                        String message = finding.toMessage();
                        validationContext.addIssue(STEP_NAME, message, "ref: " + cmd.getRefName());
                        rp.sendMessage(color(RED, "[git-proxy]   " + sym(CROSS_MARK) + "  " + message));
                        logs.add("FAIL: " + message);
                    }
                    anyFailed = true;
                }

            } catch (Exception e) {
                log.error("Failed to scan secrets for {}", cmd.getRefName(), e);
                rp.sendMessage(
                        color(YELLOW, "[git-proxy]   " + sym(WARNING) + "  Could not scan secrets: " + e.getMessage()));
                logs.add("ERROR: " + cmd.getRefName() + " — " + e.getMessage());
            }
        }

        if (!anyFailed) {
            pushContext.addStep(PushStep.builder()
                    .stepName(STEP_NAME)
                    .status(StepStatus.PASS)
                    .logs(logs)
                    .build());
        }
    }
}
