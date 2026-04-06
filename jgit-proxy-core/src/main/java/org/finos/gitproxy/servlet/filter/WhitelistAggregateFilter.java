package org.finos.gitproxy.servlet.filter;

import static org.finos.gitproxy.git.GitClientUtils.SymbolCodes.*;
import static org.finos.gitproxy.git.GitClientUtils.sym;
import static org.finos.gitproxy.servlet.filter.WhitelistByUrlFilter.WHITELISTED_BY_ATTRIBUTE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.finos.gitproxy.db.FetchStore;
import org.finos.gitproxy.db.model.FetchRecord;
import org.finos.gitproxy.git.GitClientUtils;
import org.finos.gitproxy.git.GitRequestDetails;
import org.finos.gitproxy.git.HttpOperation;
import org.finos.gitproxy.provider.GitProxyProvider;

/**
 * An aggregate filter that applies a list of {@link WhitelistByUrlFilter} filters to a given request and provider. This
 * filter is used to iterate through the list of whitelists and only block the request if it does not match any of the
 * whitelists.
 */
@Slf4j
@ToString
public class WhitelistAggregateFilter extends AbstractProviderAwareGitProxyFilter {

    private final List<WhitelistByUrlFilter> whitelistFilters;
    private final FetchStore fetchStore;

    // Whitelist aggregate filters must be in the authorization range 50-199
    private static final int MIN_WHITELIST_ORDER = 50;
    private static final int MAX_WHITELIST_ORDER = 199;

    public WhitelistAggregateFilter(
            int order,
            Set<HttpOperation> applicableOperations,
            GitProxyProvider provider,
            List<WhitelistByUrlFilter> whitelistFilters) {
        this(order, applicableOperations, provider, whitelistFilters, null, null);
    }

    public WhitelistAggregateFilter(int order, GitProxyProvider provider, List<WhitelistByUrlFilter> whitelistFilters) {
        this(order, DEFAULT_OPERATIONS, provider, whitelistFilters, null, null);
    }

    public WhitelistAggregateFilter(
            int order, GitProxyProvider provider, List<WhitelistByUrlFilter> whitelistFilters, String pathPrefix) {
        this(order, DEFAULT_OPERATIONS, provider, whitelistFilters, pathPrefix, null);
    }

    public WhitelistAggregateFilter(
            int order,
            GitProxyProvider provider,
            List<WhitelistByUrlFilter> whitelistFilters,
            String pathPrefix,
            FetchStore fetchStore) {
        this(order, DEFAULT_OPERATIONS, provider, whitelistFilters, pathPrefix, fetchStore);
    }

    public WhitelistAggregateFilter(
            int order,
            Set<HttpOperation> applicableOperations,
            GitProxyProvider provider,
            List<WhitelistByUrlFilter> whitelistFilters,
            String pathPrefix,
            FetchStore fetchStore) {
        super(validateWhitelistOrder(order), applicableOperations, provider, pathPrefix != null ? pathPrefix : "");
        this.whitelistFilters = whitelistFilters;
        this.fetchStore = fetchStore;
    }

    /**
     * Validates that the whitelist filter order is within the allowed range.
     *
     * @param order The filter order
     * @return The validated order
     * @throws IllegalArgumentException if order is outside the allowed range
     */
    private static int validateWhitelistOrder(int order) {
        if (order < MIN_WHITELIST_ORDER || order > MAX_WHITELIST_ORDER) {
            throw new IllegalArgumentException(String.format(
                    "Whitelist aggregate filter order must be in the authorization range %d-%d (inclusive), but was %d",
                    MIN_WHITELIST_ORDER, MAX_WHITELIST_ORDER, order));
        }
        return order;
    }

    @Override
    public String getStepName() {
        return "checkWhitelist";
    }

    @Override
    public boolean skipForRefDeletion() {
        return false; // Deletions must still be whitelisted
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        for (WhitelistByUrlFilter filter : whitelistFilters) {
            filter.applyWhitelist(request);
        }
        String whitelistedBy = (String) request.getAttribute(WHITELISTED_BY_ATTRIBUTE);
        var operation = determineOperation(request);
        boolean allowed = whitelistedBy != null;

        if (allowed) {
            log.debug("Whitelisted by {}", whitelistedBy);
        }

        if (operation == HttpOperation.FETCH && fetchStore != null) {
            recordFetch(request, allowed);
        }

        if (!allowed) {
            String action = operation == HttpOperation.PUSH ? "Push" : "Fetch";
            String title = sym(NO_ENTRY) + "  " + action + " Blocked - Repository Not Allowed";
            String verb = operation == HttpOperation.PUSH ? "Pushes to" : "Fetches from";
            String message = verb + " this repository are not permitted.\n"
                    + "\n"
                    + "Contact an administrator to add this repository\n"
                    + "to the allowlist.";
            rejectAndSendError(
                    request,
                    response,
                    "Repository not in allowlist",
                    GitClientUtils.formatForOperation(title, message, GitClientUtils.AnsiColor.RED, operation));
        }
    }

    private void recordFetch(HttpServletRequest request, boolean allowed) {
        try {
            var details = (GitRequestDetails)
                    request.getAttribute(org.finos.gitproxy.servlet.GitProxyServlet.GIT_REQUEST_ATTR);
            if (details == null) return;
            var ref = details.getRepoRef();
            String authHeader = request.getHeader("Authorization");
            String pushUsername = null;
            if (authHeader != null && authHeader.startsWith("Basic ")) {
                String decoded = new String(java.util.Base64.getDecoder().decode(authHeader.substring(6)));
                int colon = decoded.indexOf(':');
                if (colon > 0) pushUsername = decoded.substring(0, colon);
            }
            fetchStore.record(FetchRecord.builder()
                    .provider(provider.getUri().getHost())
                    .owner(ref.getOwner())
                    .repoName(ref.getName())
                    .result(allowed ? FetchRecord.Result.ALLOWED : FetchRecord.Result.BLOCKED)
                    .pushUsername(pushUsername)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record fetch event", e);
        }
    }
}
