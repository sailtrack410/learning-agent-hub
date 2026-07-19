<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { employeeApi, type EmployeeFileFolderResponse, type EmployeeFileResponse, type EntityId } from '@/api';
import { useFilesSectionContext } from '../employee-shell-context';
import { isFileTrashed } from '@/utils/file-record';
import { fileSizeLabel, fileTypeLabelFromName } from '@/utils/file-format';
import { buildStorageOverview, parseStorageSizeBytes } from '@/utils/cloud-storage-summary';
import { formatDateTime } from '@/utils/employee-format';
import type { PersonalFileRecord } from '@/stores/shell';
import cloudHeroImage from '@/assets/education/generated/cloud-hero.webp';
import cloudRecentImage from '@/assets/education/generated/cloud-recent.webp';
import cloudUploadImage from '@/assets/education/generated/cloud-upload.webp';
import cloudFavoriteImage from '@/assets/education/generated/cloud-favorite.webp';
import cloudSharedImage from '@/assets/education/generated/cloud-shared.webp';
import archiveFileIcon from '@/assets/education/generated/cloud-file-archive.webp';
import excelFileIcon from '@/assets/education/generated/cloud-file-excel.webp';
import fileIcon from '@/assets/education/generated/cloud-file-generic.webp';
import folderFileIcon from '@/assets/education/generated/cloud-file-folder.webp';
import pdfFileIcon from '@/assets/education/generated/cloud-file-pdf.webp';
import pptFileIcon from '@/assets/education/generated/cloud-file-ppt.webp';
import wordFileIcon from '@/assets/education/generated/cloud-file-word.webp';
import AppButton from '../../components/ui/AppButton.vue';
import AppDialog from '../../components/ui/AppDialog.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppLoadingState from '../../components/ui/AppLoadingState.vue';

const {
  im,
  fileUploading,
  activeFile,
  currentMeta,
  backToList,
  runAction,
  showToast,
  setUploadFolderId,
  refreshFileCenter,
  sendSelectedFileToConversation,
  openPersonalFilePreview,
  downloadPersonalFile,
  trashPersonalFile,
  restorePersonalFile,
  purgePersonalFile,
} = useFilesSectionContext();

type FileTab = 'all' | 'trash';
type FileQuickView = 'all' | 'recentAccess' | 'recentUpload' | 'favorites' | 'shared';
type FilePlacementMode = 'move' | 'copy';

const fileTab = ref<FileTab>('all');
const quickView = ref<FileQuickView>('all');
const currentFolderId = ref<EntityId | null>(null);
const browserLoading = ref(false);
const browserError = ref('');
const browserFolders = ref<EmployeeFileFolderResponse[]>([]);
const browserFiles = ref<PersonalFileRecord[]>([]);
const breadcrumbs = ref<EmployeeFileFolderResponse[]>([]);
const allFolders = ref<EmployeeFileFolderResponse[]>([]);
const fileKeyword = ref('');
const fileTypeFilter = ref('all');
const folderNameDraft = ref('');
const folderActionError = ref('');
const placementTarget = ref<{ mode: FilePlacementMode; file: PersonalFileRecord } | null>(null);
const placementFolderId = ref('root');
const copyNameDraft = ref('');
const accessStorageKey = 'workforce-hub.employee.file-access.v1';
const favoriteStorageKey = 'workforce-hub.employee.file-favorites.v1';
const recentAccessLog = ref<Record<string, number>>(readRecentAccessLog());
const favoriteFileIds = ref<string[]>(readFavoriteFileIds());
const selectedFileRows = ref<string[]>([]);
let searchTimer: number | undefined;

