package org.finos.gitproxy.dashboard.controller;

import jakarta.annotation.Resource;
import java.util.List;
import org.finos.gitproxy.config.ProviderConfigurationSource;
import org.finos.gitproxy.jetty.config.AttestationQuestion;
import org.finos.gitproxy.jetty.config.GitProxyConfig;
import org.finos.gitproxy.jetty.config.ProviderConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProviderController {

    @Resource(name = "providers")
    private ProviderConfigurationSource providers;

    @Autowired
    private GitProxyConfig gitProxyConfig;

    @GetMapping("/api/providers")
    public List<ProviderInfo> list() {
        return providers.getProviders().stream()
                .map(p -> {
                    ProviderConfig cfg = gitProxyConfig.getProviders().get(p.getName());
                    List<AttestationQuestion> questions = cfg != null ? cfg.getAttestationQuestions() : List.of();
                    return new ProviderInfo(
                            p.getName(),
                            p.getProviderId(),
                            p.getUri().toString(),
                            p.getUri().getHost(),
                            "/push" + p.servletPath(),
                            "/proxy" + p.servletPath(),
                            questions);
                })
                .toList();
    }

    public record ProviderInfo(
            String name,
            String id,
            String uri,
            String host,
            String pushPath,
            String proxyPath,
            List<AttestationQuestion> attestationQuestions) {}
}
