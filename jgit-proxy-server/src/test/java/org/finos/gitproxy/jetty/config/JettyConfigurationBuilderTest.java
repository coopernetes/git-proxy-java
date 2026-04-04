package org.finos.gitproxy.jetty.config;

import static org.junit.jupiter.api.Assertions.*;

import org.finos.gitproxy.approval.AutoApprovalGateway;
import org.finos.gitproxy.approval.UiApprovalGateway;
import org.finos.gitproxy.db.PushStoreFactory;
import org.junit.jupiter.api.Test;

class JettyConfigurationBuilderTest {

    // ---- buildApprovalGateway ----

    @Test
    void buildApprovalGateway_defaultConfig_returnsAutoApprovalGateway() {
        var builder = new JettyConfigurationBuilder(configWithApprovalMode("auto"));
        var gateway = builder.buildApprovalGateway(PushStoreFactory.inMemory());
        assertInstanceOf(AutoApprovalGateway.class, gateway);
    }

    @Test
    void buildApprovalGateway_uiMode_returnsUiApprovalGateway() {
        var builder = new JettyConfigurationBuilder(configWithApprovalMode("ui"));
        var gateway = builder.buildApprovalGateway(PushStoreFactory.inMemory());
        assertInstanceOf(UiApprovalGateway.class, gateway);
    }

    @Test
    void buildApprovalGateway_unknownMode_fallsBackToAuto() {
        var builder = new JettyConfigurationBuilder(configWithApprovalMode("bogus"));
        var gateway = builder.buildApprovalGateway(PushStoreFactory.inMemory());
        assertInstanceOf(AutoApprovalGateway.class, gateway);
    }

    // ---- helpers ----

    private static GitProxyConfig configWithApprovalMode(String mode) {
        var config = new GitProxyConfig();
        config.getServer().setApprovalMode(mode);
        return config;
    }
}