const activeFiles = computed(() => activeFile.value.files.filter((file: PersonalFileRecord) => !isFileTrashed(file)));
const trashedFiles = computed(() => activeFile.value.files.filter((file: PersonalFileRecord) => isFileTrashed(file)));
const visibleTrashFiles = computed(() => trashedFiles.value);
const placementTitle = computed(() => placementTarget.value?.mode === 'copy' ? '复制到' : '移动到');
const recentUploadCount = computed(() => activeFiles.value.filter((file) => isWithinLastWeek(file.rawCreatedAt ?? file.createdAt)).length);
const recentAccessCount = computed(() => activeFiles.value.filter((file) => (recentAccessLog.value[fileIdentity(file)] ?? 0) >= weekAgoTime()).length);
const favoriteFileCount = computed(() => activeFiles.value.filter((file) => favoriteFileIds.value.includes(fileIdentity(file))).length);
const fileQuickCards = computed(() => [
  { key: 'recentAccess' as const, title: '最近访问', count: recentAccessCount.value },
  { key: 'recentUpload' as const, title: '最近上传', count: recentUploadCount.value },
  { key: 'favorites' as const, title: '收藏夹', count: favoriteFileCount.value },
  { key: 'shared' as const, title: '共享文件', count: 0 },
]);
const fileQuickImages = {
  recentAccess: cloudRecentImage,
  recentUpload: cloudUploadImage,
  favorites: cloudFavoriteImage,
  shared: cloudSharedImage,
} as const;
const quickViewTitles: Record<FileQuickView, string> = {
  all: '全部文件',
  recentAccess: '最近访问',
  recentUpload: '最近上传',
  favorites: '收藏夹',
  shared: '共享文件',
};
const quickViewEmptyDescriptions: Record<FileQuickView, string> = {
  all: '上传或新建文件夹后开始管理',
  recentAccess: '预览或下载文件后会出现在这里',
  recentUpload: '最近 7 天上传的文件会出现在这里',
  favorites: '收藏文件后会出现在这里',
  shared: '',
};
const quickViewFiles = computed(() => {
  const files = activeFiles.value
    .filter((file) => fileMatchesQuickView(file))
    .filter((file) => fileMatchesClientFilters(file));
  if (quickView.value === 'recentAccess') {
    return [...files].sort((a, b) => (recentAccessLog.value[fileIdentity(b)] ?? 0) - (recentAccessLog.value[fileIdentity(a)] ?? 0));
  }
  if (quickView.value === 'recentUpload') {
    return [...files].sort((a, b) => fileCreatedTime(b) - fileCreatedTime(a));
  }
  return files;
});
const visibleBrowserFolders = computed(() => quickView.value === 'all' ? browserFolders.value : []);
const visibleBrowserFiles = computed(() => quickView.value === 'all' ? browserFiles.value : quickViewFiles.value);
const emptyTitle = computed(() => {
  if (fileTab.value === 'trash') return '回收站为空';
  return quickView.value === 'all' ? '暂无文件' : `暂无${quickViewTitles[quickView.value]}`;
});
const emptyDescription = computed(() => fileTab.value === 'trash' ? '删除的文件会暂存在这里' : quickViewEmptyDescriptions[quickView.value]);
const storageUsedLabel = computed(() => formatStorageLabel(activeFile.value.summary[0]?.[0] ?? '0B'));
const storageTotalLabel = computed(() => formatStorageLabel(activeFile.value.summary[1]?.[0] ?? '0B'));
const storageUsedBytes = computed(() => parseStorageSizeBytes(storageUsedLabel.value));
const storageTotalBytes = computed(() => parseStorageSizeBytes(storageTotalLabel.value));
const storageOverview = computed(() => buildStorageOverview(activeFiles.value, storageUsedBytes.value, storageTotalBytes.value));
const storageSegments = computed(() => storageOverview.value.segments);
const storageUsagePercentLabel = computed(() => formatUsagePercent(storageOverview.value.usedPercent));

function mapBrowserFile(file: EmployeeFileResponse): PersonalFileRecord {
  const name = file.displayName || file.originalFileName || '未命名文件';
  return {
    id: `personal-file-${file.attachmentRefId}`,
    name,
    type: fileTypeLabelFromName(name, file.mimeType ?? ''),
    size: fileSizeLabel(file.fileSize),
    createdAt: formatDateTime(file.createdAt),
    rawCreatedAt: file.createdAt ?? undefined,
    fileSizeBytes: file.fileSize,
    status: file.deletedAt ? '回收站' : '已上传',
    attachmentRefId: file.attachmentRefId,
    folderId: file.folderId ?? null,
    mimeType: file.mimeType ?? undefined,
  };
}

function weekAgoTime() {
  return Date.now() - 7 * 24 * 60 * 60 * 1000;
}

function isWithinLastWeek(value?: string | null) {
  if (!value) return false;
  if (value === '刚刚') return true;
  const time = new Date(value).getTime();
  return Number.isFinite(time) && time >= weekAgoTime();
}

