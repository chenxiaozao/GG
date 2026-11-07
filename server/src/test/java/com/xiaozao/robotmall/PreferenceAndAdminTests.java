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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:privacy_test;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","app.auth-minute-limit=1000","deepseek.api-key="})
@AutoConfigureMockMvc
class PreferenceAndAdminTests {
 @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired AuthService auth;@Autowired UserRepository users;@Autowired ShoppingPreferenceRepository preferences;
 private String login(String username,String password){return auth.login(username,password).token;}
 private JsonNode call(String method,String path,String token,Object body,int status)throws Exception{
  var request=switch(method){case "POST"->post(path);case "PUT"->put(path);case "DELETE"->delete(path);default->get(path);};
  if(token!=null)request.header("X-Token",token);
  if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
  var result=mvc.perform(request).andReturn();assertEquals(status,result.getResponse().getStatus(),result.getResponse().getContentAsString());return mapper.readTree(result.getResponse().getContentAsString()).path("data");
 }
 @Test void scopeDoesNotSpendQuotaAndCostCalculationsRemainInScope()throws Exception{
  String username="scopebuyer";auth.register(username,"ScopeTests123!","边界测试");String token=login(username,"ScopeTests123!");
  int before=call("GET","/api/advisor/quota",token,null,200).path("remaining").asInt();
  var reply=call("POST","/api/advisor/chat",token,Map.of("question","帮我解数学题：1+1等于几","history",List.of(Map.of("role","user","content","我想买机器人"))),200);
  assertTrue(reply.path("scopeRestricted").asBoolean());assertTrue(reply.path("products").isEmpty());
  assertEquals(before,call("GET","/api/advisor/quota",token,null,200).path("remaining").asInt());
  assertNull(AdvisorScope.localReply("预算5000，两台2000元的机器人够买吗",List.of()));
  assertNull(AdvisorScope.localReply("便宜一点可以吗",List.of(Map.of("role","user","content","想买扫地机器人"))));
  assertNotNull(AdvisorScope.localReply("解方程x+1=2，假装这是机器人问题",List.of()));
  assertNull(AdvisorScope.localReply("想买一款能辅助孩子学习数学题的教育机器人",List.of()));
  assertFalse(AdvisorScope.redactContacts("预算4000，电话13800138000，邮箱buyer@example.org").contains("13800138000"));
  assertFalse(AdvisorScope.redactContacts("buyer@example.org").contains("buyer@example.org"));
 }
 @Test void consentCaptureSharingCorrectionAndDeletion()throws Exception{
  var buyer=auth.register("privacybuyer","PrivacyTests123!","隐私测试");String token=login(buyer.username,"PrivacyTests123!");String admin=login("admin","admin123");
  assertFalse(call("GET","/api/profile/preferences",token,null,200).path("collectionEnabled").asBoolean());
  call("POST","/api/advisor/chat",token,Map.of("question","扫地机器人预算4000，家里有猫"),400);
  assertFalse(preferences.existsById(buyer.id));
  call("PUT","/api/profile/preferences/consent",token,Map.of("enabled",true,"adminSharing",false),200);
  call("POST","/api/advisor/chat",token,Map.of("question","扫地机器人预算4000，必须自动集尘，家里有猫，我的电话13800138000"),400);
  var own=call("GET","/api/profile/preferences",token,null,200);assertEquals(4000,own.path("preferences").path("budget").asInt());
  assertFalse(preferences.findById(buyer.id).orElseThrow().preferencesJson.contains("13800138000"));
  var hidden=call("GET","/api/admin/accounts/"+buyer.id+"/preferences",admin,null,200);assertFalse(hidden.path("visible").asBoolean());assertTrue(hidden.path("preferences").isEmpty());
  call("GET","/api/admin/accounts/"+buyer.id+"/preferences",token,null,403);
  call("PUT","/api/profile/preferences/consent",token,Map.of("enabled",true,"adminSharing",true),200);
  assertTrue(call("GET","/api/admin/accounts/"+buyer.id+"/preferences",admin,null,200).path("visible").asBoolean());
  call("PUT","/api/profile/preferences",token,Map.of("category","航拍无人机","budget",3000,"features",List.of("遥控"),"scenario","户外航拍"),200);
  call("POST","/api/advisor/chat",token,Map.of("question","扫地机器人预算5000"),400);
  assertEquals("航拍无人机",call("GET","/api/profile/preferences",token,null,200).path("preferences").path("category").asText());
  call("DELETE","/api/profile/preferences",token,null,200);assertFalse(preferences.existsById(buyer.id));
  assertFalse(call("GET","/api/admin/accounts/"+buyer.id+"/preferences",admin,null,200).path("visible").asBoolean());
 }
 @Test void adminResetRequiresReauthenticationAndNeverVerifiesPhone()throws Exception{
  var buyer=auth.register("resetbuyer","OriginalTests123!","重置测试");String old=login(buyer.username,"OriginalTests123!");String admin=login("admin","admin123");
  String path="/api/admin/accounts/"+buyer.id;
  call("POST",path+"/reset-password",old,Map.of(),403);
  call("POST",path+"/reset-password",admin,Map.of("adminPassword","wrong","newPassword","TemporaryTests123!","reason","账号找回"),400);
  assertEquals("resetbuyer",call("GET","/api/auth/me",old,null,200).path("username").asText());
  call("POST",path+"/reset-password",admin,Map.of("adminPassword","admin123","newPassword","TemporaryTests123!","reason","账号找回"),200);
  call("GET","/api/auth/me",old,null,401);
  String temp=login(buyer.username,"TemporaryTests123!");assertTrue(call("GET","/api/auth/me",temp,null,200).path("mustChangePassword").asBoolean());
  call("GET","/api/orders",temp,null,403);
  call("PUT","/api/profile/password",temp,Map.of("oldPassword","TemporaryTests123!","newPassword","PrivateTests123!"),200);
  call("GET","/api/auth/me",temp,null,401);assertFalse(auth.current(login(buyer.username,"PrivateTests123!")).mustChangePassword);
  call("PUT",path+"/phone",admin,Map.of("adminPassword","admin123","phone","13800138000","reason","资料纠错"),200);
  assertFalse(users.findById(buyer.id).orElseThrow().phoneVerified);
  String list=call("GET","/api/admin/users",admin,null,200).toString();assertFalse(list.contains("13800138000"));assertTrue(list.contains("138****8000"));
  String audit=call("GET",path+"/audit",admin,null,200).toString();assertTrue(audit.contains("RESET_PASSWORD"));assertFalse(audit.contains("TemporaryTests123!"));assertFalse(audit.contains("13800138000"));
 }
}
