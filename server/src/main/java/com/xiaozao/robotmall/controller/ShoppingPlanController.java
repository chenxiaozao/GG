package com.xiaozao.robotmall.controller;
import com.xiaozao.robotmall.common.*;
import com.xiaozao.robotmall.entity.*;
import com.xiaozao.robotmall.repository.*;
import com.xiaozao.robotmall.service.AuthService;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDateTime;
@RestController @RequestMapping("/api/shopping-plans")
public class ShoppingPlanController {
 private final AuthService auth; private final ShoppingPlanRepository plans; private final ProductRepository products;
 private final ShoppingPlanVersionRepository versions; private final com.fasterxml.jackson.databind.ObjectMapper mapper;
 public ShoppingPlanController(AuthService auth,ShoppingPlanRepository plans,ProductRepository products,ShoppingPlanVersionRepository versions,com.fasterxml.jackson.databind.ObjectMapper mapper){this.auth=auth;this.plans=plans;this.products=products;this.versions=versions;this.mapper=mapper;}
 private void record(ShoppingPlan plan){
  var version=new ShoppingPlanVersion();version.planId=plan.id;version.createdAt=LocalDateTime.now();
  try{version.snapshotJson=mapper.writeValueAsString(plan);}catch(Exception e){throw new IllegalStateException("计划版本记录失败",e);}
  versions.save(version);var history=versions.findByPlanIdOrderByIdDesc(plan.id);if(history.size()>20)versions.deleteAll(history.subList(20,history.size()));
 }
 @GetMapping("/{id}/versions") public ApiResponse<?> history(@RequestHeader("X-Token")String token,@PathVariable Long id){
  owned(id,auth.current(token).id);
  return ApiResponse.ok(versions.findByPlanIdOrderByIdDesc(id).stream().map(v->{try{return Map.of("id",v.id,"createdAt",v.createdAt,"snapshot",mapper.readTree(v.snapshotJson));}catch(Exception e){throw new IllegalStateException("历史记录读取失败",e);}}).toList());
 }
 @GetMapping public ApiResponse<?> list(@RequestHeader("X-Token")String token){return ApiResponse.ok(plans.findByUserIdOrderByUpdatedAtDesc(auth.current(token).id));}
 private ShoppingPlan owned(Long id,Long user){return plans.findById(id).filter(p->p.userId.equals(user)).orElseThrow(()->new BizException("计划不存在或无权访问"));}
 private String text(Map<String,Object>b,String key,int max,boolean required){String s=Objects.toString(b.get(key),"").trim();if(s.length()>max || (required&&s.isBlank()))throw new BizException(key+"内容为空或过长");return s;}
 @Transactional
 @PostMapping public ApiResponse<?> save(@RequestHeader("X-Token")String token,@RequestBody Map<String,Object>b){
  Long user=auth.current(token).id;
  ShoppingPlan p;
  if(b.get("id")!=null){try{p=owned(Long.valueOf(b.get("id").toString()),user);if(versions.findByPlanIdOrderByIdDesc(p.id).isEmpty())record(p);}catch(NumberFormatException e){throw new BizException("无效计划编号");}}
  else {if(plans.findByUserIdOrderByUpdatedAtDesc(user).size()>=30)throw new BizException("最多保存30个计划，请先清理旧计划");p=new ShoppingPlan();p.userId=user;}
  p.title=text(b,"title",60,true);p.requirement=text(b,"requirement",800,true);p.note=text(b,"note",800,false);
  String budget=Objects.toString(b.get("budget"),"").trim();
  if(budget.isEmpty())p.budget=null;
  else {try{p.budget=new java.math.BigDecimal(budget);if(p.budget.signum()<=0||p.budget.compareTo(new java.math.BigDecimal("10000000"))>0||p.budget.stripTrailingZeros().scale()>2)throw new NumberFormatException();}catch(NumberFormatException ex){throw new BizException("预算须为大于0、不超过10000000元且最多两位小数的金额");}}
  p.scene=text(b,"scene",30,false);
  if(!p.scene.isEmpty()&&!Set.of("地面清洁","擦窗机器人","庭院与泳池","陪伴娱乐","航拍无人机","教育编程","四足机器人","人形机器人","商用服务","机器人配件").contains(p.scene))throw new BizException("请选择有效使用场景");
  Object raw=b.get("productIds");if(!(raw instanceof List<?> ids)||ids.size()>4)throw new BizException("请选择最多4款候选商品");
  Set<Long> selected=new LinkedHashSet<>();
  for(Object value:ids){try{Long id=Long.valueOf(value.toString());if(!products.existsById(id))throw new BizException("候选商品不存在");selected.add(id);}catch(NumberFormatException e){throw new BizException("无效商品编号");}}
  p.productIds=selected.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));p.updatedAt=LocalDateTime.now();p=plans.save(p);record(p);return ApiResponse.ok(p);
 }
 @Transactional @DeleteMapping("/{id}") public ApiResponse<?> delete(@RequestHeader("X-Token")String token,@PathVariable Long id){var plan=owned(id,auth.current(token).id);versions.deleteAll(versions.findByPlanIdOrderByIdDesc(id));plans.delete(plan);return ApiResponse.ok();}
}