function fileCreatedTime(file: PersonalFileRecord) {
  const value = file.rawCreatedAt ?? file.createdAt;
  if (!value) return 0;
  if (value === '刚刚') return Date.now();
  const time = new Date(value).getTime();
  return Number.isFinite(time) ? time : 0;
}

function readRecentAccessLog() {
  try {
    return JSON.parse(globalThis.localStorage?.getItem(accessStorageKey) || '{}') as Record<string, number>;
  } catch {
    return {};
  }
}

function readFavoriteFileIds() {
  try {
    const value = JSON.parse(globalThis.localStorage?.getItem(favoriteStorageKey) || '[]') as unknown;
    return Array.isArray(value) ? value.map(String) : [];
  } catch {
    return [];
  }
}

function fileIdentity(file: PersonalFileRecord) {
  return String(file.attachmentRefId ?? file.id);
}

function recordFileAccess(file: PersonalFileRecord) {
  const next = { ...recentAccessLog.value, [fileIdentity(file)]: Date.now() };
  recentAccessLog.value = next;
  globalThis.localStorage?.setItem(accessStorageKey, JSON.stringify(next));
}

function toggleFavorite(file: PersonalFileRecord) {
  const id = fileIdentity(file);
  const next = favoriteFileIds.value.includes(id)
    ? favoriteFileIds.value.filter((item) => item !== id)
    : [...favoriteFileIds.value, id];
  favoriteFileIds.value = next;
  globalThis.localStorage?.setItem(favoriteStorageKey, JSON.stringify(next));
}

function isFavorite(file: PersonalFileRecord) {
  return favoriteFileIds.value.includes(fileIdentity(file));
}

function formatStorageLabel(value: string) {
  return value.replace(/^([\d.]+)([A-Z]+)$/i, '$1 $2');
}

function formatUsagePercent(value: number) {
  if (value > 0 && value < 0.01) return '<0.01';
  return value < 1 ? value.toFixed(2) : String(Math.round(value));
}

function folderLabel(folder: EmployeeFileFolderResponse) {
  const parent = allFolders.value.find((item) => String(item.folderId) === String(folder.parentFolderId));
  return parent ? `${parent.folderName} / ${folder.folderName}` : folder.folderName;
}

function folderSelectionValue(folder: EmployeeFileFolderResponse) {
  return `folder:${folder.folderId}`;
}

function fileSelectionValue(file: PersonalFileRecord) {
  return `file:${fileIdentity(file)}`;
}

function fileIconAssetOf(file: PersonalFileRecord) {
  const name = file.name.toLowerCase();
  const mime = file.mimeType?.toLowerCase() ?? '';
  if (mime.includes('pdf') || name.endsWith('.pdf')) return pdfFileIcon;
  if (/\.(doc|docx)$/i.test(name) || mime.includes('wordprocessingml') || mime.includes('msword')) return wordFileIcon;
  if (/\.(xls|xlsx|csv)$/i.test(name) || mime.includes('spreadsheet')) return excelFileIcon;
  if (/\.(ppt|pptx)$/i.test(name) || mime.includes('presentation')) return pptFileIcon;
  if (/\.(zip|rar|7z|tar|gz)$/i.test(name)) return archiveFileIcon;
  return fileIcon;
}

function fileMatchesQuickView(file: PersonalFileRecord) {
  if (quickView.value === 'all') return true;
  if (quickView.value === 'recentAccess') return (recentAccessLog.value[fileIdentity(file)] ?? 0) >= weekAgoTime();
  if (quickView.value === 'recentUpload') return isWithinLastWeek(file.rawCreatedAt ?? file.createdAt);
  if (quickView.value === 'favorites') return favoriteFileIds.value.includes(fileIdentity(file));
  return false;
}

