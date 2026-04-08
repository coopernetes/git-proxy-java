package org.finos.gitproxy.provider;

import java.net.URI;
import lombok.Builder;

public class GenericProxyProvider extends AbstractGitProxyProvider {

    @Builder
    GenericProxyProvider(String name, URI uri, String basePath, String customPath) {
        super(name, uri, basePath, customPath);
    }
}
