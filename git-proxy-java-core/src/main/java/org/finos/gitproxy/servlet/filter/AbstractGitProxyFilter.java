package org.finos.gitproxy.servlet.filter;

import static org.finos.gitproxy.servlet.GitProxyServlet.GIT_REQUEST_ATTR;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.finos.gitproxy.git.GitRequestDetails;
import org.finos.gitproxy.git.HttpOperation;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractGitProxyFilter implements GitProxyFilter {
    protected final int order;
    protected final Set<HttpOperation> applicableOperations;

    /**
     * When {@code true}, skip this filter if a prior filter has already set the push result to
     * {@link GitRequestDetails.GitResult#REJECTED}. Only applies to non-system, non-terminal filters (order 0 through
     * {@code Integer.MAX_VALUE - 100}).
     */
    private boolean failFast = false;

    /**
     * Apply this GitProxyFilter against all Git operations.
     *
     * @param order Order the filter is applied
     */
    public AbstractGitProxyFilter(int order) {
        this.applicableOperations = ALL_OPERATIONS;
        this.order = order;
    }

    /** Enable or disable fail-fast mode. Called by the filter registrar based on server config. */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (failFast && order >= 0 && order < Integer.MAX_VALUE - 100) {
            var details = (GitRequestDetails) ((HttpServletRequest) request).getAttribute(GIT_REQUEST_ATTR);
            if (details != null && details.getResult() == GitRequestDetails.GitResult.REJECTED) {
                chain.doFilter(request, response);
                return;
            }
        }
        GitProxyFilter.super.doFilter(request, response, chain);
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public Predicate<HttpServletRequest> shouldFilter() {
        return (HttpServletRequest request) -> applicableOperations.contains(determineOperation(request));
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" + "order="
                + order + ", appliedOperations="
                + applicableOperations + '}';
    }
}