function fileMatchesClientFilters(file: PersonalFileRecord) {
  const keyword = fileKeyword.value.trim().toLowerCase();
  if (keyword) {
    const haystack = [file.name, file.type, file.status, file.mimeType ?? ''].join(' ').toLowerCase();
    if (!haystack.includes(keyword)) return false;
  }
  if (fileTypeFilter.value === 'all') return true;
  const name = file.name.toLowerCase();
  const type = file.type.toLowerCase();
  const mime = file.mimeType?.toLowerCase() ?? '';
  if (fileTypeFilter.value === 'document') return /(\.pdf|\.docx?|\.txt|\.rtf|\.md)$/i.test(name) || type.includes('文档') || mime.includes('pdf') || mime.includes('word');
  if (fileTypeFilter.value === 'image') return /\.(png|jpe?g|gif|webp|svg|bmp)$/i.test(name) || mime.startsWith('image/');
  if (fileTypeFilter.value === 'sheet') return /\.(xls|xlsx|csv)$/i.test(name) || type.includes('表格') || mime.includes('spreadsheet');
  if (fileTypeFilter.value === 'archive') return /\.(zip|rar|7z|tar|gz)$/i.test(name) || type.includes('压缩');
  if (fileTypeFilter.value === 'media') return /\.(mp3|mp4|wav|mov|avi|m4a)$/i.test(name) || mime.startsWith('audio/') || mime.startsWith('video/');
  if (fileTypeFilter.value === 'code') return /\.(js|ts|java|py|go|json|xml|yml|yaml|css|html)$/i.test(name) || type.includes('代码');
  return true;
}

async function loadBrowser() {
  if (fileTab.value !== 'all' || quickView.value !== 'all') {
    browserLoading.value = false;
    return;
  }
  browserLoading.value = true;
  browserError.value = '';
  try {
    const [browser, folders] = await Promise.all([
      employeeApi.files.browser({
        folderId: currentFolderId.value ?? undefined,
        keyword: fileKeyword.value.trim() || undefined,
        fileType: fileTypeFilter.value,
      }),
      employeeApi.files.folders(),
    ]);
    browserFolders.value = browser.folders;
    browserFiles.value = browser.files.map(mapBrowserFile);
    breadcrumbs.value = browser.breadcrumbs;
    allFolders.value = folders;
  } catch (error) {
    browserError.value = error instanceof Error ? error.message : '文件加载失败';
  } finally {
    browserLoading.value = false;
  }
}

function openRootFolder() {
  currentFolderId.value = null;
}

function openFolder(folderId: EntityId) {
  currentFolderId.value = folderId;
}

async function createFolder() {
  const folderName = folderNameDraft.value.trim();
  folderActionError.value = '';
  if (!folderName) {
    folderActionError.value = '文件夹名称不能为空';
    return;
  }
  try {
    await employeeApi.files.createFolder({
      parentFolderId: currentFolderId.value,
      folderName,
    });
    folderNameDraft.value = '';
    await loadBrowser();
    showToast('文件夹已创建');
  } catch (error) {
    folderActionError.value = error instanceof Error ? error.message : '创建失败';
  }
}

function openPlacement(file: PersonalFileRecord, mode: FilePlacementMode) {
  placementTarget.value = { file, mode };
  placementFolderId.value = currentFolderId.value == null ? 'root' : String(currentFolderId.value);
  copyNameDraft.value = mode === 'copy' ? file.name : '';
}

function closePlacement() {
  placementTarget.value = null;
  placementFolderId.value = 'root';
  copyNameDraft.value = '';
  folderActionError.value = '';
}

async function submitPlacement() {
  const target = placementTarget.value;
  if (!target?.file.attachmentRefId) return;
  const targetFolderId = placementFolderId.value === 'root' ? null : placementFolderId.value;
  try {
    if (target.mode === 'copy') {
      await employeeApi.files.copy(target.file.attachmentRefId, {
        targetFolderId,
        displayName: copyNameDraft.value.trim() || undefined,
      });
      showToast('文件已复制');
    } else {
      await employeeApi.files.move(target.file.attachmentRefId, { targetFolderId });
      showToast('文件已移动');
    }
    closePlacement();
    await Promise.all([refreshFileCenter(), loadBrowser()]);
  } catch (error) {
    folderActionError.value = error instanceof Error ? error.message : '操作失败';
  }
}

async function trashBrowserFile(file: PersonalFileRecord) {
  await trashPersonalFile(file);
  await loadBrowser();
}

async function previewBrowserFile(file: PersonalFileRecord) {
  recordFileAccess(file);
  await openPersonalFilePreview(file);
}

async function downloadBrowserFile(file: PersonalFileRecord) {
  recordFileAccess(file);
  await downloadPersonalFile(file);
}

function selectAllFiles() {
  fileTab.value = 'all';
  quickView.value = 'all';
  selectedFileRows.value = [];
}

