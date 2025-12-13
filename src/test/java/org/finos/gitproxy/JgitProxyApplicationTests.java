package org.finos.gitproxy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import spring.GitProxyServerApplication;

// @Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = GitProxyServerApplication.class)
class JgitProxyApplicationTests {

    @Test
    void contextLoads() {}
}
