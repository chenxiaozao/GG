package com.xiaozao.robotmall.service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozao.robotmall.common.BizException;
import com.xiaozao.robotmall.entity.*;
import com.xiaozao.robotmall.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDateTime;

@Service
public class ShoppingPreferenceService {
    private final ShoppingPreferenceRepository profiles;
    private final UserRepository users;
    private final ObjectMapper mapper;
    public ShoppingPreferenceService(ShoppingPreferenceRepository profiles,UserRepository users,ObjectMapper mapper) {
        this.profiles=profiles;this.users=users;this.mapper=mapper;
    }
    private ShoppingPreference profile(long userId) {
        return profiles.findById(userId).orElseGet(()->{var p=new ShoppingPreference();p.userId=userId;return p;});
    }
    public Map<String,Object> view(long userId,boolean admin) {
        var p=profile(userId);
        Map<String,Object> out=new LinkedHashMap<>();out.put("collectionEnabled",p.collectionEnabled);
        out.put("adminSharing",p.adminSharing);out.put("confirmed",p.confirmed);
        out.put("updatedAt",p.updatedAt);out.put("source","仅限用户明确表达的选购需求，不保存原始聊天，不推断身份/收入/健康");
        out.put("preferences",(!admin||p.collectionEnabled&&p.adminSharing)?read(p):Map.of());
        out.put("visible",!admin||p.collectionEnabled&&p.adminSharing);
        return out;
    }
    private Map<String,Object> read(ShoppingPreference p) {
        try {return mapper.readValue(p.preferencesJson,new TypeReference<LinkedHashMap<String,Object>>(){});}
        catch(Exception e){return new LinkedHashMap<>();}
    }
    private void write(ShoppingPreference p,Map<String,Object> data) {
        try {p.preferencesJson=mapper.writeValueAsString(data);p.updatedAt=LocalDateTime.now();profiles.save(p);}
        catch(Exception e){throw new BizException("偏好档案保存失败");}
    }
    @Transactional
    public Map<String,Object> consent(long userId,boolean enabled,boolean sharing) {
        users.findForUpdate(userId).orElseThrow(()->new BizException("用户不存在"));
        var p=profile(userId);p.collectionEnabled=enabled;p.adminSharing=enabled&&sharing;p.consentAt=LocalDateTime.now();
        if(!enabled){p.confirmed=false;p.preferencesJson="{}";}
        p.updatedAt=LocalDateTime.now();profiles.save(p);return view(userId,false);
    }
    @Transactional
    public void capture(long userId,RecommendationEngine.Need need,String question) {
        users.findForUpdate(userId).orElseThrow(()->new BizException("用户不存在"));
        var p=profile(userId);if(!p.collectionEnabled)return;
        // User-confirmed preferences are not silently overwritten by a later conversation.
        if(p.confirmed)return;
        Map<String,Object> data=read(p);
        if(need.category()!=null)data.put("category",need.category());
        if(need.budget()!=null)data.put("budget",need.budget());
        var known=need.requiredFeatures().stream().filter(x->!x.startsWith("待确认")).toList();
        if(!known.isEmpty())data.put("features",known);
        if(need.minEndurance()>0&&need.minEndurance()<=1440)data.put("minEndurance",need.minEndurance());
        if(question.matches("(?s).*(家里有猫|家里有狗|家中养猫|家中养狗|家里养宠).*"))data.put("scenario","养宠清洁（用户明确表达，待确认）");
        if(question.matches("(?s).*(给孩子|孩子学习|孩子入门).*"))data.put("scenario","儿童学习选购（用户明确表达，待确认）");
        data.put("evidenceSource","用户选购咨询中明确表达；未保存原文");
        write(p,data);
    }
    @Transactional
    public Map<String,Object> confirm(long userId,Map<String,Object> body) {
        users.findForUpdate(userId).orElseThrow(()->new BizException("用户不存在"));
        var p=profile(userId);if(!p.collectionEnabled)throw new BizException("请先开启偏好档案");
        String category=Objects.toString(body.get("category"),"");
        if(!category.isEmpty()&&!RecommendationEngine.SCENES.containsValue(category))throw new BizException("类别无效");
        Map<String,Object> data=new LinkedHashMap<>();
        if(!category.isEmpty())data.put("category",category);
        Object budget=body.get("budget");
        if(budget!=null&&!Objects.toString(budget,"").isBlank()) {
            double number;
            try {number=Double.parseDouble(budget.toString());}catch(Exception e){throw new BizException("预算格式无效");}
            if(!Double.isFinite(number)||number<0||number>1000000)throw new BizException("预算范围无效");
            data.put("budget",number);
        }
        if(body.get("features") instanceof List<?> list) {
            List<String> features=list.stream().map(Objects::toString).distinct().toList();
            if(!Set.of("自动集尘","避障","防缠绕","拖地","编程","遥控").containsAll(features))throw new BizException("功能选项无效");
            data.put("features",features);
        }
        String scenario=Objects.toString(body.get("scenario"),"");
        if(!Set.of("","家庭清洁","养宠清洁","儿童学习选购","户外航拍","其他选购").contains(scenario))throw new BizException("使用场景无效");
        if(!scenario.isBlank())data.put("scenario",scenario);
        if(body.containsKey("minEndurance")) {
            if(!(body.get("minEndurance") instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=Math.rint(n.doubleValue())||n.doubleValue()<0||n.doubleValue()>1440)throw new BizException("最低续航须为0至1440整数分钟");
            if(n.intValue()>0)data.put("minEndurance",n.intValue());
        }
        data.put("evidenceSource","用户自行编辑并确认");p.confirmed=true;write(p,data);return view(userId,false);
    }
    @Transactional
    public void clear(long userId) {
        users.findForUpdate(userId).orElseThrow(()->new BizException("用户不存在"));
        profiles.deleteById(userId);
    }
}