function selectTrashFiles() {
  fileTab.value = 'trash';
  quickView.value = 'all';
  selectedFileRows.value = [];
}

function selectQuickView(view: FileQuickView) {
  fileTab.value = 'all';
  quickView.value = view;
  selectedFileRows.value = [];
}

function uploadToCurrentFolder() {
  setUploadFolderId(quickView.value === 'all' ? currentFolderId.value : null);
  runAction(currentMeta.value.primaryAction);
}

watch([currentFolderId, fileTypeFilter, fileTab, quickView], () => {
  void loadBrowser();
});

watch(() => activeFile.value.files.length, () => {
  void loadBrowser();
});

watch(fileKeyword, () => {
  if (quickView.value !== 'all') return;
  if (searchTimer) window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(() => {
    void loadBrowser();
  }, 240);
});

onMounted(() => {
  void loadBrowser();
});
</script>

<template>
<div class="workspace generic-workspace">
  <header class="cloud-hero" :style="{ '--cloud-hero-image': `url(${cloudHeroImage})` }">
    <div class="cloud-hero-copy">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <span aria-hidden="true">‹</span>
      </button>
      <span class="cloud-hero-eyebrow">个人数字资料库</span>
      <h1>{{ activeFile.title }}</h1>
      <p>集中整理课程文件、作业附件与个人学习材料。</p>
      <div class="cloud-hero-actions">
        <AppButton variant="primary" size="sm" :disabled="fileUploading" @click="uploadToCurrentFolder">
          {{ fileUploading ? '上传中' : currentMeta.primaryAction?.label }}
        </AppButton>
        <span>已使用 {{ storageUsagePercentLabel }}%</span>
      </div>
    </div>
    <div class="cloud-capacity-badge" aria-label="存储用量">
      <small>当前用量</small>
      <strong>{{ storageUsedLabel }}</strong>
      <span>/ {{ storageTotalLabel }}</span>
    </div>
  </header>

  <section class="content-stack cloud-content-stack">
    <section class="cloud-summary-grid" aria-label="云空间概览">
      <div class="cloud-quick-grid">
        <button
          v-for="card in fileQuickCards"
          :key="card.key"
          class="cloud-folder-card"
          :class="{ active: fileTab === 'all' && quickView === card.key }"
          type="button"
          :aria-pressed="fileTab === 'all' && quickView === card.key"
          @click="selectQuickView(card.key)"
        >
          <span class="cloud-quick-icon">
            <img :src="fileQuickImages[card.key]" alt="" />
          </span>
          <div>
            <h3>{{ card.title }}</h3>
            <p>{{ card.count }} 个文件</p>
          </div>
        </button>
      </div>

      <article class="storage-overview-card">
        <header><span>存储概览</span><em>个人空间</em></header>
        <div class="storage-overview-body">
          <div class="storage-usage-line">
            <strong>{{ storageUsedLabel }}</strong>
            <span>/ {{ storageTotalLabel }}</span>
          </div>
          <div class="storage-ratio-bar" aria-hidden="true">
            <div class="storage-used-fill" :style="{ width: `${storageOverview.usedPercent}%` }">
              <span
                v-for="item in storageSegments"
                v-show="item.percent > 0"
                :key="item.key"
                :style="{ width: `${item.percent}%`, background: item.color }"
              />
            </div>
          </div>
          <div class="storage-legend">
            <span v-for="item in storageSegments" :key="item.key">
              <i :style="{ background: item.color }" />
              {{ item.label }}
            </span>
          </div>
        </div>
        <footer>已使用 {{ storageUsagePercentLabel }}%</footer>
      </article>
    </section>

    <section class="detail-card cloud-browser-card">
      <div class="title-line file-tabs">
        <div class="cloud-browser-title">
          <small>文件管理</small>
          <strong>{{ quickViewTitles[quickView] }}</strong>
        </div>
        <div class="settings-density-row">
          <button type="button" :class="{ active: fileTab === 'all' && quickView === 'all' }" @click="selectAllFiles">
            全部文件
            <span class="tab-count">{{ activeFiles.length }}</span>
          </button>
          <button type="button" :class="{ active: fileTab === 'trash' }" @click="selectTrashFiles">
            回收站
            <span class="tab-count">{{ trashedFiles.length }}</span>
          </button>
        </div>
      </div>

      <template v-if="fileTab === 'all'">
        <div class="file-browser-toolbar" :class="{ compact: quickView !== 'all' }">
          <label class="file-search-field">
            <input v-model="fileKeyword" type="search" placeholder="搜索文件或文件夹" />
          </label>
          <select v-model="fileTypeFilter" class="ui-inline-input file-type-select">
            <option value="all">全部类型</option>
            <option value="document">文档</option>
            <option value="image">图片</option>
            <option value="sheet">表格</option>
            <option value="archive">压缩包</option>
            <option value="media">音视频</option>
            <option value="code">代码</option>
          </select>
          <label v-if="quickView === 'all'" class="file-folder-create">
            <input v-model="folderNameDraft" class="ui-inline-input" placeholder="新建文件夹" @input="folderActionError = ''" @keydown.enter.prevent="createFolder" />
            <AppButton variant="secondary" size="sm" :disabled="!folderNameDraft.trim()" @click="createFolder">
              新建
            </AppButton>
          </label>
        </div>
        <p v-if="folderActionError" class="dialog-error">{{ folderActionError }}</p>

        <div v-if="quickView === 'all'" class="file-breadcrumb">
          <button type="button" :class="{ active: currentFolderId === null }" @click="openRootFolder">
            根目录
          </button>
          <button
            v-for="folder in breadcrumbs"
            :key="folder.folderId"
            type="button"
            :class="{ active: String(currentFolderId) === String(folder.folderId) }"
            @click="openFolder(folder.folderId)"
          >
            {{ folder.folderName }}
          </button>
        </div>

        <AppLoadingState v-if="quickView === 'all' && browserLoading" />
        <p v-else-if="quickView === 'all' && browserError" class="dialog-error">{{ browserError }}</p>
        <AppEmptyState
          v-else-if="!visibleBrowserFolders.length && !visibleBrowserFiles.length"
          :title="emptyTitle"
          :description="emptyDescription"
        />

        <div v-else class="file-list cloud-file-list">
          <div
            v-for="folder in visibleBrowserFolders"
            :key="folder.folderId"
            class="file-row folder-row"
            role="button"
            tabindex="0"
            @click="openFolder(folder.folderId)"
            @keydown.enter.prevent="openFolder(folder.folderId)"
          >
            <input v-model="selectedFileRows" type="checkbox" :value="folderSelectionValue(folder)" :aria-label="`选择${folder.folderName}`" @click.stop />
            <span class="file-thumb" aria-hidden="true">
              <img class="file-thumb-img" :src="folderFileIcon" alt="" />
            </span>
            <div>
              <strong>{{ folder.folderName }}</strong>
              <small class="file-meta-line">
                <span>文件夹</span>
                <span>{{ formatDateTime(folder.createdAt) }}</span>
              </small>
            </div>
          </div>

          <div v-for="file in visibleBrowserFiles" :key="file.id" class="file-row">
            <input v-model="selectedFileRows" type="checkbox" :value="fileSelectionValue(file)" :aria-label="`选择${file.name}`" />
            <span class="file-thumb" aria-hidden="true">
              <img class="file-thumb-img" :src="fileIconAssetOf(file)" alt="" />
            </span>
            <div>
              <button class="file-name-button" type="button" :disabled="!file.attachmentRefId" @click="previewBrowserFile(file)">
                {{ file.name }}
              </button>
              <small class="file-meta-line">
                <span>{{ file.type }}</span>
                <span>{{ file.size }}</span>
                <span>{{ file.createdAt }}</span>
                <span>{{ file.status }}</span>
              </small>
            </div>
            <div class="file-actions">
              <button type="button" :disabled="!file.attachmentRefId || im.messageSending" @click="sendSelectedFileToConversation(file)">
                发送
              </button>
              <button type="button" :disabled="!file.attachmentRefId" @click="downloadBrowserFile(file)">
                下载
              </button>
              <details class="file-more-menu">
                <summary>更多</summary>
                <div class="file-more-panel">
                  <button type="button" :disabled="!file.attachmentRefId" @click="toggleFavorite(file)">
                    {{ isFavorite(file) ? '已收藏' : '收藏' }}
                  </button>
                  <button type="button" :disabled="!file.attachmentRefId" @click="openPlacement(file, 'move')">
                    移动
                  </button>
                  <button type="button" :disabled="!file.attachmentRefId" @click="openPlacement(file, 'copy')">
                    复制
                  </button>
                  <button type="button" :disabled="!file.attachmentRefId" @click="trashBrowserFile(file)">
                    删除
                  </button>
                </div>
              </details>
            </div>
          </div>
        </div>
      </template>

      <template v-else>
        <AppEmptyState v-if="!visibleTrashFiles.length" :title="emptyTitle" :description="emptyDescription" />
        <div v-else class="file-list cloud-file-list">
          <div v-for="file in visibleTrashFiles" :key="file.id" class="file-row">
            <input v-model="selectedFileRows" type="checkbox" :value="fileSelectionValue(file)" :aria-label="`选择${file.name}`" />
            <span class="file-thumb" aria-hidden="true">
              <img class="file-thumb-img" :src="fileIconAssetOf(file)" alt="" />
            </span>
            <div>
              <strong>{{ file.name }}</strong>
              <small class="file-meta-line">
                <span>{{ file.type }}</span>
                <span>{{ file.size }}</span>
                <span>{{ file.createdAt }}</span>
                <span>{{ file.status }}</span>
              </small>
            </div>
            <div class="file-actions">
              <button type="button" :disabled="!file.attachmentRefId" @click="restorePersonalFile(file)">
                恢复
              </button>
              <button type="button" :disabled="!file.attachmentRefId" @click="purgePersonalFile(file)">
                彻底删除
              </button>
            </div>
          </div>
        </div>
      </template>
    </section>
  </section>
