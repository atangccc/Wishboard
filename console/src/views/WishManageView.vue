<script lang="ts" setup>
import { ref, onMounted, computed } from "vue";
import { consoleApiClient } from "@halo-dev/api-client";
import { Toast, Dialog, IconFolder } from "@halo-dev/components";

const PLUGIN_NAME = "PluginWishboard";
const API_BASE = "/apis/console.api.wishboard.aobp.cn/v1alpha1";

const activeNav = ref("list");
const navTitles: Record<string, string> = {
  list: "便签管理",
  types: "类型管理",
  settings: "插件设置"
};
const pageTitle = computed(() => navTitles[activeNav.value] || "心愿便签");

// --- Custom dropdown ---
const openDropdown = ref("");
function toggleDrop(name: string) { openDropdown.value = openDropdown.value === name ? "" : name; }
function selectOpt(model: any, key: string, val: string) { (model as any)[key] = val; openDropdown.value = ""; }

// --- Types ---
interface WishTypeSpec { slug: string; displayName: string; description: string; builtIn: boolean; priority: number; }
interface WishTypeItem { metadata: { name: string }; spec: WishTypeSpec; }
interface TypeStat { slug: string; displayName: string; count: number; }

const wishTypes = ref<WishTypeItem[]>([]);
const typeStats = ref<TypeStat[]>([]);
const typesLoading = ref(false);
const showTypeDialog = ref(false);
const editingType = ref<WishTypeItem | null>(null);
const typeForm = ref({ slug: "", displayName: "", description: "", priority: 10 });

async function loadTypes() {
  typesLoading.value = true;
  try {
    const [typesRes, statsRes] = await Promise.all([
      fetch(`${API_BASE}/wish-types`),
      fetch(`${API_BASE}/wish-types/-/stats`)
    ]);
    wishTypes.value = await typesRes.json();
    typeStats.value = await statsRes.json();
  } catch (e) { console.error("加载类型失败", e); }
  finally { typesLoading.value = false; }
}

function getTypeCount(slug: string): number {
  return typeStats.value.find(s => s.slug === slug)?.count ?? 0;
}

function openAddType() {
  editingType.value = null;
  typeForm.value = { slug: "", displayName: "", description: "", priority: 10 };
  showTypeDialog.value = true;
}

function openEditType(type: WishTypeItem) {
  editingType.value = type;
  typeForm.value = {
    slug: type.spec.slug,
    displayName: type.spec.displayName,
    description: type.spec.description || "",
    priority: type.spec.priority
  };
  showTypeDialog.value = true;
}

async function saveType() {
  if (!typeForm.value.slug.trim() || !typeForm.value.displayName.trim()) {
    Toast.warning("标识和名称不能为空");
    return;
  }
  try {
    if (editingType.value) {
      // 编辑
      const r = await fetch(`${API_BASE}/wish-types/${editingType.value.metadata.name}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(typeForm.value)
      });
      const d = await r.json();
      if (!r.ok) { Toast.error(d.error || "保存失败"); return; }
      Toast.success("保存成功");
    } else {
      // 新增
      const r = await fetch(`${API_BASE}/wish-types`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(typeForm.value)
      });
      const d = await r.json();
      if (!r.ok) { Toast.error(d.error || "创建失败"); return; }
      Toast.success("类型创建成功");
    }
    showTypeDialog.value = false;
    loadTypes();
  } catch (e) { Toast.error("操作失败"); }
}

function deleteType(type: WishTypeItem) {
  Dialog.warning({
    title: "确认删除",
    description: `确定要删除类型「${type.spec.displayName}」吗？该类型下的 ${getTypeCount(type.spec.slug)} 条便签也会一并删除，此操作不可恢复。`,
    confirmText: "删除",
    cancelText: "取消",
    onConfirm: async () => {
      try {
        await fetch(`${API_BASE}/wish-types/${type.metadata.name}`, { method: "DELETE" });
        wishTypes.value = wishTypes.value.filter(t => t.metadata.name !== type.metadata.name);
        typeStats.value = typeStats.value.filter(s => s.slug !== type.spec.slug);
        Toast.success("已删除");
      } catch (e) { Toast.error("删除失败"); }
    }
  });
}

// --- Wish list ---
interface WishSpec {
  content: string; nickname: string; type: string; color: string;
  status: string; anonymous: boolean; aiReply: string; emotionTag: string;
  ip: string; doneImage: string; doneNote: string; priority: string;
  createdAt: string; completedAt: string;
}
interface Wish { metadata: { name: string }; spec: WishSpec; }

const wishes = ref<Wish[]>([]);
const listTab = ref("all");
const listLoading = ref(false);

const dynamicTabs = computed(() => {
  const tabs: { k: string; l: string }[] = [{ k: "all", l: "全部" }, { k: "pending", l: "待审核" }];
  for (const t of wishTypes.value) {
    tabs.push({ k: t.spec.slug, l: t.spec.displayName });
  }
  return tabs;
});

const filteredWishes = computed(() => {
  let list = wishes.value;
  if (listTab.value === "pending") list = list.filter(w => w.spec.status === "pending_review");
  else if (listTab.value !== "all") list = list.filter(w => w.spec.type === listTab.value);
  return [...list].sort((a, b) => {
    const ta = a.spec.createdAt ? new Date(a.spec.createdAt).getTime() : 0;
    const tb = b.spec.createdAt ? new Date(b.spec.createdAt).getTime() : 0;
    return tb - ta;
  });
});
const pendingCount = computed(() => wishes.value.filter(w => w.spec.status === "pending_review").length);

const viewWish = ref<Wish | null>(null);

// --- Batch selection ---
const selectedWishes = ref<Set<string>>(new Set());
const isSelectMode = ref(false);

const allFilteredSelected = computed(() => {
  if (filteredWishes.value.length === 0) return false;
  return filteredWishes.value.every(w => selectedWishes.value.has(w.metadata.name));
});

function toggleSelectMode() {
  isSelectMode.value = !isSelectMode.value;
  if (!isSelectMode.value) selectedWishes.value = new Set();
}

function toggleSelectAll() {
  if (allFilteredSelected.value) {
    filteredWishes.value.forEach(w => selectedWishes.value.delete(w.metadata.name));
  } else {
    filteredWishes.value.forEach(w => selectedWishes.value.add(w.metadata.name));
  }
  selectedWishes.value = new Set(selectedWishes.value);
}

function toggleSelectWish(name: string) {
  const s = new Set(selectedWishes.value);
  if (s.has(name)) s.delete(name); else s.add(name);
  selectedWishes.value = s;
}

function batchDelete() {
  const count = selectedWishes.value.size;
  if (count === 0) return;
  Dialog.warning({
    title: "确认批量删除",
    description: `确定要删除选中的 ${count} 条便签吗？此操作不可恢复。`,
    confirmText: "删除",
    cancelText: "取消",
    onConfirm: async () => {
      try {
        await fetch(`${API_BASE}/wishes/-/batch-delete`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ names: Array.from(selectedWishes.value) })
        });
        Toast.success(`已删除 ${count} 条便签`);
        selectedWishes.value = new Set();
        isSelectMode.value = false;
        loadWishes();
      } catch (e) { Toast.error("批量删除失败"); }
    }
  });
}

// --- 心愿达成弹窗 ---
const showDoneDialog = ref(false);
const doneTarget = ref<Wish | null>(null);
const doneForm = ref({ doneNote: "", doneImage: "" });
const doneSaving = ref(false);
const doneImageSelectorVisible = ref(false);

function onDoneImageSelect(attachments: any[]) {
  if (attachments.length > 0) {
    doneForm.value.doneImage = attachments[0].status?.permalink || attachments[0].spec?.url || '';
  }
  doneImageSelectorVisible.value = false;
}

function openDoneDialog(wish: Wish) {
  doneTarget.value = wish;
  doneForm.value = { doneNote: wish.spec.doneNote || "", doneImage: wish.spec.doneImage || "" };
  showDoneDialog.value = true;
}

async function submitDone() {
  if (!doneTarget.value) return;
  doneSaving.value = true;
  try {
    await fetch(`${API_BASE}/wishes/${doneTarget.value.metadata.name}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status: "done", doneNote: doneForm.value.doneNote, doneImage: doneForm.value.doneImage })
    });
    Toast.success("心愿已达成 🎉");
    showDoneDialog.value = false;
    doneTarget.value = null;
    loadWishes();
  } catch (e) { Toast.error("操作失败"); }
  finally { doneSaving.value = false; }
}

