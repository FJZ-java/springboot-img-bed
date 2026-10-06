<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="gradient-text">数据统计</h2>
        <p class="desc">图床核心指标与可视化分析 · 仅管理员可见</p>
      </div>
      <div class="head-actions">
        <n-radio-group v-model:value="days" size="small">
          <n-radio-button :label="'近 7 天'" :value="7" />
          <n-radio-button :label="'近 14 天'" :value="14" />
          <n-radio-button :label="'近 30 天'" :value="30" />
        </n-radio-group>
        <n-button secondary :loading="loading" @click="load">🔄 刷新</n-button>
      </div>
    </div>

    <!-- ===== 上半部分：核心数据 ===== -->
    <div class="stat-row">
      <div class="glass-card stat-card">
        <div class="stat-icon" style="--c1: #7c5cff; --c2: #22d3ee">🖼️</div>
        <div class="stat-body">
          <div class="stat-label">图片总数</div>
          <div class="stat-value">{{ ov.images }}</div>
          <div class="stat-sub">今日新增 +{{ ov.todayUploads }}</div>
        </div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-icon" style="--c1: #22d3ee; --c2: #4ade80">💾</div>
        <div class="stat-body">
          <div class="stat-label">占用空间</div>
          <div class="stat-value cyan">{{ fmtSize(ov.totalSize) }}</div>
          <div class="stat-sub">平均每张 {{ fmtSize(ov.avgSize) }}</div>
        </div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-icon" style="--c1: #38bdf8; --c2: #6366f1">👥</div>
        <div class="stat-body">
          <div class="stat-label">注册用户</div>
          <div class="stat-value ok">{{ ov.users }}</div>
          <div class="stat-sub">其中管理员 {{ ov.admins }} 位</div>
        </div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-icon" style="--c1: #f87171; --c2: #fb923c">🚫</div>
        <div class="stat-body">
          <div class="stat-label">已封禁账号</div>
          <div class="stat-value danger">{{ ov.banned }}</div>
          <div class="stat-sub">正常状态 {{ ov.users - ov.banned }} 位</div>
        </div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-icon" style="--c1: #c084fc; --c2: #f472b6">🔗</div>
        <div class="stat-body">
          <div class="stat-label">有效分享链接</div>
          <div class="stat-value purple">{{ ov.shareLinks }}</div>
          <div class="stat-sub">累计浏览量 {{ ov.shareViews }}</div>
        </div>
      </div>
      <div class="glass-card stat-card">
        <div class="stat-icon" style="--c1: #fbbf24; --c2: #fb7185">📊</div>
        <div class="stat-body">
          <div class="stat-label">近 7 天上传</div>
          <div class="stat-value orange">{{ ov.weekUploads }}</div>
          <div class="stat-sub">日均 {{ avgWeek }} 张</div>
        </div>
      </div>
    </div>

    <!-- ===== 下半部分：可视化图表 ===== -->
    <div class="glass-card chart-card">
      <div class="card-head">
        <span class="card-title">📈 上传趋势</span>
        <span class="card-sub">近 {{ days }} 天每日上传量与占用体积</span>
      </div>
      <div class="chart-wrap">
        <div ref="trendRef" class="chart" style="height: 290px"></div>
        <div v-if="!trend.length" class="chart-empty">暂无上传数据</div>
      </div>
    </div>

    <div class="chart-grid">
      <div class="glass-card chart-card">
        <div class="card-head">
          <span class="card-title">🗄️ 仓库容量分布</span>
          <span class="card-sub">按占用体积</span>
        </div>
        <div class="chart-wrap">
          <div ref="reposRef" class="chart" style="height: 240px"></div>
          <div v-if="!repos.length" class="chart-empty">暂无数据</div>
        </div>
      </div>

      <div class="glass-card chart-card">
        <div class="card-head">
          <span class="card-title">🏆 用户贡献排行</span>
          <span class="card-sub">上传量 TOP{{ topUsers.length || 8 }}</span>
        </div>
        <div class="chart-wrap">
          <div ref="usersRef" class="chart" style="height: 240px"></div>
          <div v-if="!topUsers.length" class="chart-empty">暂无数据</div>
        </div>
      </div>

      <div class="glass-card chart-card">
        <div class="card-head">
          <span class="card-title">🛡️ 账号状态</span>
          <span class="card-sub">正常 / 封禁</span>
        </div>
        <div class="chart-wrap">
          <div ref="statusRef" class="chart" style="height: 240px"></div>
          <div v-if="!ov.users" class="chart-empty">暂无数据</div>
        </div>
      </div>

      <div class="glass-card chart-card">
        <div class="card-head">
          <span class="card-title">🎨 图片格式分布</span>
          <span class="card-sub">按 content-type</span>
        </div>
        <div class="chart-wrap">
          <div ref="formatsRef" class="chart" style="height: 240px"></div>
          <div v-if="!formats.length" class="chart-empty">暂无数据</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import api from '../api'