</div>

<AppDialog v-if="placementTarget">
  <section class="file-placement-dialog">
    <header class="dialog-head dialog-head-row">
      <div>
        <h2>{{ placementTitle }}</h2>
        <p>{{ placementTarget.file.name }}</p>
      </div>
      <button class="field-icon-button" type="button" aria-label="关闭" @click="closePlacement">
        <span aria-hidden="true">×</span>
      </button>
    </header>
    <label class="security-field">
      <span>目标目录</span>
      <select v-model="placementFolderId" class="ui-inline-input">
        <option value="root">根目录</option>
        <option v-for="folder in allFolders" :key="folder.folderId" :value="String(folder.folderId)">
          {{ folderLabel(folder) }}
        </option>
      </select>
    </label>
    <label v-if="placementTarget.mode === 'copy'" class="security-field">
      <span>副本名称</span>
      <input v-model="copyNameDraft" class="ui-inline-input" />
    </label>
    <p v-if="folderActionError" class="dialog-error">{{ folderActionError }}</p>
    <div class="form-actions">
      <AppButton variant="secondary" @click="closePlacement">取消</AppButton>
      <AppButton variant="primary" @click="submitPlacement">确认</AppButton>
    </div>
  </section>
</AppDialog>
</template>

<style scoped>
.cloud-hero {
  position: relative;
  isolation: isolate;
  min-height: 20rem;
  overflow: hidden;
  padding: clamp(var(--space-24), 3vw, var(--space-40)) var(--layout-content-pad);
  border-bottom: 1px solid var(--border-accent);
  background-color: var(--surface-accent);
  background-image:
    linear-gradient(90deg, color-mix(in srgb, var(--surface-panel) 98%, transparent) 0%, color-mix(in srgb, var(--surface-panel) 90%, transparent) 38%, transparent 64%),
    var(--cloud-hero-image);
  background-position: center;
  background-size: cover;
  box-shadow: var(--shadow-md);
}

