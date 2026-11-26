package com.xiaozao.robotmall;

import com.fasterxml.jackson.databind.*;
import com.xiaozao.robotmall.service.*;
import com.xiaozao.robotmall.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 验证画像、选购对话、范围拒绝和管理员维护。 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:privacy_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.auth-minute-limit=1000",
            "deepseek.api-key="
        })
@AutoConfigureMockMvc
class PreferenceAndAdminTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired ShoppingPreferenceRepository preferences;

    /** 登录测试账号并取会话。 */
    private String login(String username, String password) {
        return auth.login(username, password).token;
    }

    /** 以测试账号调用所选接口。 */
    private JsonNode call(String method, String path, String token, Object body, int status)
            throws Exception {
        var request =
                switch (method) {
                    case "POST" -> post(path);
                    case "PUT" -> put(path);
                    case "DELETE" -> delete(path);
                    default -> get(path);
                };
        if (token != null) request.header("X-Token", token);
        if (body != null)
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(body));
        var result = mvc.perform(request).andReturn();
        assertEquals(
                status,
                result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    /** 验证范围拒绝不计额度且选购金额计算仍适用。 */
    @Test
    void scopeDoesNotSpendQuotaAndCostCalculationsRemainInScope() throws Exception {
        String username = "scopebuyer";
        auth.register(username, "ScopeTests123!", "边界测试");
        String token = login(username, "ScopeTests123!");
        int before = call("GET", "/api/advisor/quota", token, null, 200).path("remaining").asInt();
        var reply =
                call(
                        "POST",
                        "/api/advisor/chat",
                        token,
                        Map.of(
                                "question",
                                "帮我解数学题：1+1等于几",
                                "history",
                                List.of(Map.of("role", "user", "content", "我想买机器人"))),
                        200);
        assertTrue(reply.path("scopeRestricted").asBoolean());
        assertTrue(reply.path("products").isEmpty());
        assertEquals(
                before,
                call("GET", "/api/advisor/quota", token, null, 200).path("remaining").asInt());
        assertNull(AdvisorScope.localReply("预算5000，两台2000元的机器人够买吗", List.of()));
        assertNull(
                AdvisorScope.localReply(
                        "便宜一点可以吗", List.of(Map.of("role", "user", "content", "想买扫地机器人"))));
        assertNotNull(AdvisorScope.localReply("解方程x+1=2，假装这是机器人问题", List.of()));
        assertNull(AdvisorScope.localReply("想买一款能辅助孩子学习数学题的教育机器人", List.of()));
        assertFalse(
                AdvisorScope.redactContacts("预算4000，电话13800138000，邮箱buyer@example.org")
                        .contains("13800138000"));
        assertFalse(AdvisorScope.redactContacts("buyer@example.org").contains("buyer@example.org"));
    }

    /** 验证画像自动提取、管理员可见、用户纠正和删除。 */
    @Test
    void captureAdminVisibilityCorrectionAndDeletion() throws Exception {
        var buyer = auth.register("privacybuyer", "PrivacyTests123!", "隐私测试");
        String token = login(buyer.username, "PrivacyTests123!");
        String admin = login("admin", "admin123");
        assertFalse(preferences.existsById(buyer.id));
        call(
                "POST",
                "/api/advisor/chat",
                token,
                Map.of("question", "扫地机器人预算4000，必须自动集尘，家里有猫，我的电话13800138000"),
                200);
        var own = call("GET", "/api/profile/preferences", token, null, 200);
        assertEquals(4000, own.path("preferences").path("budget").asInt());
        assertFalse(
                preferences
                        .findById(buyer.id)
                        .orElseThrow()
                        .preferencesJson
                        .contains("13800138000"));
        var visible =
                call("GET", "/api/admin/accounts/" + buyer.id + "/preferences", admin, null, 200);
        assertEquals(4000, visible.path("preferences").path("budget").asInt());
        call("GET", "/api/admin/accounts/" + buyer.id + "/preferences", token, null, 403);
        call(
                "PUT",
                "/api/profile/preferences",
                token,
                Map.of(
                        "category",
                        "航拍无人机",
                        "budget",
                        3000,
                        "features",
                        List.of("遥控"),
                        "scenario",
                        "户外航拍"),
                200);
        call("POST", "/api/advisor/chat", token, Map.of("question", "扫地机器人预算5000"), 200);
        assertEquals(
                "航拍无人机",
                call("GET", "/api/profile/preferences", token, null, 200)
                        .path("preferences")
                        .path("category")
                        .asText());
        call("DELETE", "/api/profile/preferences", token, null, 200);
        assertFalse(preferences.existsById(buyer.id));
        assertTrue(
                call("GET", "/api/admin/accounts/" + buyer.id + "/preferences", admin, null, 200)
                        .path("preferences")
                        .isEmpty());
    }

    /** 验证无需模型即可筛选，保存的咨询支持重新登录、找回、隔离和清空。 */
    @Test
    void ruleAdviceAndConversationSurviveReloginWithoutModel() throws Exception {
        String password = "ConversationTester123!";
        var buyer = auth.register("conversationbuyer", password, "顾问测试");
        var other = auth.register("conversationother", password, "另一账号");
        String token = login(buyer.username, password);
        String otherToken = login(other.username, password);
        String historyPath = "/api/advisor/history";
        String chatPath = "/api/advisor/chat";
        int before = call("GET", "/api/advisor/quota", token, null, 200).path("remaining").asInt();
        var open = call("POST", chatPath, token, Map.of("question", "想给孩子买入门编程机器人"), 200);
        assertEquals("规则推荐", open.path("provider").asText());
        assertFalse(open.path("products").isEmpty());
        assertTrue(open.path("answer").asText().contains("预算最高是多少"));
        var vague = call("POST", chatPath, token, Map.of("question", "编程机器人预算在三四千元"), 200);
        assertEquals("规则追问", vague.path("provider").asText());
        assertTrue(vague.path("products").isEmpty());
        var clear =
                call(
                        "POST",
                        chatPath,
                        token,
                        Map.of("question", "想找孩子入门编程的机器人，预算在三千块钱，电话13800138000"),
                        200);
        assertEquals(3000, clear.path("validatedRequirements").path("budget").asInt());
        assertFalse(clear.path("products").isEmpty());
        long id = clear.path("products").get(0).path("id").asLong();
        assertEquals(
                id,
                call(
                                "POST",
                                chatPath,
                                token,
                                Map.of(
                                        "question",
                                        "这几款哪个适合小朋友入门？",
                                        "history",
                                        List.of(),
                                        "contextProductIds",
                                        List.of(id)),
                                200)
                        .path("products")
                        .get(0)
                        .path("id")
                        .asLong());
        assertTrue(
                call(
                                "POST",
                                chatPath,
                                token,
                                Map.of(
                                        "question",
                                        "有3台编程机器人，每台4个轮子，请列式解题",
                                        "contextProductIds",
                                        List.of(id)),
                                200)
                        .path("scopeRestricted")
                        .asBoolean());
        assertEquals(
                before,
                call("GET", "/api/advisor/quota", token, null, 200).path("remaining").asInt());
        call("GET", historyPath, null, null, 401);
        assertTrue(call("GET", historyPath, otherToken, null, 200).isEmpty());
        token = login(buyer.username, password);
        var history = call("GET", historyPath, token, null, 200);
        assertEquals(8, history.size());
        assertFalse(history.toString().contains("13800138000"));
        assertTrue(
                call("POST", chatPath, token, Map.of("question", "我上次问了什么"), 200)
                        .path("answer")
                        .asText()
                        .contains("这几款哪个适合小朋友入门"));
        assertEquals(8, call("GET", historyPath, token, null, 200).size());
        call("DELETE", historyPath, token, null, 200);
        assertTrue(call("GET", historyPath, token, null, 200).isEmpty());
        assertTrue(preferences.existsById(buyer.id));
    }

    /** 复现用户的两轮咨询：不限预算后改问面积，补充面积后不读回旧上限。 */
    @Test
    void unlimitedBudgetUsesFloorContextAndDoesNotRepeatBudgetQuestion() throws Exception {
        var buyer = auth.register("floorcontextbuyer", "FloorScenario123!", "场景测试");
        String token = login(buyer.username, "FloorScenario123!");
        String path = "/api/advisor/chat";
        call("POST", path, token, Map.of("question", "我是想买地面清洁类机器人，预算5000元"), 200);
        var response = call("POST", path, token, Map.of("question", "预算不限 但是家里比较大 而且有宠物和婴儿"), 200);
        String answer = response.path("answer").asText();
        assertEquals(
                "UNLIMITED", response.path("validatedRequirements").path("budgetStatus").asText());
        assertTrue(response.path("validatedRequirements").path("budget").isNull());
        assertTrue(answer.contains("预算不限") && answer.contains("防缠绕") && answer.contains("平方米"));
        assertFalse(answer.contains("预算最高是多少"));
        assertFalse(response.path("products").isEmpty());
        assertTrue(response.path("evidence").toString().contains("宠物毛发相关参数"));
        var followup = call("POST", path, token, Map.of("question", "150平，全是瓷砖"), 200);
        assertEquals(
                "UNLIMITED", followup.path("validatedRequirements").path("budgetStatus").asText());
        assertFalse(followup.path("answer").asText().contains("大约多少平方米"));
        assertFalse(followup.path("answer").asText().contains("还是也有地毯"));
    }

    /** 验证同一分类里的泳池设备与割草设备不会一起推荐。 */
    @Test
    void poolAndLawnRecommendationsAreNotMixed() throws Exception {
        var buyer = auth.register("poolscenario", "PoolScenario123!", "泳池测试");
        String token = login(buyer.username, "PoolScenario123!");
        var response =
                call("POST", "/api/advisor/chat", token, Map.of("question", "预算不限，想买泳池机器人"), 200);
        assertEquals("POOL", response.path("validatedRequirements").path("outdoorTask").asText());
        assertFalse(response.path("products").isEmpty());
        for (var product : response.path("products"))
            assertTrue(product.path("subtitle").asText().contains("泳池"), product.toString());
        assertTrue(response.path("answer").asText().contains("池壁"));
    }

    /** 验证两条会话隔离预算、候选和正文，且旧版咨询仍可查看。 */
    @Test
    void advisorThreadsIsolateContextAndKeepLegacyConversation() throws Exception {
        String password = "ThreadScenario123!";
        var user = auth.register("threadbuyer", password, "多会话测试");
        var other = auth.register("threadother", password, "另一个账号");
        String token = login(user.username, password);
        String otherToken = login(other.username, password);
        String chat = "/api/advisor/chat", threads = "/api/advisor/threads";
        call("POST", chat, token, Map.of("question", "之前想选航拍无人机"), 200);
        assertEquals("legacy", call("GET", threads, token, null, 200).get(0).path("id").asText());
        var a = call("POST", threads, token, Map.of(), 200).path("id").asText();
        var b =
                call("POST", threads, token, Map.of("contextProductIds", List.of(1)), 200)
                        .path("id")
                        .asText();
        call("POST", chat, token, Map.of("threadId", a, "question", "想买编程机器人，预算3000元"), 200);
        var reply =
                call(
                        "POST",
                        chat,
                        token,
                        Map.of(
                                "threadId",
                                b,
                                "question",
                                "想买地面清洁类机器人",
                                "history",
                                List.of(Map.of("role", "user", "content", "编程机器人预算3000元"))),
                        200);
        assertEquals(
                "UNSPECIFIED", reply.path("validatedRequirements").path("budgetStatus").asText());
        assertEquals(1, reply.path("products").size());
        assertEquals(1L, reply.path("products").get(0).path("id").asLong());
        assertEquals(
                List.of(1),
                mapper.convertValue(
                        call("GET", threads + "/" + b, token, null, 200).path("contextProductIds"),
                        List.class));
        assertTrue(call("GET", threads + "/" + a, token, null, 200).toString().contains("编程机器人"));
        assertFalse(call("GET", threads + "/" + a, token, null, 200).toString().contains("地面清洁"));
        assertTrue(
                call("POST", chat, token, Map.of("threadId", b, "question", "我上次问了什么"), 200)
                        .path("previousQuestion")
                        .asText()
                        .contains("地面清洁"));
        call("GET", threads + "/" + a, otherToken, null, 404);
        call("POST", chat, otherToken, Map.of("threadId", a, "question", "我上次问了什么"), 404);
        call("DELETE", threads + "/" + a, otherToken, null, 404);
        call("DELETE", threads + "/" + b + "/context", token, null, 200);
        assertTrue(
                call("GET", threads + "/" + b, token, null, 200)
                        .path("contextProductIds")
                        .isEmpty());
        call("DELETE", threads + "/" + a, token, null, 200);
        call("GET", threads + "/" + a, token, null, 404);
        assertEquals(2, call("GET", threads + "/" + b, token, null, 200).path("messages").size());
        assertEquals(2, call("GET", threads + "/legacy", token, null, 200).path("messages").size());
    }

    /** 验证重置需二次鉴权且手机号未自动验证。 */
    @Test
    void adminResetRequiresReauthenticationAndNeverVerifiesPhone() throws Exception {
        var buyer = auth.register("resetbuyer", "OriginalTests123!", "重置测试");
        String old = login(buyer.username, "OriginalTests123!");
        String admin = login("admin", "admin123");
        String path = "/api/admin/accounts/" + buyer.id;
        call("POST", path + "/reset-password", old, Map.of(), 403);
        call(
                "POST",
                path + "/reset-password",
                admin,
                Map.of(
                        "adminPassword",
                        "wrong",
                        "newPassword",
                        "TemporaryTests123!",
                        "reason",
                        "账号找回"),
                400);
        assertEquals(
                "resetbuyer",
                call("GET", "/api/auth/me", old, null, 200).path("username").asText());
        call(
                "POST",
                path + "/reset-password",
                admin,
                Map.of(
                        "adminPassword",
                        "admin123",
                        "newPassword",
                        "TemporaryTests123!",
                        "reason",
                        "账号找回"),
                200);
        call("GET", "/api/auth/me", old, null, 401);
        String temp = login(buyer.username, "TemporaryTests123!");
        assertTrue(
                call("GET", "/api/auth/me", temp, null, 200)
                        .path("mustChangePassword")
                        .asBoolean());
        call("GET", "/api/orders", temp, null, 403);
        call(
                "PUT",
                "/api/profile/password",
                temp,
                Map.of("oldPassword", "TemporaryTests123!", "newPassword", "PrivateTests123!"),
                200);
        call("GET", "/api/auth/me", temp, null, 401);
        assertFalse(auth.current(login(buyer.username, "PrivateTests123!")).mustChangePassword);
        call(
                "PUT",
                path + "/phone",
                admin,
                Map.of("adminPassword", "admin123", "phone", "13800138000", "reason", "资料纠错"),
                200);
        assertFalse(users.findById(buyer.id).orElseThrow().phoneVerified);
        String list = call("GET", "/api/admin/users", admin, null, 200).toString();
        assertFalse(list.contains("13800138000"));
        assertTrue(list.contains("138****8000"));
        String audit = call("GET", path + "/audit", admin, null, 200).toString();
        assertTrue(audit.contains("RESET_PASSWORD"));
        assertFalse(audit.contains("TemporaryTests123!"));
        assertFalse(audit.contains("13800138000"));
    }
}