import echarts, {
  CHART_AXIS,
  CHART_TEXT,
  legendStyle,
  palette,
  tooltipStyle
} from '../utils/echarts'

const message = useMessage()

const loading = ref(false)
const days = ref(30)
const ov = ref(emptyOverview())
const trend = ref([])
const repos = ref([])
const topUsers = ref([])
const accountStatus = ref([])
const formats = ref([])

const trendRef = ref(null)
const reposRef = ref(null)
const usersRef = ref(null)
const statusRef = ref(null)
const formatsRef = ref(null)

const charts = {}
const observers = []

const avgWeek = computed(() => (Number(ov.value.weekUploads) / 7).toFixed(1))

function emptyOverview() {
  return {
    images: 0,
    totalSize: 0,
    avgSize: 0,
    users: 0,
    admins: 0,
    banned: 0,
    shareLinks: 0,
    shareViews: 0,
    todayUploads: 0,
    weekUploads: 0
  }
}

function fmtSize(bytes) {
  const b = Number(bytes) || 0
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  if (b < 1024 * 1024 * 1024) return (b / 1024 / 1024).toFixed(2) + ' MB'
  return (b / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/admin/stats', { params: { days: days.value } })
    const d = res.data || {}
    ov.value = Object.assign(emptyOverview(), d.overview || {})
    trend.value = d.trend || []
    repos.value = d.repos || []
    topUsers.value = d.topUsers || []
    accountStatus.value = d.accountStatus || []
    formats.value = d.formats || []
    renderAll()
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

/* ---------- 图表实例管理 ---------- */
function ensure(key, el) {
  if (!el) return null
  if (!charts[key]) {
    charts[key] = echarts.init(el, null, { renderer: 'canvas' })
    const ro = new ResizeObserver(() => charts[key] && charts[key].resize())
    ro.observe(el)
    observers.push(ro)
  }
  return charts[key]
}

const axisBase = {
  axisLine: { lineStyle: { color: CHART_AXIS } },
  axisTick: { show: false },
  axisLabel: { color: CHART_TEXT, fontSize: 11 }
}
const splitBase = { lineStyle: { color: 'rgba(255,255,255,0.06)', type: 'dashed' } }

function chartTrend() {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(124,92,255,0.08)' } },
      ...tooltipStyle
    },
    legend: { data: ['上传量', '占用体积'], ...legendStyle, right: 0, top: 0 },
    grid: { left: 6, right: 6, top: 38, bottom: 2, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: trend.value.map(t => t.date), ...axisBase, axisLabel: { ...axisBase.axisLabel, formatter: v => v.slice(5) } },
    yAxis: [
      { type: 'value', name: '张', nameTextStyle: { color: CHART_TEXT, fontSize: 11 }, ...axisBase, splitLine: splitBase },
      { type: 'value', name: 'KB', nameTextStyle: { color: CHART_TEXT, fontSize: 11 }, ...axisBase, splitLine: { show: false } }
    ],
    series: [
      {
        name: '占用体积',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 14,
        data: trend.value.map(t => +(t.size / 1024).toFixed(1)),
        itemStyle: { color: 'rgba(124,92,255,0.42)', borderRadius: [4, 4, 0, 0] }
      },
      {
        name: '上传量',
        type: 'line',
        yAxisIndex: 0,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: trend.value.map(t => t.count),
        lineStyle: { width: 2.6, color: '#22d3ee' },
        itemStyle: { color: '#22d3ee', borderColor: '#0a0a0f', borderWidth: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(34,211,238,0.42)' },
              { offset: 1, color: 'rgba(34,211,238,0)' }
            ]
          }
        }
      }
    ]
  }
}