.cloud-hero-copy {
  display: grid;
  align-content: center;
  justify-items: start;
  gap: var(--space-9);
  width: min(51%, 38rem);
  min-height: 100%;
}

.cloud-hero-eyebrow {
  display: inline-flex;
  align-items: center;
  min-height: 1.85rem;
  padding: 0 var(--space-10);
  border: 1px solid color-mix(in srgb, var(--border-accent) 72%, transparent);
  border-radius: 999px;
  background: var(--surface-glass);
  color: var(--text-accent);
  font-size: var(--text-caption);
  font-weight: 800;
  letter-spacing: .08em;
}

.cloud-hero h1 {
  margin: 0;
  font-family: var(--font-family-display);
  font-size: clamp(var(--text-title), 3vw, var(--text-headline));
  line-height: 1.1;
}

.cloud-hero p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.65;
}

.cloud-hero-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-10);
  margin-top: var(--space-4);
}

.cloud-hero-actions > span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-5);
  min-height: 2.25rem;
  padding: 0 var(--space-10);
  border: 1px solid var(--border-default);
  border-radius: 999px;
  background: var(--surface-glass);
  color: var(--text-secondary);
  font-size: var(--text-caption-md);
}

.cloud-capacity-badge {
  position: absolute;
  z-index: 3;
  right: clamp(var(--space-20), 4vw, var(--space-40));
  bottom: var(--space-20);
  display: grid;
  grid-template-columns: auto auto;
  align-items: baseline;
  column-gap: var(--space-5);
  padding: var(--space-12) var(--space-16);
  border: 1px solid var(--border-accent);
  border-radius: var(--radius-16);
  background: var(--surface-glass);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(.7rem);
}

