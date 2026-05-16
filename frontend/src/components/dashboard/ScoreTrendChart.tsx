import { useMemo, useState } from 'react'
import {
  Box,
  Typography,
  Paper,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Chip,
  Divider,
} from '@mui/material'
import ViewModuleIcon from '@mui/icons-material/ViewModule'
import TimelineIcon from '@mui/icons-material/Timeline'
import { useTheme, type Theme } from '@mui/material/styles'
import { CHART_HEIGHT } from '../../constants/layout'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  // ResponsiveContainer removed: replaced by SafeResponsiveContainer wrapper
  ReferenceLine,
} from 'recharts'
import SafeResponsiveContainer from '../common/SafeResponsiveContainer'
import { useTranslation } from 'react-i18next'
import type { TrendSeriesPoint, MeasureThreshold } from '../../types'
import { CHART_COLORS } from '../../constants/chartColors'
import { classifyScoring, type ScoringFamily } from './scoringFamily'

type ChartMode = 'multiples' | 'overlay'

interface ScoreTrendChartProps {
  data: TrendSeriesPoint[]
  thresholds?: MeasureThreshold[]
  title?: string
}

interface ChartDataPoint {
  period: string
  [measureName: string]: string | number | null
}

const SMALL_CHART_HEIGHT = 160

function buildChartRows(points: TrendSeriesPoint[]): {
  rows: ChartDataPoint[]
  measureNames: string[]
} {
  const names = [...new Set(points.map((d) => d.measureName))]
  const periods = [...new Set(points.map((d) => d.period))]
  const rows: ChartDataPoint[] = periods.map((period) => {
    const point: ChartDataPoint = { period }
    for (const name of names) {
      const match = points.find((d) => d.period === period && d.measureName === name)
      point[name] = match?.score ?? null
    }
    return point
  })
  return { rows, measureNames: names }
}

export default function ScoreTrendChart({ data, thresholds = [], title }: ScoreTrendChartProps) {
  const { t } = useTranslation('measures')
  const theme = useTheme()
  const [mode, setMode] = useState<ChartMode>('multiples')

  const grouped = useMemo(() => {
    const buckets: Record<ScoringFamily, TrendSeriesPoint[]> = {
      proportion: [],
      continuousVariable: [],
      cohort: [],
    }
    for (const p of data) {
      buckets[classifyScoring(p.scoringType)].push(p)
    }
    return buckets
  }, [data])

  const proportionMeasureNames = useMemo(
    () => [...new Set(grouped.proportion.map((d) => d.measureName))],
    [grouped.proportion],
  )

  // PAT-151: memoize to match the rest of the derived state in this file. The
  // filter recreates the array on every render otherwise, which propagates new
  // identities into ProportionSection's ReferenceLine map and forces recharts
  // to rebuild those nodes unnecessarily.
  const targetLines = useMemo(
    () => thresholds.filter((th) => th.thresholdType === 'target'),
    [thresholds],
  )
  const warningLines = useMemo(
    () => thresholds.filter((th) => th.thresholdType === 'warning'),
    [thresholds],
  )

  const handleModeChange = (_: React.MouseEvent<HTMLElement>, next: ChartMode | null) => {
    if (next) setMode(next)
  }

  const hasProportion = grouped.proportion.length > 0
  const hasContinuous = grouped.continuousVariable.length > 0
  const hasCohort = grouped.cohort.length > 0
  const showModeToggle = hasProportion && proportionMeasureNames.length > 1

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
        <Typography variant="subtitle2">{title || t('dashboard.scoreTrend')}</Typography>
        {showModeToggle && (
          <ToggleButtonGroup
            size="small"
            value={mode}
            exclusive
            onChange={handleModeChange}
            aria-label={t('dashboard.chartMode.label')}
          >
            <ToggleButton value="multiples" aria-label={t('dashboard.chartMode.multiples')}>
              <ViewModuleIcon fontSize="small" sx={{ mr: 0.5 }} />
              {t('dashboard.chartMode.multiples')}
            </ToggleButton>
            <ToggleButton value="overlay" aria-label={t('dashboard.chartMode.overlay')}>
              <TimelineIcon fontSize="small" sx={{ mr: 0.5 }} />
              {t('dashboard.chartMode.overlay')}
            </ToggleButton>
          </ToggleButtonGroup>
        )}
      </Stack>

      {hasProportion && (
        <ProportionSection
          points={grouped.proportion}
          mode={mode}
          targetLines={targetLines}
          warningLines={warningLines}
          theme={theme}
          t={t}
          colorFor={(name) => CHART_COLORS[proportionMeasureNames.indexOf(name) % CHART_COLORS.length]}
        />
      )}

      {hasContinuous && (
        <>
          {hasProportion && <Divider sx={{ my: 2 }} />}
          <PerMeasureSection
            family="continuousVariable"
            points={grouped.continuousVariable}
            t={t}
          />
        </>
      )}

      {hasCohort && (
        <>
          {(hasProportion || hasContinuous) && <Divider sx={{ my: 2 }} />}
          <PerMeasureSection family="cohort" points={grouped.cohort} t={t} />
        </>
      )}
    </Paper>
  )
}