function typeDisplayName(slug: string): string {
  const t = wishTypes.value.find(x => x.spec.slug === slug);
  return t ? t.spec.displayName : slug;
}

async function loadWishes() {
  listLoading.value = true;
  try { const r = await fetch(`${API_BASE}/wishes`); wishes.value = await r.json(); }
  catch (e) { console.error("加载失败", e); }
  finally { listLoading.value = false; }
}
async function approveWish(n: string) { await fetch(`${API_BASE}/wishes/${n}/approve`, { method: "POST" }); loadWishes(); }
function rejectWish(n: string) {
  Dialog.warning({
    title: "确认拒绝",
    description: "确定要拒绝这条便签吗？",
    confirmText: "拒绝",
    cancelText: "取消",
    onConfirm: async () => {
      await fetch(`${API_BASE}/wishes/${n}/reject`, { method: "POST" });
      loadWishes();
    }
  });
}
function deleteWish(n: string) {
  Dialog.warning({
    title: "确认删除",
    description: "确定删除这条便签吗？此操作不可恢复。",
    confirmText: "删除",
    cancelText: "取消",
    onConfirm: async () => {
      await fetch(`${API_BASE}/wishes/${n}`, { method: "DELETE" });
      wishes.value = wishes.value.filter(w => w.metadata.name !== n);
    }
  });
}
async function updateStatus(n: string, s: string) {
  await fetch(`${API_BASE}/wishes/${n}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status: s }) });
  loadWishes();
}
function fmtDate(d: string) { return d ? new Date(d).toLocaleString("zh-CN") : "-"; }
function stLabel(s: string) { return ({ pending:"心愿中", doing:"进行中", done:"已达成", approved:"已通过", pending_review:"待审核", rejected:"已拒绝" } as any)[s] || s; }
function stClass(s: string) { return ({ pending:"st-pending", doing:"st-doing", done:"st-done", approved:"st-done", pending_review:"st-review", rejected:"st-rejected" } as any)[s] || ""; }

// --- Settings ---
const configMapData = ref<Record<string, any>>({});
const sLoading = ref(false);
const sSaving = ref(false);
const basic = ref({ pageTitle: "心愿墙", pageSubtitle: "写下你的心愿，留下你的故事", showDaysCounter: false, anniversaryDate: "", partnerNameA: "", partnerNameB: "", enableBuiltinPage: false });
const treehole = ref({ enableSubmit: true, reviewMode: "ai", rateLimit: 3, maxLength: 200, blockedWords: "" });
const ai = ref({ enabled: false, provider: "openai", apiBase: "https://api.openai.com", apiKey: "", model: "gpt-4o-mini", customModel: "", enableWarmReply: true, enableEmotionTag: true, enableContentReview: true, warmReplyPrompt: "" });
const notification = ref({ enabled: false, recipientEmail: "" });

async function fetchSettings() {
  sLoading.value = true;
  try {
    const { data } = await consoleApiClient.plugin.plugin.fetchPluginJsonConfig({ name: PLUGIN_NAME });
    configMapData.value = data || {};
    if (data?.basic) basic.value = { ...basic.value, ...data.basic };
    if (data?.treehole) treehole.value = { ...treehole.value, ...data.treehole };
    if (data?.ai) ai.value = { ...ai.value, ...data.ai };
    if (data?.notification) notification.value = { ...notification.value, ...data.notification };
  } catch (e) { console.error("加载设置失败", e); }
  finally { sLoading.value = false; }
}
async function saveAllSettings() {
  sSaving.value = true;
  try {
    if (notification.value.enabled && !notification.value.recipientEmail.trim()) {
      Toast.warning("启用邮箱通知后，请填写接收邮箱");
      return;
    }
    const body = {
      ...configMapData.value,
      basic: basic.value,
      treehole: treehole.value,
      ai: ai.value,
      notification: {
        ...notification.value,
        recipientEmail: notification.value.recipientEmail.trim()
      }
    };
    await consoleApiClient.plugin.plugin.updatePluginJsonConfig({ name: PLUGIN_NAME, body });
    configMapData.value = body;
    Toast.success("保存成功");
  } catch (e) { Toast.error("保存失败"); }
  finally { sSaving.value = false; }
}

// --- 导出/导入 ---
async function exportData() {
  try {
    const r = await fetch(`${API_BASE}/wishes/-/export`);
    const data = await r.json();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `wishboard-export-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
    Toast.success("导出成功");
  } catch (e) { Toast.error("导出失败"); }
}

const importFileInput = ref<HTMLInputElement | null>(null);
function triggerImport() { importFileInput.value?.click(); }
async function handleImportFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0];
  if (!file) return;
  try {
    const text = await file.text();
    const data = JSON.parse(text);
    if (!data.wishes && !data.types) { Toast.error("无效的导入文件"); return; }
    Dialog.warning({
      title: "确认导入",
      description: `将导入 ${data.wishes?.length || 0} 条便签和 ${data.types?.length || 0} 个类型（已存在的类型会跳过）。`,
      confirmText: "导入",
      cancelText: "取消",
      onConfirm: async () => {
        try {
          const r = await fetch(`${API_BASE}/wishes/-/import`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
          });
          const d = await r.json();
          Toast.success(d.message || "导入成功");
          loadWishes();
          loadTypes();
        } catch (err) { Toast.error("导入失败"); }
      }
    });
  } catch (err) { Toast.error("文件解析失败，请确认是有效的 JSON 文件"); }
  // 重置 input 以便再次选择同一文件
  (e.target as HTMLInputElement).value = "";
}

onMounted(() => {
  loadWishes(); loadTypes(); fetchSettings();
  document.addEventListener('click', () => { openDropdown.value = ''; });
});
</script>

