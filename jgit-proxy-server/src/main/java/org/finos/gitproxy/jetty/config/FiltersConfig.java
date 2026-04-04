package org.finos.gitproxy.jetty.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** Binds the {@code filters:} block in git-proxy.yml. */
@Data
public class FiltersConfig {

    private List<WhitelistConfig> whitelists = new ArrayList<>();
}