.cloud-capacity-badge small {
  grid-column: 1 / -1;
  color: var(--text-tertiary);
  font-size: var(--text-caption);
  font-weight: 700;
}

.cloud-capacity-badge strong {
  color: var(--text-accent);
  font-family: var(--font-family-display);
  font-size: var(--text-subtitle-md);
}

.cloud-capacity-badge span {
  color: var(--text-tertiary);
  font-size: var(--text-caption);
}

.cloud-content-stack {
  gap: var(--space-18);
  overflow: auto;
  background-color: var(--surface-canvas);
  background-image: var(--canvas-backdrop);
}

.cloud-quick-grid {
  gap: var(--space-10);
}

.cloud-folder-card {
  position: relative;
  grid-template-columns: 1fr;
  grid-template-rows: minmax(0, 1fr) auto;
  align-items: center;
  align-content: center;
  justify-items: center;
  gap: var(--space-6);
  overflow: hidden;
  padding: var(--space-12);
  border-radius: var(--radius-16);
  background: linear-gradient(145deg, var(--surface-panel), color-mix(in srgb, var(--surface-accent) 48%, var(--surface-panel)));
  text-align: center;
  transition: transform var(--transition-base), border-color var(--transition-base), box-shadow var(--transition-base);
}

.cloud-folder-card:hover,
.cloud-folder-card:focus-visible,
.cloud-folder-card.active {
  transform: translateY(-.18rem);
  box-shadow: var(--shadow-md) !important;
}

.cloud-quick-icon {
  display: grid;
  width: 4.75rem;
  height: 4.75rem;
  place-items: center;
}

.cloud-quick-icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.cloud-folder-card h3 {
  margin: 0;
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.cloud-folder-card p {
  margin-top: var(--space-4);
  font-family: var(--font-family-display);
  font-size: var(--text-subtitle-md);
}

.storage-overview-card {
  border-radius: var(--radius-18);
  background: linear-gradient(145deg, var(--surface-panel), color-mix(in srgb, var(--surface-accent) 34%, var(--surface-panel)));
  box-shadow: var(--shadow-md);
}

.storage-overview-card header {
  justify-content: space-between;
}

.storage-overview-card header em {
  display: inline-flex;
  align-items: center;
  gap: var(--space-5);
  color: var(--text-tertiary);
  font-size: var(--text-caption);
  font-style: normal;
}

.cloud-browser-card {
  border-radius: var(--radius-20);
  background: var(--surface-panel);
  box-shadow: var(--shadow-md);
}

.cloud-browser-title {
  display: grid;
  gap: var(--space-3);
}

.cloud-browser-title small {
  color: var(--text-accent);
  font-size: var(--text-caption);
  font-weight: 800;
  letter-spacing: .08em;
}

.cloud-browser-title strong {
  font-family: var(--font-family-display);
  font-size: var(--text-subtitle-md);
}

@media (max-width: 980px) {
  .cloud-hero {
    min-height: 18rem;
    background-position: 64% center;
  }

  .cloud-hero-copy {
    width: min(65%, 36rem);
  }
}

:global([data-theme='dark']) .cloud-hero {
  background-image:
    linear-gradient(90deg, color-mix(in srgb, var(--surface-panel) 98%, transparent) 0%, color-mix(in srgb, var(--surface-panel) 92%, transparent) 42%, color-mix(in srgb, var(--surface-panel) 24%, transparent) 72%),
    var(--cloud-hero-image);
}
</style>