<template>
  <div class="ah-page">
    <div class="ah-card">
      <div class="ah-float-nav">
        <button type="button" :class="['ah-float-btn', { active: activeNav === 'list' }]" title="便签管理" @click="activeNav = 'list'">
          <svg class="wb-nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>
          <span v-if="pendingCount > 0" class="ah-nav-badge">{{ pendingCount > 99 ? '99+' : pendingCount }}</span>
        </button>
        <button type="button" :class="['ah-float-btn', { active: activeNav === 'types' }]" title="类型管理" @click="activeNav = 'types'">
          <svg class="wb-nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h16M4 12h10M4 17h6"/><circle cx="19" cy="17" r="3"/></svg>
        </button>
        <button type="button" :class="['ah-float-btn', { active: activeNav === 'settings' }]" title="插件设置" @click="activeNav = 'settings'">
          <svg class="wb-nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
        </button>
      </div>

      <div class="ah-corner ah-corner-tl"></div>
      <div class="ah-corner ah-corner-tr"></div>
      <div class="ah-corner ah-corner-bl"></div>
      <div class="ah-corner ah-corner-br"></div>

      <div class="ah-topbar">
        <div class="ah-topbar-left">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="#8081FF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M7 3h10a2 2 0 0 1 2 2v14l-7-4-7 4V5a2 2 0 0 1 2-2z"/>
            <path d="M9 7h6"/>
            <path d="M9 10h4"/>
          </svg>
          <span class="ah-topbar-brand">心愿<span class="ah-topbar-accent">便签</span></span>
          <span class="ah-topbar-page-title">{{ pageTitle }}</span>
        </div>
        <div class="ah-topbar-right">
          <button
            v-if="activeNav === 'settings'"
            class="ah-topbar-tab"
            :class="{ active: true }"
            :disabled="sSaving"
            @click="saveAllSettings"
          >
            {{ sSaving ? '保存中...' : '保存设置' }}
          </button>
        </div>
      </div>

      <div class="ah-body">
        <div class="wb-main">
        <!-- ====== 便签管理 ====== -->
        <template v-if="activeNav === 'list'">
          <div class="wb-s-sticky-layout">
            <!-- 固定区域：标题 + tabs -->
            <div class="wb-s-sticky-top">
              <div class="wb-s-header">
                <div class="wb-s-header-left">
                  <span class="wb-s-icon" style="background:linear-gradient(135deg,#fce7f3,#fbcfe8)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#ec4899" stroke-width="2" width="14" height="14"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>
                  </span>
                  <div><div class="wb-s-title">便签管理</div><div class="wb-s-sub">查看和管理所有便签数据</div></div>
                </div>
                <div style="display:flex;gap:6px">
                  <template v-if="isSelectMode">
                    <button class="wb-s-action-btn" @click="toggleSelectAll">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
                      {{ allFilteredSelected ? '取消全选' : '全选' }}
                    </button>
                    <button class="wb-s-btn-batch-delete" :disabled="selectedWishes.size === 0" @click="batchDelete">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                      删除选中 ({{ selectedWishes.size }})
                    </button>
                    <button class="wb-s-action-btn" @click="toggleSelectMode">取消</button>
                  </template>
                  <template v-else>
                    <button class="wb-s-action-btn" @click="toggleSelectMode">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
                      批量操作
                    </button>
                    <button class="wb-s-action-btn" @click="exportData">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                    导出
                  </button>
                  <button class="wb-s-action-btn" @click="triggerImport">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                    导入
                  </button>
                  <input ref="importFileInput" type="file" accept=".json" style="display:none" @change="handleImportFile" />
                  <button class="wb-s-action-btn" @click="loadWishes" :disabled="listLoading">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                    {{ listLoading ? '...' : '刷新' }}
                  </button>
                  </template>
                </div>
              </div>
              <div class="wb-s-card" style="padding:10px 14px;margin-bottom:0">
                <div class="wb-s-tabs">
                  <button v-for="t in dynamicTabs" :key="t.k" :class="['wb-s-tab', { active: listTab === t.k }]" @click="listTab = t.k">
                    {{ t.l }}<span v-if="t.k === 'pending' && pendingCount > 0" class="wb-s-tab-badge">{{ pendingCount }}</span>
                  </button>
                </div>
              </div>
            </div>
            <!-- 可滚动列表区域 -->
            <div class="wb-s-scroll-body">
              <div v-if="filteredWishes.length === 0" class="wb-s-empty">暂无数据</div>
              <div v-else class="wb-s-wish-list">
                <div v-for="wish in filteredWishes" :key="wish.metadata.name" :class="['wb-s-wish-item', { 'wb-selected': isSelectMode && selectedWishes.has(wish.metadata.name) }]" @click="isSelectMode ? toggleSelectWish(wish.metadata.name) : (viewWish = wish)">
                  <div v-if="isSelectMode" class="wb-s-wish-checkbox" @click.stop="toggleSelectWish(wish.metadata.name)">
                    <input type="checkbox" :checked="selectedWishes.has(wish.metadata.name)" />
                  </div>
                  <div class="wb-s-wish-body">
                    <div class="wb-s-wish-top">
                      <span class="wb-s-wish-type">{{ typeDisplayName(wish.spec.type) }}</span>
                      <span :class="['wb-s-wish-status', stClass(wish.spec.status)]">{{ stLabel(wish.spec.status) }}</span>
                    </div>
                    <div class="wb-s-wish-content">{{ wish.spec.content }}</div>
                    <div v-if="wish.spec.aiReply" class="wb-s-wish-ai">AI: {{ wish.spec.aiReply }}</div>
                    <div class="wb-s-wish-meta">
                      <span>{{ wish.spec.anonymous ? "匿名" : wish.spec.nickname }}</span>
                      <span>{{ fmtDate(wish.spec.createdAt) }}</span>
                      <span v-if="wish.spec.ip" class="wb-s-wish-ip">{{ wish.spec.ip }}</span>
                    </div>
                  </div>
                  <div class="wb-s-wish-actions" @click.stop>
                    <template v-if="wish.spec.status === 'pending_review'">
                      <button class="wb-s-btn wb-s-btn-approve" @click="approveWish(wish.metadata.name)">通过</button>
                      <button class="wb-s-btn wb-s-btn-reject" @click="rejectWish(wish.metadata.name)">拒绝</button>
                    </template>
                    <template v-if="wish.spec.status !== 'done' && wish.spec.status !== 'pending_review' && wish.spec.status !== 'rejected'">
                      <button v-if="wish.spec.status === 'pending' || wish.spec.status === 'approved'" class="wb-s-btn wb-s-btn-action" @click="updateStatus(wish.metadata.name, 'doing')">开始</button>
                      <button v-if="wish.spec.status === 'doing'" class="wb-s-btn wb-s-btn-action" @click="openDoneDialog(wish)">完成</button>
                    </template>
                    <button class="wb-s-btn wb-s-btn-delete" @click="deleteWish(wish.metadata.name)">删除</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- ====== 类型管理 ====== -->
        <template v-if="activeNav === 'types'">
          <div class="wb-s-sticky-layout">
            <div class="wb-s-sticky-top">
              <div class="wb-s-header">
                <div class="wb-s-header-left">
                  <span class="wb-s-icon" style="background:linear-gradient(135deg,#e0f2fe,#bae6fd)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#0ea5e9" stroke-width="2" width="14" height="14"><path d="M4 7h16M4 12h10M4 17h6"/><circle cx="19" cy="17" r="3"/></svg>
                  </span>
                  <div><div class="wb-s-title">类型管理</div><div class="wb-s-sub">管理便签分类与自定义类型</div></div>
                </div>
                <div style="display:flex;gap:6px">
                  <button class="wb-s-action-btn" @click="loadTypes" :disabled="typesLoading">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                    {{ typesLoading ? '...' : '刷新' }}
                  </button>
                  <button class="wb-s-save-btn" @click="openAddType">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                    新增类型
                  </button>
                </div>
              </div>
            </div>
            <div class="wb-s-scroll-body">
              <div v-if="wishTypes.length === 0" class="wb-s-empty">暂无自定义类型</div>
              <div v-else class="wb-s-wish-list">
                <div v-for="t in wishTypes" :key="t.metadata.name" class="wb-s-type-item">
                  <div class="wb-s-type-body">
                    <div class="wb-s-type-top">
                      <span class="wb-s-type-name">{{ t.spec.displayName }}</span>
                      <span class="wb-s-type-slug">{{ t.spec.slug }}</span>
                      <span v-if="t.spec.builtIn" class="wb-s-type-builtin">内置</span>
                    </div>
                    <div v-if="t.spec.description" class="wb-s-type-desc">{{ t.spec.description }}</div>
                    <div class="wb-s-type-meta">
                      <span class="wb-s-type-count">{{ getTypeCount(t.spec.slug) }} 条数据</span>
                      <span>排序: {{ t.spec.priority }}</span>
                    </div>
                  </div>
                  <div class="wb-s-wish-actions">
                    <button class="wb-s-btn wb-s-btn-action" @click="openEditType(t)">编辑</button>
                    <button class="wb-s-btn wb-s-btn-delete" @click="deleteType(t)">删除</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- ====== 插件设置（合并页面） ====== -->
        <template v-if="activeNav === 'settings'">
          <div class="wb-settings">
            <div class="wb-s-header">
              <div class="wb-s-header-left">
                <span class="wb-s-icon" style="background:linear-gradient(135deg,#fce7f3,#fbcfe8)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="#ec4899" stroke-width="2" width="14" height="14"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
                </span>
                <div><div class="wb-s-title">插件设置</div><div class="wb-s-sub">便签墙的页面、投稿与 AI 配置</div></div>
              </div>
            </div>

            <!-- 页面设置 -->
            <div class="wb-s-card">
              <div class="wb-s-card-title"><span class="wb-s-dot" style="background:#3b82f6"></span>页面设置</div>
              <div class="wb-s-card-body">
                <div class="wb-s-toggle-item">
                  <div class="wb-s-toggle-info"><span class="wb-s-toggle-label">启用插件内置页面</span><span class="wb-s-toggle-desc">开启后插件直接提供 /wishes 页面路由；关闭时需在后台「页面」中创建自定义页面（模板选「便签墙」，别名设为 wishes）</span></div>
                  <label class="wb-s-toggle"><input type="checkbox" v-model="basic.enableBuiltinPage" /><span class="wb-s-slider"></span></label>
                </div>
                <div class="wb-s-grid-2">
                  <div class="wb-s-field"><label>页面标题</label><input v-model="basic.pageTitle" /></div>
                  <div class="wb-s-field"><label>页面副标题</label><input v-model="basic.pageSubtitle" /></div>
                </div>
                <div class="wb-s-toggle-item">
                  <div class="wb-s-toggle-info"><span class="wb-s-toggle-label">在一起天数</span><span class="wb-s-toggle-desc">在页面顶部显示纪念日计数</span></div>
                  <label class="wb-s-toggle"><input type="checkbox" v-model="basic.showDaysCounter" /><span class="wb-s-slider"></span></label>
                </div>
                <div v-if="basic.showDaysCounter" class="wb-s-grid-3">
                  <div class="wb-s-field"><label>纪念日</label><input v-model="basic.anniversaryDate" placeholder="2024-01-01" /></div>
                  <div class="wb-s-field"><label>昵称 A</label><input v-model="basic.partnerNameA" /></div>
                  <div class="wb-s-field"><label>昵称 B</label><input v-model="basic.partnerNameB" /></div>
                </div>
              </div>
            </div>

            <!-- 投稿设置 -->
            <div class="wb-s-card">
              <div class="wb-s-card-title"><span class="wb-s-dot" style="background:#34d399"></span>投稿设置</div>
              <div class="wb-s-card-body">
                <div class="wb-s-toggle-item">
                  <div class="wb-s-toggle-info"><span class="wb-s-toggle-label">允许前端投稿</span><span class="wb-s-toggle-desc">关闭后隐藏输入框，仅展示已有便签</span></div>
                  <label class="wb-s-toggle"><input type="checkbox" v-model="treehole.enableSubmit" /><span class="wb-s-slider"></span></label>
                </div>
                <div class="wb-s-field">
                  <label>审核模式</label>
                  <div class="wb-select" @click.stop="toggleDrop('reviewMode')">
                    <div class="wb-select-trigger">{{ { none: '免审核', ai: 'AI 预审', manual: '人工审核' }[treehole.reviewMode] || '请选择' }}<span class="wb-select-arrow" :class="{ open: openDropdown === 'reviewMode' }"></span></div>
                    <div v-if="openDropdown==='reviewMode'" class="wb-select-dropdown">
                      <div v-for="o in [{ v: 'none', l: '免审核' }, { v: 'ai', l: 'AI 预审' }, { v: 'manual', l: '人工审核' }]" :key="o.v" :class="['wb-select-item', { active: treehole.reviewMode === o.v }]" @click.stop="selectOpt(treehole, 'reviewMode', o.v)">{{ o.l }}</div>
                    </div>
                  </div>
                  <div v-if="treehole.reviewMode === 'ai' && !ai.enabled" class="wb-s-warn">提示：未启用 AI，预审将自动降级为人工审核</div>
                </div>
                <div class="wb-s-grid-2">
                  <div class="wb-s-field"><label>频率限制（次/小时）</label><input type="number" v-model.number="treehole.rateLimit" min="1" max="20" /></div>
                  <div class="wb-s-field"><label>内容长度限制</label><input type="number" v-model.number="treehole.maxLength" min="10" max="500" /></div>
                </div>
                <div class="wb-s-field"><label>敏感词黑名单（每行一个）</label><textarea v-model="treehole.blockedWords" rows="2"></textarea></div>
              </div>
            </div>

            <!-- 邮箱通知 -->
            <div class="wb-s-card">
              <div class="wb-s-card-title"><span class="wb-s-dot" style="background:#f59e0b"></span>邮箱通知</div>
              <div class="wb-s-card-body">
                <div class="wb-s-toggle-item">
                  <div class="wb-s-toggle-info">
                    <span class="wb-s-toggle-label">启用邮箱通知</span>
                    <span class="wb-s-toggle-desc">访客成功提交新便签后，通过 Halo 邮件通知指定邮箱</span>
                  </div>
                  <label class="wb-s-toggle"><input type="checkbox" v-model="notification.enabled" /><span class="wb-s-slider"></span></label>
                </div>
                <div v-if="notification.enabled" class="wb-s-field">
                  <label>接收邮箱</label>
                  <input v-model.trim="notification.recipientEmail" type="email" placeholder="admin@example.com" autocomplete="email" />
                  <div class="wb-s-field-hint">邮件通道使用 Halo 后台配置的 SMTP，模板样式与站点维护插件一致。</div>
                </div>
              </div>
            </div>

            <!-- AI 设置 -->
            <div class="wb-s-card">
              <div class="wb-s-card-title"><span class="wb-s-dot" style="background:#a78bfa"></span>AI 设置</div>
              <div class="wb-s-card-body">
                <div class="wb-s-toggle-item">
                  <div class="wb-s-toggle-info"><span class="wb-s-toggle-label">启用 AI</span><span class="wb-s-toggle-desc">开启后可使用暖心回复、情绪标签、内容审核等功能</span></div>
                  <label class="wb-s-toggle"><input type="checkbox" v-model="ai.enabled" /><span class="wb-s-slider"></span></label>
                </div>
                <template v-if="ai.enabled">
                  <div class="wb-s-grid-2">
                    <div class="wb-s-field">
                      <label>提供商</label>
                      <div class="wb-select" @click.stop="toggleDrop('provider')">
                        <div class="wb-select-trigger">{{ { openai: 'OpenAI', dashscope: '通义千问', deepseek: 'DeepSeek', custom: '自定义' }[ai.provider] || '请选择' }}<span class="wb-select-arrow" :class="{ open: openDropdown === 'provider' }"></span></div>
                        <div v-if="openDropdown==='provider'" class="wb-select-dropdown">
                          <div v-for="o in [{ v: 'openai', l: 'OpenAI' }, { v: 'dashscope', l: '通义千问' }, { v: 'deepseek', l: 'DeepSeek' }, { v: 'custom', l: '自定义 (OpenAI 兼容)' }]" :key="o.v" :class="['wb-select-item', { active: ai.provider === o.v }]" @click.stop="selectOpt(ai, 'provider', o.v)">{{ o.l }}</div>
                        </div>
                      </div>
                    </div>
                    <div class="wb-s-field"><label>API Key</label><input type="password" v-model="ai.apiKey" /></div>
                  </div>
                  <div class="wb-s-grid-2">
                    <div class="wb-s-field"><label>API 地址</label><input v-model="ai.apiBase" /></div>
                    <div class="wb-s-field">
                      <label>模型</label>
                      <div class="wb-select" @click.stop="toggleDrop('model')">
                        <div class="wb-select-trigger">{{ ai.customModel || ai.model || '请选择' }}<span class="wb-select-arrow" :class="{open: openDropdown==='model'}"></span></div>
                        <div v-if="openDropdown==='model'" class="wb-select-dropdown">
                          <div class="wb-select-group">OpenAI</div>
                          <div v-for="m in ['gpt-4o-mini','gpt-4o','gpt-3.5-turbo']" :key="m" :class="['wb-select-item',{active:ai.model===m}]" @click.stop="selectOpt(ai,'model',m)">{{ m }}</div>
                          <div class="wb-select-group">通义千问</div>
                          <div v-for="m in ['qwen-turbo','qwen-plus','qwen-max']" :key="m" :class="['wb-select-item',{active:ai.model===m}]" @click.stop="selectOpt(ai,'model',m)">{{ m }}</div>
                          <div class="wb-select-group">DeepSeek</div>
                          <div v-for="m in ['deepseek-chat','deepseek-reasoner']" :key="m" :class="['wb-select-item',{active:ai.model===m}]" @click.stop="selectOpt(ai,'model',m)">{{ m }}</div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div class="wb-s-field"><label>自定义模型名称（填写后优先使用）</label><input v-model="ai.customModel" placeholder="留空则使用上方选择的模型" /></div>
                  <div class="wb-s-divider"></div>
                  <div class="wb-s-toggle-item">
                    <div class="wb-s-toggle-info"><span class="wb-s-toggle-label">暖心回复</span><span class="wb-s-toggle-desc">AI 自动为每条便签生成一句温暖的回应</span></div>
                    <label class="wb-s-toggle"><input type="checkbox" v-model="ai.enableWarmReply" /><span class="wb-s-slider"></span></label>
                  </div>
                  <div class="wb-s-toggle-item">
                    <div class="wb-s-toggle-info"><span class="wb-s-toggle-label">情绪标签</span><span class="wb-s-toggle-desc">AI 识别便签情绪并标记 emoji</span></div>
                    <label class="wb-s-toggle"><input type="checkbox" v-model="ai.enableEmotionTag" /><span class="wb-s-slider"></span></label>
                  </div>
                  <div class="wb-s-toggle-item wb-s-toggle-last">
                    <div class="wb-s-toggle-info"><span class="wb-s-toggle-label">内容审核</span><span class="wb-s-toggle-desc">AI 预审时用于判断内容是否违规</span></div>
                    <label class="wb-s-toggle"><input type="checkbox" v-model="ai.enableContentReview" /><span class="wb-s-slider"></span></label>
                  </div>
                  <div v-if="ai.enableWarmReply" class="wb-s-field"><label>暖心回复提示词</label><textarea v-model="ai.warmReplyPrompt" rows="2" placeholder="留空使用默认提示词"></textarea></div>
                </template>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <div v-if="viewWish" class="wb-s-overlay" @click.self="viewWish = null">
      <div class="wb-s-dialog">
        <div class="wb-s-dialog-header">
          <div class="wb-s-dialog-title">
            <span class="wb-s-dot" style="background:#ec4899"></span>
            {{ typeDisplayName(viewWish.spec.type) }} 详情
          </div>
          <button class="wb-s-dialog-close" @click="viewWish = null">&times;</button>
        </div>
        <div class="wb-s-dialog-body">
          <div class="wb-s-dialog-content">{{ viewWish.spec.content }}</div>
          <div v-if="viewWish.spec.aiReply" class="wb-s-wish-ai" style="margin-top:12px">AI: {{ viewWish.spec.aiReply }}</div>
          <!-- 心愿达成信息 -->
          <template v-if="viewWish.spec.status === 'done'">
            <div class="wb-s-divider" style="margin:12px 0"></div>
            <div class="wb-s-done-section">
              <div class="wb-s-done-badge">🎉 心愿已达成</div>
              <div v-if="viewWish.spec.completedAt" class="wb-s-done-time">达成时间：{{ fmtDate(viewWish.spec.completedAt) }}</div>
              <div v-if="viewWish.spec.doneNote" class="wb-s-done-note">{{ viewWish.spec.doneNote }}</div>
              <img v-if="viewWish.spec.doneImage" class="wb-s-done-img" :src="viewWish.spec.doneImage" alt="纪念照片" />
              <div v-if="!viewWish.spec.doneNote && !viewWish.spec.doneImage" class="wb-s-done-empty">
                暂无感言和纪念照片
                <button class="wb-s-btn wb-s-btn-action" style="margin-left:8px" @click="openDoneDialog(viewWish); viewWish = null">补充</button>
              </div>
            </div>
          </template>
          <div class="wb-s-wish-meta" style="margin-top:12px">
            <span>{{ viewWish.spec.anonymous ? '匿名' : viewWish.spec.nickname }}</span>
            <span>{{ fmtDate(viewWish.spec.createdAt) }}</span>
            <span :class="['wb-s-wish-status', stClass(viewWish.spec.status)]">{{ stLabel(viewWish.spec.status) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑类型弹窗 -->
    <div v-if="showTypeDialog" class="wb-s-overlay" @click.self="showTypeDialog = false">
      <div class="wb-s-dialog" style="width:420px">
        <div class="wb-s-dialog-header">
          <div class="wb-s-dialog-title">
            <span class="wb-s-dot" style="background:#0ea5e9"></span>
            {{ editingType ? '编辑类型' : '新增类型' }}
          </div>
          <button class="wb-s-dialog-close" @click="showTypeDialog = false">&times;</button>
        </div>
        <div class="wb-s-dialog-body">
          <div class="wb-s-field"><label>类型标识（英文，如 love-letter）</label><input v-model="typeForm.slug" placeholder="my-type" :disabled="!!editingType" :style="editingType ? 'opacity:0.6;cursor:not-allowed' : ''" /></div>
          <div class="wb-s-field"><label>显示名称</label><input v-model="typeForm.displayName" placeholder="我的类型" /></div>
          <div class="wb-s-field"><label>描述（可选）</label><input v-model="typeForm.description" placeholder="类型描述" /></div>
          <div class="wb-s-field"><label>排序权重（越小越靠前）</label><input type="number" v-model.number="typeForm.priority" min="0" max="100" /></div>
          <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:16px">
            <button class="wb-s-action-btn" @click="showTypeDialog = false">取消</button>
            <button class="wb-s-save-btn" @click="saveType">{{ editingType ? '保存' : '创建' }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 心愿达成弹窗 -->
    <div v-if="showDoneDialog && doneTarget" class="wb-s-overlay" @click.self="showDoneDialog = false">
      <div class="wb-s-dialog" style="width:460px">
        <div class="wb-s-dialog-header">
          <div class="wb-s-dialog-title">
            <span style="font-size:16px">🎉</span>
            心愿达成
          </div>
          <button class="wb-s-dialog-close" @click="showDoneDialog = false">&times;</button>
        </div>
        <div class="wb-s-dialog-body">
          <div class="wb-s-done-preview">{{ doneTarget.spec.content }}</div>
          <div class="wb-s-field">
            <label>达成感言（选填）</label>
            <textarea v-model="doneForm.doneNote" rows="3" placeholder="记录这个心愿达成的喜悦..."></textarea>
          </div>
          <div class="wb-s-field">
            <label>纪念照片（选填）</label>
            <div class="wb-s-input-row">
              <input v-model="doneForm.doneImage" placeholder="选择附件或输入URL" />
              <button type="button" class="wb-s-input-btn" @click="doneImageSelectorVisible = true" title="从附件库选择"><IconFolder /></button>
            </div>
          </div>
          <div v-if="doneForm.doneImage" class="wb-s-done-img-preview">
            <img :src="doneForm.doneImage" alt="预览" @error="($event.target as HTMLImageElement).style.display='none'" />
          </div>
          <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:16px">
            <button class="wb-s-action-btn" @click="showDoneDialog = false">取消</button>
            <button class="wb-s-save-btn" :disabled="doneSaving" @click="submitDone">
              {{ doneSaving ? '保存中...' : '🎉 标记达成' }}
            </button>
          </div>
        </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 附件选择器 -->
  <AttachmentSelectorModal v-if="doneImageSelectorVisible" :accepts="['image/*']" :min="1" :max="1" @select="onDoneImageSelect" @close="doneImageSelectorVisible = false" />
</template>

<style scoped>
/* ===== AstraHub shell ===== */
.ah-page {
  padding: 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  height: calc(100vh - 64px);
  max-height: calc(100vh - 64px);
  box-sizing: border-box;
  font-family: "Comic Sans MS", "Yuanti SC", "圆体-简", "华文圆体", "HYWenHei-85W", "LXGW WenKai", "Microsoft YaHei UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", system-ui, sans-serif;
  letter-spacing: .02em;
}
.ah-card {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 16px;
  padding-left: 72px;
  background: rgba(255,255,255,.75);
  backdrop-filter: blur(24px);
  border: 1px solid rgba(0,0,0,.08);
  border-radius: 24px;
  box-shadow: 0 18px 48px rgba(2,6,23,.06);
  overflow: hidden;
}
.ah-corner { position: absolute; width: 24px; height: 24px; pointer-events: none; z-index: 5; }
.ah-corner-tl { top: 8px; left: 8px; border-top: 2px solid rgba(37,99,235,.2); border-left: 2px solid rgba(37,99,235,.2); border-top-left-radius: 24px; }
.ah-corner-tr { top: 8px; right: 8px; border-top: 2px solid rgba(37,99,235,.2); border-right: 2px solid rgba(37,99,235,.2); border-top-right-radius: 24px; }
.ah-corner-bl { bottom: 8px; left: 8px; border-bottom: 2px solid rgba(37,99,235,.2); border-left: 2px solid rgba(37,99,235,.2); border-bottom-left-radius: 24px; }
.ah-corner-br { bottom: 8px; right: 8px; border-bottom: 2px solid rgba(37,99,235,.2); border-right: 2px solid rgba(37,99,235,.2); border-bottom-right-radius: 24px; }
.ah-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 20px;
  margin-left: -56px;
  margin-bottom: 14px;
  background: rgba(255,255,255,.7);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(0,0,0,.06);
  border-radius: 999px;
  box-shadow: 0 4px 16px rgba(0,0,0,.03);
  flex-shrink: 0;
  position: relative;
  z-index: 2;
}
.ah-topbar-left { display: flex; align-items: center; gap: 12px; }
.ah-topbar-brand { font-size: 15px; font-weight: 800; letter-spacing: -0.5px; color: #1e293b; }
.ah-topbar-accent { color: #7c3aed; }
.ah-topbar-page-title { font-size: 13px; font-weight: 600; color: #64748b; font-style: italic; letter-spacing: -0.2px; }
.ah-topbar-page-title :deep(.ah-kw) { color: #7c3aed; font-style: italic; }
.ah-topbar-right { display: flex; align-items: center; gap: 10px; }
.ah-topbar-btn { display: inline-flex; align-items: center; gap: 6px; height: 32px; padding: 0 14px; border-radius: 10px; border: 1px solid rgba(0,0,0,.08); background: rgba(255,255,255,.8); color: #64748b; font-size: 12px; font-weight: 600; cursor: pointer; transition: all .15s; }
.ah-topbar-btn:hover { border-color: rgba(37,99,235,.3); color: #2563eb; }
.ah-topbar-btn:disabled { opacity: .5; cursor: not-allowed; }
.ah-topbar-btn-primary { border-color: rgba(37,99,235,.3); background: rgba(37,99,235,.08); color: #2563eb; }
.ah-topbar-tab { display: inline-flex; align-items: center; outline: none; padding: 5px 14px; border: 2px dashed #64748b; border-radius: 15px; background-color: #f1f5f9; color: #64748b; font-size: 11px; font-weight: 600; cursor: pointer; transition: transform .2s ease-out; box-shadow: 0 0 0 3px #f1f5f9, 1.5px 1.5px 3px 1px rgba(0,0,0,.15); }
.ah-topbar-tab:hover { transform: translateY(-4px) translateX(-2px); box-shadow: 0 0 0 3px #f1f5f9, 2px 5px 0 0 currentColor; }
.ah-topbar-tab:active { transform: translateY(1px) translateX(1px); box-shadow: 0 0 0 3px #f1f5f9, 0 0 0 0 currentColor; }
.ah-topbar-tab.active { border-color: #075985; color: #075985; background-color: #f0f9ff; box-shadow: 0 0 0 3px #f0f9ff, 1.5px 1.5px 3px 1px rgba(0,0,0,.15); }
.ah-topbar-tab.active:hover { box-shadow: 0 0 0 3px #f0f9ff, 2px 5px 0 0 #075985; }
.ah-topbar-tab:disabled { opacity: .6; cursor: not-allowed; transform: none; }
.ah-topbar-search { display: inline-flex; align-items: center; gap: 6px; padding: 4px 12px 4px 12px; height: 30px; border: 2px dashed #64748b; border-radius: 999px; background: #f1f5f9; box-shadow: 0 0 0 3px #f1f5f9, 1.5px 1.5px 3px 1px rgba(0,0,0,.15); transition: transform .2s ease-out; }
.ah-topbar-search:focus-within { transform: translateY(-2px) translateX(-1px); border-color: #075985; background: #f0f9ff; box-shadow: 0 0 0 3px #f0f9ff, 2px 5px 0 0 #075985; }
.ah-topbar-search-icon { width: 14px; height: 14px; color: #64748b; flex-shrink: 0; }
.ah-topbar-search:focus-within .ah-topbar-search-icon { color: #075985; }
.ah-topbar-search-input { width: 140px; height: 22px; border: none; outline: none; background: transparent; color: #0f172a; font-size: 12px; font-weight: 600; padding: 0; }
.ah-topbar-search-input::placeholder { color: #94a3b8; font-weight: 500; }
.ah-topbar-search-input::-webkit-search-cancel-button { -webkit-appearance: none; height: 12px; width: 12px; cursor: pointer; background: linear-gradient(45deg, transparent 45%, #94a3b8 45% 55%, transparent 55%), linear-gradient(-45deg, transparent 45%, #94a3b8 45% 55%, transparent 55%); }
.ah-body {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  background: #ffffff;
  border: 1px solid rgba(0,0,0,.06);
  border-radius: 28px;
  box-shadow: 0 4px 16px rgba(0,0,0,.03);
  overflow: hidden;
}
.ah-float-nav {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 8px;
  background: rgba(248,250,252,.95);
  border: 1px solid rgba(0,0,0,.05);
  border-radius: 18px;
  box-shadow: 0 4px 16px rgba(0,0,0,.04);
  z-index: 20;
}
.ah-float-spacer { flex: 1; min-height: 8px; }
.ah-float-btn {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.18s;
}
.ah-float-btn:hover { background: rgba(37,99,235,.06); color: #2563eb; }
.ah-float-btn.active { background: rgba(37,99,235,.1); color: #2563eb; box-shadow: 0 0 12px rgba(37,99,235,.1); }
.ah-nav-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  box-shadow: 0 0 0 2px #fff;
  pointer-events: none;
}
.ah-topbar-tab--rel { position: relative; gap: 6px; }
.ah-tab-badge {
  min-width: 16px;
  height: 16px;
  padding: 0 5px;
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
}
.ah-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}
.ah-content *::-webkit-scrollbar { display: none; }
.ah-content * { scrollbar-width: none; -ms-overflow-style: none; }
.wb-nav-icon { width: 16px; height: 16px; flex-shrink: 0; }

/* ===== 鍙充晶涓诲唴瀹?===== */
.wb-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== Sticky layout (list/types) ===== */
.wb-s-sticky-layout { display: flex; flex-direction: column; flex: 1; min-height: 0; overflow: hidden; }
.wb-s-sticky-top { flex-shrink: 0; padding: 16px 20px 12px; }
.wb-s-scroll-body { flex: 1; min-height: 0; overflow-y: auto; padding: 0 20px 16px; }
.wb-s-scroll-body::-webkit-scrollbar { width: 4px; }
.wb-s-scroll-body::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 4px; }

/* ===== Action button (header) ===== */
.wb-s-action-btn {
  display: inline-flex; align-items: center; gap: 4px; padding: 6px 12px;
  border: 1px solid #e2e8f0; border-radius: 8px; font-size: 12px; font-weight: 500;
  background: #fff; color: #64748b; cursor: pointer; transition: all .15s; white-space: nowrap;
}
.wb-s-action-btn:hover:not(:disabled) { background: #f8fafc; border-color: #cbd5e1; color: #334155; }
.wb-s-action-btn:disabled { opacity: .5; cursor: not-allowed; }

/* ===== Filter tabs ===== */
.wb-s-tabs { display: flex; gap: 4px; flex-wrap: wrap; }
.wb-s-tab {
  padding: 5px 14px; border-radius: 6px; font-size: 12px; font-weight: 500;
  color: #64748b; background: none; border: none; cursor: pointer;
  transition: all .18s; display: inline-flex; align-items: center; gap: 4px;
}
.wb-s-tab:hover { color: #334155; background: rgba(0,0,0,.03); }
.wb-s-tab.active { background: rgba(212,114,122,.1); color: #d4727a; font-weight: 600; }
.wb-s-tab-badge {
  font-size: 10px; background: #ef4444; color: #fff;
  padding: 0 5px; border-radius: 8px; font-weight: 600; line-height: 1.5;
}

/* ===== Empty state ===== */
.wb-s-empty { padding: 60px 16px; text-align: center; color: #94a3b8; font-size: 13px; }

/* ===== Wish list (card items) ===== */
.wb-s-wish-list {
  display: flex; flex-direction: column; gap: 8px;
}
.wb-s-wish-item {
  display: flex; background: #fff; border: 1px solid #f0f2f7; border-radius: 12px;
  overflow: hidden; transition: all .2s ease; cursor: pointer;
}
.wb-s-wish-item:hover { border-color: #e2e6ee; box-shadow: 0 2px 12px rgba(0,0,0,.04); }
.wb-s-wish-body { flex: 1; padding: 12px 16px; min-width: 0; }
.wb-s-wish-top { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.wb-s-wish-type {
  font-size: 11px; font-weight: 600; color: #d4727a;
  background: rgba(212,114,122,.1); padding: 1px 8px; border-radius: 4px;
}
.wb-s-wish-status { font-size: 11px; font-weight: 500; padding: 1px 8px; border-radius: 4px; }
.st-pending { background: #fef3c7; color: #b45309; }
.st-doing { background: #dbeafe; color: #2563eb; }
.st-done { background: #dcfce7; color: #166534; }
.st-review { background: #fce7f3; color: #be185d; }
.st-rejected { background: #fee2e2; color: #dc2626; }
.wb-s-wish-content {
  font-size: 13px; color: #1e293b; line-height: 1.6; margin-bottom: 6px;
  word-break: break-all; display: -webkit-box; -webkit-line-clamp: 2;
  -webkit-box-orient: vertical; overflow: hidden;
}
.wb-s-wish-ai {
  font-size: 11px; color: #6366f1; background: #eef2ff;
  padding: 5px 10px; border-radius: 6px; margin-bottom: 6px; line-height: 1.5;
}
.wb-s-wish-meta { display: flex; align-items: center; gap: 10px; font-size: 11px; color: #94a3b8; }
.wb-s-wish-ip { font-family: 'SF Mono','Monaco','Menlo',monospace; font-size: 10px; }
.wb-s-wish-actions {
  display: flex; flex-direction: column; gap: 4px;
  padding: 10px 10px; justify-content: center; flex-shrink: 0;
}
.wb-s-btn {
  padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: 500;
  border: 1px solid transparent; cursor: pointer; transition: all .15s;
}
.wb-s-btn-approve { background: #dcfce7; color: #166534; border-color: #bbf7d0; }
.wb-s-btn-approve:hover { background: #bbf7d0; }
.wb-s-btn-reject { background: #fee2e2; color: #dc2626; border-color: #fecaca; }
.wb-s-btn-reject:hover { background: #fecaca; }
.wb-s-btn-action { background: #dbeafe; color: #2563eb; border-color: #bfdbfe; }
.wb-s-btn-action:hover { background: #bfdbfe; }
.wb-s-btn-delete { background: #f3f4f6; color: #6b7280; border-color: #e5e7eb; }
.wb-s-btn-delete:hover { background: #e5e7eb; color: #dc2626; }

/* ===== Type items ===== */
.wb-s-type-item {
  display: flex; background: #fff; border: 1px solid #f0f2f7; border-radius: 12px;
  overflow: hidden; transition: all .2s ease;
}
.wb-s-type-item:hover { border-color: #e2e6ee; box-shadow: 0 2px 12px rgba(0,0,0,.04); }
.wb-s-type-body { flex: 1; padding: 14px 16px; min-width: 0; }
.wb-s-type-top { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.wb-s-type-name { font-size: 13px; font-weight: 600; color: #1e293b; }
.wb-s-type-slug {
  font-size: 10px; color: #94a3b8; background: #f1f5f9;
  padding: 1px 8px; border-radius: 4px; font-family: 'SF Mono','Monaco','Menlo',monospace;
}
.wb-s-type-builtin {
  font-size: 10px; font-weight: 600; color: #d4727a;
  background: rgba(212,114,122,.1); padding: 1px 8px; border-radius: 4px;
}
.wb-s-type-desc { font-size: 12px; color: #6b7280; margin-bottom: 4px; }
.wb-s-type-meta { display: flex; align-items: center; gap: 12px; font-size: 11px; color: #94a3b8; }
.wb-s-type-count { font-weight: 600; color: #d4727a; }

/* ===== 鎻掍欢璁剧疆 ===== */
.wb-settings { padding: 16px 20px; overflow-y: auto; flex: 1; }
.wb-settings::-webkit-scrollbar { width: 4px; }
.wb-settings::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 4px; }

/* --- Header bar --- */
.wb-s-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.wb-s-header-left { display: flex; align-items: center; gap: 10px; }
.wb-s-icon { width: 32px; height: 32px; border-radius: 10px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.wb-s-title { font-size: 13px; font-weight: 600; color: #1e293b; line-height: 1.3; }
.wb-s-sub { font-size: 11px; color: #94a3b8; line-height: 1.3; margin-top: 1px; }
.wb-s-save-btn {
  display: inline-flex; align-items: center; gap: 5px; padding: 6px 14px;
  border: 1px solid #f9a8d4; border-radius: 8px; font-size: 12px; font-weight: 500;
  background: #fff; color: #ec4899; cursor: pointer; transition: all .15s; white-space: nowrap;
}
.wb-s-save-btn:hover:not(:disabled) { background: #fdf2f8; border-color: #f472b6; color: #db2777; }
.wb-s-save-btn:disabled { opacity: .5; cursor: not-allowed; }

/* --- Cards --- */
.wb-s-card {
  background: #fff; border: 1px solid #f0f2f7; border-radius: 14px;
  padding: 16px 18px; margin-bottom: 12px; transition: all .2s ease;
}
.wb-s-card:hover { border-color: #e2e6ee; box-shadow: 0 2px 12px rgba(0,0,0,.04); }
.wb-s-card:last-child { margin-bottom: 0; }
.wb-s-card-title { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 600; color: #1e293b; margin-bottom: 14px; }
.wb-s-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.wb-s-card-body { padding: 0; }

/* --- Grid layouts --- */
.wb-s-grid-2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; margin-bottom: 10px; }
.wb-s-grid-3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 10px; }

/* --- Field --- */
.wb-s-field { margin-bottom: 10px; }
.wb-s-field:last-child { margin-bottom: 0; }
.wb-s-field label { display: block; font-size: 12px; font-weight: 500; color: #64748b; margin-bottom: 6px; }
.wb-s-field input,
.wb-s-field textarea {
  width: 100%; padding: 7px 10px; border: 1px solid #e2e8f0; border-radius: 8px;
  font-size: 13px; background: #fff; box-sizing: border-box;
  transition: border-color .15s, box-shadow .15s; color: #1e293b;
}
.wb-s-field input:focus,
.wb-s-field textarea:focus { outline: none; border-color: #ec4899; box-shadow: 0 0 0 2px rgba(236,72,153,.1); }
.wb-s-field input::placeholder,
.wb-s-field textarea::placeholder { color: #cbd5e1; }
.wb-s-field textarea { resize: vertical; min-height: 52px; line-height: 1.5; }
.wb-s-field-hint { margin-top: 6px; color: #94a3b8; font-size: 11px; line-height: 1.5; }
.wb-s-input-row { display: flex; gap: 0; }
.wb-s-input-row input { border-top-right-radius: 0; border-bottom-right-radius: 0; flex: 1; }
.wb-s-input-btn { display: flex; align-items: center; justify-content: center; padding: 0 12px; border: 1px solid #e2e8f0; border-left: none; border-radius: 0 8px 8px 0; background: #f8fafc; cursor: pointer; transition: all .2s; color: #64748b; }
.wb-s-input-btn:hover { background: #fdf2f8; color: #ec4899; border-color: #fbcfe8; }

/* --- Toggle items --- */
.wb-s-toggle-item { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #f1f5f9; }
.wb-s-toggle-last { border-bottom: none; }
.wb-s-toggle-info { display: flex; flex-direction: column; gap: 2px; }
.wb-s-toggle-label { font-size: 13px; font-weight: 500; color: #1e293b; }
.wb-s-toggle-desc { font-size: 11px; color: #94a3b8; }
.wb-s-toggle { position: relative; display: inline-block; width: 36px; height: 20px; flex-shrink: 0; }
.wb-s-toggle input { opacity: 0; width: 0; height: 0; }
.wb-s-slider {
  position: absolute; cursor: pointer; inset: 0; background: #e2e8f0;
  border-radius: 20px; transition: .2s;
}
.wb-s-slider::before {
  content: ''; position: absolute; height: 16px; width: 16px; left: 2px; bottom: 2px;
  background: #fff; border-radius: 50%; transition: .2s; box-shadow: 0 1px 3px rgba(0,0,0,.1);
}
.wb-s-toggle input:checked + .wb-s-slider { background: #34d399; }
.wb-s-toggle input:checked + .wb-s-slider::before { transform: translateX(16px); }

/* --- Warning hint --- */
.wb-s-warn { font-size: 11px; color: #b45309; background: #fef3c7; padding: 6px 10px; border-radius: 6px; border: 1px solid #fde68a; margin-top: 6px; }

/* --- Divider --- */
.wb-s-divider { height: 1px; background: #f1f5f9; margin: 10px 0; }

/* ===== Custom select ===== */
.wb-select { position: relative; width: 100%; }
.wb-select-trigger {
  width: 100%; padding: 8px 36px 8px 12px; border: 1px solid #d1d5db; border-radius: 8px;
  font-size: 14px; background: #fff; cursor: pointer; transition: all 0.15s;
  display: flex; align-items: center; min-height: 38px; box-sizing: border-box;
  line-height: 1.5; color: #1f2937; position: relative;
}
.wb-select-trigger:hover { border-color: #d4727a; }
.wb-select-arrow {
  position: absolute; right: 14px; top: 50%; width: 6px; height: 6px;
  border-right: 1.5px solid #6b7280; border-bottom: 1.5px solid #6b7280;
  transform: translateY(-65%) rotate(45deg); transition: transform 0.2s;
}
.wb-select-arrow.open { transform: translateY(-35%) rotate(-135deg); }
.wb-select-dropdown {
  position: absolute; top: calc(100% + 4px); left: 0; right: 0; z-index: 50;
  background: #fff; border: 1px solid #e5e7eb; border-radius: 10px;
  box-shadow: 0 8px 24px -4px rgba(0,0,0,0.12); max-height: 220px;
  overflow-y: auto; padding: 4px; animation: wbDropIn 0.15s ease;
}
.wb-select-dropdown::-webkit-scrollbar { width: 4px; }
.wb-select-dropdown::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 4px; }
.wb-select-item {
  padding: 8px 12px; border-radius: 6px; cursor: pointer;
  transition: all 0.1s; font-size: 13px; color: #374151;
}
.wb-select-item:hover { background: #fff5f6; }
.wb-select-item.active { background: rgba(212,114,122,0.1); color: #d4727a; font-weight: 500; }
.wb-select-group {
  padding: 6px 12px 4px; font-size: 11px; font-weight: 600;
  color: #9ca3af; letter-spacing: 0.03em;
}
@keyframes wbDropIn { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: translateY(0); } }

.wb-form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }

/* ===== 寮圭獥 ===== */
.wb-s-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,.35); backdrop-filter: blur(2px);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
  animation: wbFadeIn .15s ease;
}
.wb-s-dialog {
  background: #fff; border-radius: 16px; width: 520px; max-width: 90vw;
  max-height: 80vh; overflow: hidden;
  box-shadow: 0 20px 60px rgba(0,0,0,.15), 0 0 0 1px rgba(0,0,0,.04);
  animation: wbSlideUp .2s ease;
}
.wb-s-dialog-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; border-bottom: 1px solid #f0f2f7;
}
.wb-s-dialog-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 14px; font-weight: 600; color: #1e293b;
}
.wb-s-dialog-close {
  width: 28px; height: 28px; border: none; background: #f1f5f9;
  border-radius: 8px; font-size: 16px; cursor: pointer;
  display: flex; align-items: center; justify-content: center; color: #94a3b8;
  transition: all .15s;
}
.wb-s-dialog-close:hover { background: #fee2e2; color: #ef4444; }
.wb-s-dialog-body { padding: 20px; overflow-y: auto; max-height: calc(80vh - 64px); }
.wb-s-dialog-content {
  font-size: 13px; color: #1e293b; line-height: 1.7;
  word-break: break-all; white-space: pre-wrap;
}
@keyframes wbFadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes wbSlideUp { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }


/* ===== Batch delete button ===== */
.wb-s-btn-batch-delete {
  display: inline-flex; align-items: center; gap: 4px; padding: 6px 12px;
  border: 1px solid #fecaca; border-radius: 8px; font-size: 12px; font-weight: 500;
  background: #fef2f2; color: #dc2626; cursor: pointer; transition: all .15s; white-space: nowrap;
}
.wb-s-btn-batch-delete:hover:not(:disabled) { background: #fee2e2; border-color: #fca5a5; }
.wb-s-btn-batch-delete:disabled { opacity: .5; cursor: not-allowed; }

/* ===== Wish checkbox ===== */
.wb-s-wish-checkbox {
  display: flex; align-items: center; justify-content: center;
  padding: 0 6px 0 14px; flex-shrink: 0; cursor: pointer;
}
.wb-s-wish-checkbox input[type="checkbox"] {
  width: 16px; height: 16px; accent-color: #d4727a; cursor: pointer;
}
.wb-s-wish-item.wb-selected { border-color: #f9a8d4; background: #fdf2f8; }

/* ===== 蹇冩効杈炬垚鐩稿叧 ===== */
.wb-s-done-section { background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 10px; padding: 14px; }
.wb-s-done-badge { font-size: 13px; font-weight: 600; color: #166534; margin-bottom: 8px; }
.wb-s-done-time { font-size: 11px; color: #6b7280; margin-bottom: 8px; }
.wb-s-done-note { font-size: 13px; color: #1e293b; line-height: 1.6; margin-bottom: 10px; white-space: pre-wrap; }
.wb-s-done-img { max-width: 100%; border-radius: 8px; margin-top: 6px; }
.wb-s-done-empty { font-size: 12px; color: #94a3b8; display: flex; align-items: center; }
.wb-s-done-preview {
  font-size: 13px; color: #64748b; background: #f8fafc; border: 1px solid #e2e8f0;
  border-radius: 8px; padding: 10px 14px; margin-bottom: 14px; line-height: 1.6;
}
.wb-s-done-img-preview { margin-top: 8px; margin-bottom: 4px; }
.wb-s-done-img-preview img { max-width: 100%; max-height: 200px; border-radius: 8px; object-fit: cover; }
</style>