function chartRepos() {
  const data = repos.value.map((r, i) => ({
    name: r.name,
    value: r.size,
    count: r.count,
    itemStyle: { color: palette[i % palette.length] }
  }))
  return {
    tooltip: {
      trigger: 'item',
      ...tooltipStyle,
      formatter: p => `${p.name}<br/>${fmtSize(p.value)} · ${p.data.count} 张（${p.percent}%）`
    },
    legend: { type: 'scroll', orient: 'vertical', right: 4, top: 'center', ...legendStyle, itemGap: 9, textStyle: { color: '#e6e6eb', fontSize: 11.5 } },
    series: [
      {
        type: 'pie',
        radius: ['50%', '74%'],
        center: ['36%', '52%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: 'rgba(10,10,15,0.9)', borderWidth: 2, borderRadius: 6 },
        label: { show: false },
        emphasis: {
          scaleSize: 8,
          label: { show: true, color: '#e6e6eb', fontSize: 12, formatter: p => `${p.name}\n${p.percent}%` }
        },
        data
      }
    ]
  }
}

function chartUsers() {
  const list = [...topUsers.value].reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle },
    grid: { left: 6, right: 34, top: 10, bottom: 2, containLabel: true },
    xAxis: { type: 'value', ...axisBase, splitLine: splitBase },
    yAxis: { type: 'category', data: list.map(u => u.name), ...axisBase },
    series: [
      {
        type: 'bar',
        barMaxWidth: 15,
        data: list.map(u => u.count),
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: {
            type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
            colorStops: [
              { offset: 0, color: 'rgba(124,92,255,0.55)' },
              { offset: 1, color: '#22d3ee' }
            ]
          }
        },
        label: { show: true, position: 'right', color: CHART_TEXT, fontSize: 11 }
      }
    ]
  }
}

function chartStatus() {
  const total = ov.value.users
  const data = accountStatus.value.map(s => ({
    name: s.name,
    value: s.value,
    itemStyle: { color: s.name === '正常' ? '#34d399' : '#f87171' }
  }))
  return {
    tooltip: { trigger: 'item', ...tooltipStyle, formatter: p => `${p.name}：${p.value} 位（${p.percent}%）` },
    title: {
      text: String(total),
      subtext: '账号总数',
      left: 'center',
      top: '38%',
      textStyle: { color: '#e6e6eb', fontSize: 24, fontWeight: 700 },
      subtextStyle: { color: CHART_TEXT, fontSize: 11 }
    },
    series: [
      {
        type: 'pie',
        radius: ['56%', '78%'],
        center: ['50%', '54%'],
        label: { show: false },
        itemStyle: { borderColor: 'rgba(10,10,15,0.9)', borderWidth: 2 },
        emphasis: { scaleSize: 8 },
        data
      }
    ]
  }
}

