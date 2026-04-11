package org.finos.gitproxy.provider;

import java.net.URI;
import lombok.Builder;

public class GenericProxyProvider extends AbstractGitProxyProvider {

    private final int blockedInfoRefsStatus;

    @Builder
    GenericProxyProvider(String name, String type, URI uri, String basePath, Integer blockedInfoRefsStatus) {
        super(name, type != null ? type : name, uri, basePath);
        this.blockedInfoRefsStatus = blockedInfoRefsStatus != null ? blockedInfoRefsStatus : 403;
    }

    @Override
    public int getBlockedInfoRefsStatus() {
        return blockedInfoRefsStatus;
    }
}
