package org.finos.gitproxy.provider;

import java.net.URI;
import lombok.Builder;

public class GenericProxyProvider extends AbstractGitProxyProvider {

    @Builder
    GenericProxyProvider(String name, String type, URI uri, String basePath) {
        super(name, type != null ? type : name, uri, basePath);
    }
}
