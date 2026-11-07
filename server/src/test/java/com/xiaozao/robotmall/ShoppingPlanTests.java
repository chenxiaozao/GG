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
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:plan_test;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","app.auth-minute-limit=1000","deepseek.api-key="})
@AutoConfigureMockMvc
class ShoppingPlanTests {
 @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired AuthService auth;@Autowired ProductRepository products;
 @Autowired ShoppingPlanVersionRepository versions;
 JsonNode request(String method,String path,String token,Object body,int status)throws Exception{
  var req=switch(method){case "POST"->post(path);case "PUT"->put(path);case "DELETE"->delete(path);default->get(path);};if(token!=null)req.header("X-Token",token);if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));var res=mvc.perform(req).andReturn();assertEquals(status,res.getResponse().getStatus(),res.getResponse().getContentAsString());return mapper.readTree(res.getResponse().getContentAsString());
 }
 String buyer(String name){auth.register(name,"PlanTesting123!",name);return auth.login(name,"PlanTesting123!").token;}
 @Test void ownershipValidationUpdateDelete()throws Exception{
  String a=buyer("planowner"),b=buyer("planother");long pid=products.findAll().get(0).id;
  var body=new HashMap<String,Object>(Map.of("title","清洁计划","requirement","预算4000元，养猫","note","先比较，再决定","productIds",List.of(pid)));
  var created=request("POST","/api/shopping-plans",a,body,200);assertEquals(200,created.path("code").asInt());long id=created.path("data").path("id").asLong();assertTrue(id>0);
  assertEquals(0,request("GET","/api/shopping-plans",b,null,200).path("data").size());
  body.put("id",id);body.put("title","已更新");assertEquals(400,request("POST","/api/shopping-plans",b,body,400).path("code").asInt());
  request("DELETE","/api/shopping-plans/"+id,b,null,400);
  assertEquals("已更新",request("POST","/api/shopping-plans",a,body,200).path("data").path("title").asText());
  body.put("productIds",List.of(Long.MAX_VALUE));request("POST","/api/shopping-plans",a,body,400);
  body.put("productIds",List.of(pid,pid,pid,pid,pid));request("POST","/api/shopping-plans",a,body,400);
  request("GET","/api/shopping-plans",null,null,401);request("DELETE","/api/shopping-plans/"+id,a,null,200);assertEquals(0,request("GET","/api/shopping-plans",a,null,200).path("data").size());
 }
 @Test void structuredPlanValidatesBudgetAndScene()throws Exception{
  String token=buyer("structuredbuyer");
  var body=new HashMap<String,Object>(Map.of("title","清洁计划","requirement","养猫","note","私人备注","productIds",List.of(),"budget","4000.50","scene","地面清洁"));
  var created=request("POST","/api/shopping-plans",token,body,200).path("data");assertEquals("地面清洁",created.path("scene").asText());assertEquals(4000.5,created.path("budget").asDouble());
  body.put("id",created.path("id").asLong());
  for(String invalid:List.of("-1","0","10000001","1.001","abc")){body.put("budget",invalid);request("POST","/api/shopping-plans",token,body,400);}
  body.put("budget","");body.put("scene","非法场景");request("POST","/api/shopping-plans",token,body,400);
  body.put("scene","");var saved=request("POST","/api/shopping-plans",token,body,200).path("data");assertTrue(saved.path("budget").isNull());
 }
 @Test void favoriteNotesArePrivateAndDisappearWithFavorite()throws Exception{
  String a=buyer("notesbuyer"),b=buyer("notesother");long pid=products.findAll().get(0).id;
  request("POST","/api/favorites/"+pid,a,null,200);
  request("POST","/api/favorites/"+pid,b,null,200);
  request("PUT","/api/favorites/notes/"+pid,a,Map.of("note","我的私密顾虑"),200);
  assertEquals("我的私密顾虑",request("GET","/api/favorites/notes",a,null,200).path("data").path(String.valueOf(pid)).asText());
  assertEquals("",request("GET","/api/favorites/notes",b,null,200).path("data").path(String.valueOf(pid)).asText());
  request("PUT","/api/favorites/notes/"+pid,a,Map.of("note","a".repeat(501)),400);
  request("GET","/api/favorites/notes",null,null,401);
  assertFalse(request("GET","/api/products/"+pid,null,null,200).toString().contains("我的私密顾虑"));
  request("DELETE","/api/favorites/"+pid,a,null,200);
  request("PUT","/api/favorites/notes/"+pid,a,Map.of("note","不能创建收藏"),400);
  assertTrue(request("GET","/api/favorites/notes",a,null,200).path("data").isEmpty());
 }
 @Test void historyIsOwnedBoundedAndDeletedWithPlan()throws Exception{
  String a=buyer("historyowner"),b=buyer("historyother");
  var body=new HashMap<String,Object>(Map.of("title","初版","requirement","养猫，预算4000","note","私人的考虑","productIds",List.of(),"budget","4000","scene","地面清洁"));
  long id=request("POST","/api/shopping-plans",a,body,200).path("data").path("id").asLong();body.put("id",id);
  var first=request("GET","/api/shopping-plans/"+id+"/versions",a,null,200).path("data");assertEquals(1,first.size());assertEquals("初版",first.get(0).path("snapshot").path("title").asText());
  request("GET","/api/shopping-plans/"+id+"/versions",b,null,400);
  for(int i=0;i<22;i++){body.put("title","版本"+i);request("POST","/api/shopping-plans",a,body,200);}
  var history=request("GET","/api/shopping-plans/"+id+"/versions",a,null,200).path("data");assertEquals(20,history.size());assertEquals("版本21",history.get(0).path("snapshot").path("title").asText());
  var old=mapper.convertValue(history.get(5).path("snapshot"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});old.put("productIds",List.of());
  var restored=request("POST","/api/shopping-plans",a,old,200).path("data");assertEquals("版本16",restored.path("title").asText());
  request("DELETE","/api/shopping-plans/"+id,a,null,200);
  request("GET","/api/shopping-plans/"+id+"/versions",a,null,400);
  assertTrue(versions.findByPlanIdOrderByIdDesc(id).isEmpty());
 }
 @Test void contextValidationAndNoMatchDoNotSpendQuota()throws Exception{
  String token=buyer("contextbuyer");long pid=products.findAll().get(0).id;
  int before=request("GET","/api/advisor/quota",token,null,200).path("data").path("remaining").asInt();
  request("POST","/api/advisor/chat",token,Map.of("question","帮我选择机器人","contextProductIds",List.of(Long.MAX_VALUE)),400);
  var r=request("POST","/api/advisor/chat",token,Map.of("question","机器人预算1元以内","contextProductIds",List.of(pid)),200);
  assertTrue(r.path("data").path("products").isEmpty());assertEquals("规则校验",r.path("data").path("provider").asText());
  assertEquals(before,request("GET","/api/advisor/quota",token,null,200).path("data").path("remaining").asInt());
 }
}