interface ProportionSectionProps {
  points: TrendSeriesPoint[]
  mode: ChartMode
  targetLines: MeasureThreshold[]
  warningLines: MeasureThreshold[]
  theme: Theme
  t: (key: string) => string
  colorFor: (name: string) => string
}

function ProportionSection({ points, mode, targetLines, warningLines, theme, t, colorFor }: ProportionSectionProps) {
  const { rows, measureNames } = useMemo(() => buildChartRows(points), [points])
  const [selected, setSelected] = useState<Set<string>>(() => new Set(measureNames))

  const effectiveSelected = useMemo(() => {
    const filtered = measureNames.filter((n) => selected.has(n))
    return filtered.length > 0 ? filtered : measureNames
  }, [measureNames, selected])

  const toggleMeasure = (name: string) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(name)) next.delete(name)
      else next.add(name)
      return next
    })
  }

  return (
    <Box>
      <Stack direction="row" alignItems="baseline" spacing={1} mb={0.5}>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {t('dashboard.trendSections.proportion')}
        </Typography>
      </Stack>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
        {t('dashboard.trendSections.proportionHint')}
      </Typography>

      {mode === 'multiples' ? (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(3, 1fr)' },
            gap: 2,
          }}
        >
          {measureNames.map((name) => (
            <Box
              key={name}
              sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 1 }}
            >
              <Typography
                variant="caption"
                sx={{ fontWeight: 600, color: colorFor(name), display: 'block', mb: 0.5 }}
                noWrap
                title={name}
              >
                {name}
              </Typography>
              <Box sx={{ width: '100%', height: SMALL_CHART_HEIGHT }}>
                <SafeResponsiveContainer>
                  <LineChart data={rows} margin={{ top: 4, right: 8, bottom: 4, left: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="period" fontSize={10} tick={{ fontSize: 10 }} />
                    <YAxis
                      domain={[0, 100]}
                      fontSize={10}
                      tick={{ fontSize: 10 }}
                      width={32}
                      tickFormatter={(v: number) => `${v}%`}
                    />
                    <Tooltip formatter={(value) => (typeof value === 'number' ? `${value.toFixed(1)}%` : '')} />
                    <Line
                      type="monotone"
                      dataKey={name}
                      stroke={colorFor(name)}
                      strokeWidth={2}
                      dot={{ r: 2 }}
                      connectNulls
                      isAnimationActive={false}
                    />
                    {targetLines.map((th, i) => (
                      <ReferenceLine
                        key={`t-${i}`}
                        y={th.thresholdValue}
                        stroke={theme.palette.success.light}
                        strokeDasharray="5 5"
                      />
                    ))}
                    {warningLines.map((th, i) => (
                      <ReferenceLine
                        key={`w-${i}`}
                        y={th.thresholdValue}
                        stroke={theme.palette.warning.light}
                        strokeDasharray="5 5"
                      />
                    ))}
                  </LineChart>
                </SafeResponsiveContainer>
              </Box>
            </Box>
          ))}
        </Box>
      ) : (
        <>
          {measureNames.length > 1 && (
            <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', gap: 0.5, mb: 1 }}>
              {measureNames.map((name) => {
                const active = selected.has(name)
                return (
                  <Chip
                    key={name}
                    label={name}
                    size="small"
                    clickable
                    onClick={() => toggleMeasure(name)}
                    variant={active ? 'filled' : 'outlined'}
                    sx={{
                      borderColor: colorFor(name),
                      color: active ? '#fff' : colorFor(name),
                      backgroundColor: active ? colorFor(name) : 'transparent',
                      '&:hover': {
                        backgroundColor: active ? colorFor(name) : `${colorFor(name)}22`,
                      },
                    }}
                  />
                )
              })}
            </Stack>
          )}
          <Box sx={{ width: '100%', height: CHART_HEIGHT }}>
            <SafeResponsiveContainer>
              <LineChart data={rows} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="period" fontSize={11} />
                <YAxis domain={[0, 100]} fontSize={11} tickFormatter={(v: number) => `${v}%`} />
                <Tooltip formatter={(value) => (typeof value === 'number' ? `${value.toFixed(1)}%` : '')} />
                <Legend />
                {effectiveSelected.map((name) => (
                  <Line
                    key={name}
                    type="monotone"
                    dataKey={name}
                    stroke={colorFor(name)}
                    strokeWidth={2}
                    dot={{ r: 3 }}
                    connectNulls
                  />
                ))}
                {targetLines.map((th, i) => (
                  <ReferenceLine
                    key={`target-${i}`}
                    y={th.thresholdValue}
                    stroke={theme.palette.success.light}
                    strokeDasharray="5 5"
                    label={{
                      value: t('dashboard.target'),
                      fill: theme.palette.success.light,
                      fontSize: 10,
                    }}
                  />
                ))}
                {warningLines.map((th, i) => (
                  <ReferenceLine
                    key={`warning-${i}`}
                    y={th.thresholdValue}
                    stroke={theme.palette.warning.light}
                    strokeDasharray="5 5"
                  />
                ))}
              </LineChart>
            </SafeResponsiveContainer>
          </Box>
        </>
      )}
    </Box>
  )
}

