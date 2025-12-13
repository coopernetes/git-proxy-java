package org.finos.gitproxy;

import jakarta.servlet.DispatcherType;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.finos.gitproxy.config.JettyConfigurationBuilder;
import org.finos.gitproxy.config.JettyConfigurationLoader;
import org.finos.gitproxy.provider.GitProxyProvider;
import org.finos.gitproxy.servlet.GitProxyServlet;
import org.finos.gitproxy.servlet.filter.GitProxyFilter;

/**
 * Main application class for the Jetty-based GitProxy server. This application reads configuration from YAML files and
 * bootstraps the Jetty server with appropriate providers and filters.
 */
@Slf4j
public class GitProxyApplication {
    public static void main(String[] args) throws Exception {
        // Load configuration
        JettyConfigurationLoader configLoader = new JettyConfigurationLoader();
        JettyConfigurationBuilder configBuilder = new JettyConfigurationBuilder(configLoader);

        // Build providers from configuration
        List<GitProxyProvider> providers = configBuilder.buildProviders();

        if (providers.isEmpty()) {
            log.warn("No providers configured, server will not handle any requests");
        }

        // Setup Jetty server
        var threadPool = new QueuedThreadPool();
        threadPool.setName("server");
        var server = new Server(threadPool);

        // Configure server port
        int port = configLoader.getServerPort();
        var connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);

        var context = new ServletContextHandler("/", false, false);

        // Setup each provider with its filters and servlet
        for (GitProxyProvider provider : providers) {
            log.info("Configuring provider: {} at {}", provider.getName(), provider.servletMapping());

            String urlPattern = provider.servletMapping();

            // Build and add filters for this provider
            List<GitProxyFilter> filters = configBuilder.buildFiltersForProvider(provider);

            // Sort filters by order
            filters.sort(Comparator.comparingInt(GitProxyFilter::getOrder));

            for (GitProxyFilter filter : filters) {
                var filterHolder = new FilterHolder(filter);
                filterHolder.setAsyncSupported(true);
                context.addFilter(filterHolder, urlPattern, EnumSet.of(DispatcherType.REQUEST));
                log.debug(
                        "Added filter {} (order={}) for {}",
                        filter.getClass().getSimpleName(),
                        filter.getOrder(),
                        provider.getName());
            }

            // Add proxy servlet for this provider
            var proxyServlet = new GitProxyServlet();
            var proxyServletHolder = new ServletHolder(proxyServlet);
            proxyServletHolder.setInitParameter("proxyTo", provider.getUri().toString());
            proxyServletHolder.setInitParameter("prefix", provider.servletPath());
            proxyServletHolder.setInitParameter("hostHeader", provider.getUri().getHost());
            proxyServletHolder.setInitParameter("preserveHost", "false");
            context.addServlet(proxyServletHolder, urlPattern);
            log.info("Added servlet for {} proxying to {}", provider.getName(), provider.getUri());
        }

        server.setHandler(context);

        server.start();
        log.info("Server started at http://localhost:{}/", port);
        System.out.println("Server started at http://localhost:" + port + "/");
    }
}