function chartFormats() {
  const data = formats.value.map((f, i) => ({ name: f.name, value: f.value, itemStyle: { color: palette[i % palette.length] } }))
  return {
    tooltip: { trigger: 'item', ...tooltipStyle, formatter: p => `${p.name}：${p.value} 张（${p.percent}%）` },
    legend: { type: 'scroll', orient: 'horizontal', bottom: 0, ...legendStyle, itemGap: 12 },
    series: [
      {
        type: 'pie',
        radius: ['48%', '70%'],
        center: ['50%', '46%'],
        label: { show: false },
        itemStyle: { borderColor: 'rgba(10,10,15,0.9)', borderWidth: 2, borderRadius: 4 },
        emphasis: { scaleSize: 8, label: { show: true, color: '#e6e6eb', fontSize: 12, formatter: '{b}\n{c} 张' } },
        data
      }
    ]
  }
}

function renderAll() {
  const map = [
    ['trend', trendRef.value, chartTrend()],
    ['repos', reposRef.value, chartRepos()],
    ['users', usersRef.value, chartUsers()],
    ['status', statusRef.value, chartStatus()],
    ['formats', formatsRef.value, chartFormats()]
  ]
  map.forEach(([key, el, option]) => {
    const c = ensure(key, el)
    if (c && option) c.setOption(option, true)
  })
}

function disposeAll() {
  Object.values(charts).forEach(c => c && c.dispose())
  Object.keys(charts).forEach(k => delete charts[k])
  observers.forEach(o => o.disconnect())
  observers.length = 0
}

watch(days, load)
onMounted(load)
onBeforeUnmount(disposeAll)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 12px; flex-wrap: wrap; }
.page-head h2 { margin: 0 0 6px; font-size: 26px; }
.desc { color: var(--text-dim); font-size: 13px; margin: 0; }
.head-actions { display: flex; gap: 8px; flex-shrink: 0; align-items: center; }

/* ===== 核心数据卡 ===== */
.stat-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(178px, 1fr)); gap: 12px; }
.stat-card { padding: 14px 16px; display: flex; align-items: center; gap: 12px; transition: transform 0.2s, border-color 0.2s; }
.stat-card:hover { transform: translateY(-2px); border-color: rgba(124, 92, 255, 0.45); }
.stat-icon {
  width: 40px; height: 40px; flex-shrink: 0;
  display: grid; place-items: center; font-size: 19px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--c1, #7c5cff), var(--c2, #22d3ee));
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.35);
}
.stat-body { min-width: 0; flex: 1; }
.stat-label { font-size: 12px; color: var(--text-dim); margin-bottom: 3px; }
.stat-value {
  font-size: 24px; font-weight: 700; line-height: 1.15; font-variant-numeric: tabular-nums;
  background: linear-gradient(120deg, #ff7a45, #ffb26b);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
}
.stat-value.cyan { background: linear-gradient(120deg, #22d3ee, #7dd3fc); -webkit-background-clip: text; background-clip: text; }
.stat-value.ok { background: linear-gradient(120deg, #34d399, #6ee7b7); -webkit-background-clip: text; background-clip: text; }
.stat-value.danger { background: linear-gradient(120deg, #f87171, #fca5a5); -webkit-background-clip: text; background-clip: text; }
.stat-value.orange { background: linear-gradient(120deg, #fbbf24, #fb923c); -webkit-background-clip: text; background-clip: text; }
.stat-value.purple { background: linear-gradient(120deg, #c084fc, #f472b6); -webkit-background-clip: text; background-clip: text; }
.stat-sub { font-size: 11px; color: var(--text-dim); margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* ===== 图表卡 ===== */
.chart-card { padding: 14px 16px 6px; }
.card-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 4px; }
.card-title { font-size: 14px; font-weight: 600; }
.card-sub { font-size: 11.5px; color: var(--text-dim); }
.chart-wrap { position: relative; }
.chart { width: 100%; }
.chart-empty {
  position: absolute; inset: 0;
  display: grid; place-items: center;
  font-size: 13px; color: var(--text-dim);
}
.chart-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }

@media (max-width: 860px) {
  .chart-grid { grid-template-columns: minmax(0, 1fr); }
  .head-actions { flex-wrap: wrap; }
}
</style>