interface PerMeasureSectionProps {
  family: 'continuousVariable' | 'cohort'
  points: TrendSeriesPoint[]
  t: (key: string, opts?: Record<string, unknown>) => string
}

/**
 * Renders one independent chart per measure with auto-scaled Y-axis. Used for
 * continuous-variable (each measure has its own clinical range and unit) and
 * cohort (raw patient counts). Overlay mode is intentionally not offered here
 * because mixing different units / counts on one axis is meaningless.
 */
function PerMeasureSection({ family, points, t }: PerMeasureSectionProps) {
  const measureNames = useMemo(
    () => [...new Set(points.map((d) => d.measureName))],
    [points],
  )

  const unitByMeasure = useMemo(() => {
    const map: Record<string, string | undefined> = {}
    for (const p of points) {
      if (p.unit && !map[p.measureName]) map[p.measureName] = p.unit
    }
    return map
  }, [points])

  return (
    <Box>
      <Stack direction="row" alignItems="baseline" spacing={1} mb={0.5}>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {t(`dashboard.trendSections.${family}`)}
        </Typography>
      </Stack>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
        {t(`dashboard.trendSections.${family}Hint`)}
      </Typography>

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(3, 1fr)' },
          gap: 2,
        }}
      >
        {measureNames.map((name, idx) => {
          const measurePoints = points.filter((p) => p.measureName === name)
          const rows: ChartDataPoint[] = measurePoints.map((p) => ({
            period: p.period,
            [name]: p.score ?? null,
          }))
          const unit =
            family === 'cohort'
              ? t('dashboard.trendSections.unknownUnit')
              : unitByMeasure[name] || t('dashboard.trendSections.unknownUnit')
          const color = CHART_COLORS[idx % CHART_COLORS.length]
          return (
            <Box
              key={name}
              sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 1 }}
            >
              <Stack direction="row" justifyContent="space-between" alignItems="baseline" mb={0.5}>
                <Typography
                  variant="caption"
                  sx={{ fontWeight: 600, color, flexShrink: 1, minWidth: 0 }}
                  noWrap
                  title={name}
                >
                  {name}
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0, ml: 1 }}>
                  {unit}
                </Typography>
              </Stack>
              <Box sx={{ width: '100%', height: SMALL_CHART_HEIGHT }}>
                <SafeResponsiveContainer>
                  <LineChart data={rows} margin={{ top: 4, right: 8, bottom: 4, left: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="period" fontSize={10} tick={{ fontSize: 10 }} />
                    <YAxis
                      fontSize={10}
                      tick={{ fontSize: 10 }}
                      width={42}
                      domain={['auto', 'auto']}
                      tickFormatter={(v: number) =>
                        family === 'cohort' ? Math.round(v).toString() : formatRawValue(v)
                      }
                    />
                    <Tooltip
                      formatter={(value) => {
                        if (typeof value !== 'number') return ''
                        return family === 'cohort'
                          ? `${Math.round(value)} ${unit}`
                          : `${formatRawValue(value)} ${unit}`
                      }}
                    />
                    <Line
                      type="monotone"
                      dataKey={name}
                      stroke={color}
                      strokeWidth={2}
                      dot={{ r: 2 }}
                      connectNulls
                      isAnimationActive={false}
                    />
                  </LineChart>
                </SafeResponsiveContainer>
              </Box>
            </Box>
          )
        })}
      </Box>
    </Box>
  )
}

function formatRawValue(v: number): string {
  if (Math.abs(v) >= 100) return v.toFixed(0)
  if (Math.abs(v) >= 10) return v.toFixed(1)
  return v.toFixed(2)
}
