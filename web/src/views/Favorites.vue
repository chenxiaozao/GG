<script setup lang="ts">
import ProductGrid from "../components/ProductGrid.vue";
import { onMounted, ref } from 'vue';
import api from '../api';
import { advisorContext } from '../shoppingContext';
import EmptyState from "../components/EmptyState.vue";
import { useCollection } from "../composables/useCollection";
const {
  auth,
  products,
  selectedIds,
  message,
  loading,
  busy,
  loadError,
  load,
  toggleSelect,
  selectAll,
  remove,
  removeSelected,
  clearAll,
} = useCollection("favorites");
const notes=ref<Record<string,string>>({}), noteError=ref(''), noteStatus=ref(''), noteSaving=ref<number|null>(null);
async function loadNotes(){if(!auth.logged)return;try{const stored=await api.get('/favorites/notes') as Record<string,string>;notes.value={...stored,...notes.value};noteError.value='';}catch(e:any){noteError.value=e.message;}}
onMounted(loadNotes);
async function saveNote(product:any){if(noteSaving.value!==null)return;noteSaving.value=product.id;noteError.value='';noteStatus.value='';const submitted=notes.value[product.id]||'';try{const result:any=await api.put(`/favorites/notes/${product.id}`,{note:submitted});if((notes.value[product.id]||'')===submitted){notes.value[product.id]=result.note;noteStatus.value=`${product.name} 的备注已保存`;}else noteStatus.value='上一版备注已保存；你刚输入的新内容尚未保存。';}catch(e:any){noteError.value=e.message;}finally{noteSaving.value=null;}}
</script>

<template>
  <div class="container content-medium collection-page">
    <div class="record-heading">
      <div>
        <h1>我的收藏</h1>
        <p>
          {{
            auth.logged
              ? "收藏数据仅属于当前登录账号"
              : "游客收藏保存在本机，登录后自动合并"
          }}
        </p>
      </div>
      <button
        v-if="products.length"
        class="btn secondary"
        :disabled="busy || loading"
        @click="clearAll"
      >
        清空收藏
      </button>
      <router-link v-if="products.length" class="btn secondary" to="/advisor" @click="advisorContext(products.filter(p=>selectedIds.includes(p.id)).length ? products.filter(p=>selectedIds.includes(p.id)) : products.slice(0,4), '请帮我缩小这些收藏商品的选择范围。请先问我预算和使用需求，再说明取舍。')">咨询已选收藏（最多4款）</router-link>
    </div>
    <div v-if="!auth.logged && products.length" class="guest-data-tip">
      <span>游客收藏</span>
      <p>登录后同步到你的账号，不会覆盖原有收藏。</p>
      <router-link :to="{ path: '/login', query: { redirect: '/favorites' } }"
        >登录并同步 →</router-link
      >
    </div>
    <div class="notice" role="status" v-if="message">{{ message }}</div>
    <p v-if="noteError" role="alert">备注操作失败：{{ noteError }}。未保存的文字仍保留在输入框。<button class="btn secondary" @click="loadNotes">重新读取备注</button></p><p v-if="noteStatus" role="status">{{ noteStatus }}</p>
    <div v-if="products.length" class="batch-toolbar">
      <label
        ><input
          type="checkbox"
          :disabled="busy || loading"
          :checked="selectedIds.length === products.length"
          @change="selectAll"
        />
        全选</label
      ><span>已选择 {{ selectedIds.length }} 件</span
      ><button
        class="btn secondary"
        :disabled="busy || !selectedIds.length"
        @click="removeSelected"
      >
        移除所选
      </button>
    </div>
    <div v-if="loadError" class="collection-error" role="alert">
      <p>{{ loadError }}</p>
      <button class="btn secondary" :disabled="loading" @click="load">
        重新加载
      </button>
    </div>
    <div v-if="loading" class="empty">正在加载收藏…</div>
    <ProductGrid
      v-else
      :products="products"
      manageable
      selectable
      :busy="busy || loading"
      :selected-ids="selectedIds"
      action-label="取消收藏"
      @remove="remove"
      @toggle-select="toggleSelect"
    ><template #notes="{ product }"><div v-if="auth.logged" class="favorite-note"><label :for="`favorite-note-${product.id}`">我的备注（仅自己可见）</label><textarea :id="`favorite-note-${product.id}`" class="input" v-model="notes[product.id]" maxlength="500" placeholder="为什么收藏它？还有哪些顾虑？不会自动发送给 AI。" /><button class="btn secondary" :disabled="noteSaving!==null || busy" @click="saveNote(product)">{{ noteSaving===product.id?'保存中…':'保存备注' }}</button></div></template></ProductGrid>
    <EmptyState
      v-if="!loading && !loadError && !products.length"
      title="还没有收藏商品"
      description="在商品详情点击收藏，把感兴趣的型号留在这里，方便下次继续比较。"
      action-label="去发现商品"
      to="/products"
    />
  </div>
</template>
