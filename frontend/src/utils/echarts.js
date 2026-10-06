// ECharts 按需注册（只打包用到的图表与组件）
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer
])

// 站点暗色玻璃风格：文字 / 网格 / 提示框统一在这里配，各图复用
export const CHART_TEXT = '#8b8b99'
export const CHART_AXIS = 'rgba(255,255,255,0.10)'

export const tooltipStyle = {
  backgroundColor: 'rgba(14,14,22,0.92)',
  borderColor: 'rgba(255,255,255,0.14)',
  borderWidth: 1,
  padding: [10, 12],
  textStyle: { color: '#e6e6eb', fontSize: 12 },
  extraCssText: 'backdrop-filter: blur(8px); border-radius: 10px;'
}

export const legendStyle = {
  textStyle: { color: CHART_TEXT, fontSize: 12 },
  itemWidth: 10,
  itemHeight: 10,
  icon: 'circle',
  top: 4
}

export const palette = [
  '#7c5cff',
  '#22d3ee',
  '#f472b6',
  '#34d399',
  '#fbbf24',
  '#60a5fa',
  '#fb7185',
  '#a3e635',
  '#c084fc',
  '#2dd4bf'
]

export default echarts
