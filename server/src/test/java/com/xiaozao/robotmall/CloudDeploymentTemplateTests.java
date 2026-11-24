package com.xiaozao.robotmall;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import static org.junit.jupiter.api.Assertions.*;

/** 验证云端部署模板边界。 */
class CloudDeploymentTemplateTests {
    private final Path deployment = Path.of(System.getProperty("basedir", "."), "deploy");

    /** 验证模板只部署后端且不暴露内部端口。 */
    @Test
    @SuppressWarnings("unchecked")
    void composeIsBackendOnlyAndDoesNotPublishPrivatePorts() throws Exception {
        Map<String, Object> config =
                new Yaml().load(Files.readString(deployment.resolve("compose.yml")));
        assertEquals("robotmall-cloud", config.get("name"));
        Map<String, Map<String, Object>> services =
                (Map<String, Map<String, Object>>) config.get("services");
        assertEquals(Set.of("database", "backend", "gateway"), services.keySet());
        assertFalse(services.get("database").containsKey("ports"));
        assertFalse(services.get("backend").containsKey("ports"));
        assertEquals(java.util.List.of("80:80", "443:443"), services.get("gateway").get("ports"));
        var environment = (Map<String, Object>) services.get("backend").get("environment");
        assertEquals("prod", environment.get("SPRING_PROFILES_ACTIVE"));
        assertEquals("/app/data/avatars", environment.get("AVATAR_DIR"));
        assertTrue(environment.get("DB_PASSWORD").toString().startsWith("${DB_PASSWORD:?"));
        assertTrue(services.get("backend").containsKey("healthcheck"));
        assertEquals(
                Set.of("database", "avatars", "certificates", "gateway-config"),
                ((Map<?, ?>) config.get("volumes")).keySet());
    }

    /** 验证镜像及网关没有复制密钥或开放多余入口。 */
    @Test
    void dockerBuildAndGatewayDoNotCopySecretsOrExposeExtraEndpoints() throws Exception {
        String docker = Files.readString(deployment.resolve("Dockerfile"));
        assertTrue(docker.contains("server.jar /app/server.jar"));
        assertTrue(docker.contains("USER app"));
        assertFalse(docker.contains("COPY . "));
        assertEquals(
                "*\n!server.jar\n!Dockerfile\n",
                Files.readString(deployment.resolve(".dockerignore")));
        String gateway = Files.readString(deployment.resolve("Caddyfile"));
        assertTrue(gateway.contains("path /api/* /uploads/avatars/*"));
        assertTrue(gateway.contains("header_up X-Forwarded-For {remote_host}"));
        assertTrue(gateway.contains("respond \"Not found\" 404"));
    }
}
