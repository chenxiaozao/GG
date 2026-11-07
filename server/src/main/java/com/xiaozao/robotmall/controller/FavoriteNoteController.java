package com.xiaozao.robotmall.controller;
import com.xiaozao.robotmall.common.*;
import com.xiaozao.robotmall.repository.FavoriteRepository;
import com.xiaozao.robotmall.service.AuthService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/favorites/notes")
public class FavoriteNoteController {
 private final FavoriteRepository favorites; private final AuthService auth;
 public FavoriteNoteController(FavoriteRepository favorites,AuthService auth){this.favorites=favorites;this.auth=auth;}
 @GetMapping public ApiResponse<?> list(@RequestHeader("X-Token")String token){
  Map<String,String> notes=new LinkedHashMap<>();
  favorites.findByUserId(auth.current(token).id).forEach(f->notes.put(String.valueOf(f.productId),Objects.toString(f.note,"")));
  return ApiResponse.ok(notes);
 }
 @PutMapping("/{productId}") public ApiResponse<?> save(@RequestHeader("X-Token")String token,@PathVariable Long productId,@RequestBody Map<String,Object> body){
  String note=Objects.toString(body.get("note"),"").trim();if(note.length()>500)throw new BizException("收藏备注最多500字");
  var favorite=favorites.findByUserIdAndProductId(auth.current(token).id,productId).orElseThrow(()->new BizException("该商品不在你的收藏中，请刷新确认"));
  favorite.note=note;favorites.save(favorite);return ApiResponse.ok(Map.of("productId",productId,"note",note));
 }
}
