import { useMemo } from 'react'
import { Box, Typography, Paper, Grid, Chip } from '@mui/material'
import { alpha } from '@mui/material/styles'
import { useTranslation } from 'react-i18next'
import { CQL_SHOWCASE_EXAMPLE } from '../../constants/cqlExamples'
import { CARD_RADIUS } from '../../constants/layout'

export default function CqlShowcase() {
  const { t } = useTranslation('landing')

  const highlightedLines = useMemo(
    () => CQL_SHOWCASE_EXAMPLE.split('\n').map((line, i) => ({ key: i, line, highlighted: highlightLine(line) })),
    []
  )

  return (
    <Box
      sx={(theme) => ({
        py: 8,
        px: 3,
        bgcolor: alpha(theme.palette.primary.main, theme.palette.mode === 'dark' ? 0.08 : 0.03),
      })}
    >
      <Typography
        variant="h4"
        gutterBottom
        sx={{
          fontWeight: 700,
          textAlign: "center",
          color: "secondary.main"
        }}>
        {t('showcase.title')}
      </Typography>
      <Typography
        variant="body1"
        sx={{
          textAlign: "center",
          color: "text.secondary",
          mb: 5,
          maxWidth: 700,
          mx: 'auto'
        }}>
        {t('showcase.subtitle')}
      </Typography>
      <Grid container spacing={4} sx={{ maxWidth: 1100, mx: 'auto' }}>
        {/* CQL Code */}
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper
            elevation={0}
            sx={{
              borderRadius: CARD_RADIUS,
              overflow: 'hidden',
              border: '1px solid',
              borderColor: 'divider',
            }}
          >
            <Box
              sx={{
                px: 2,
                py: 1,
                bgcolor: 'secondary.main',
                display: 'flex',
                alignItems: 'center',
                gap: 1,
              }}
            >
              <Box sx={{ display: 'flex', gap: 0.75 }}>
                <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#FF5F57' }} />
                <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#FEBC2E' }} />
                <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#28C840' }} />
              </Box>
              <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.7)', ml: 1 }}>
                TWCoreDiabetesCare.cql
              </Typography>
            </Box>
            <Box
              sx={{
                p: 2,
                bgcolor: '#1E1E2E',
                fontFamily: '"Fira Code", "Cascadia Code", "JetBrains Mono", monospace',
                fontSize: '0.78rem',
                lineHeight: 1.6,
                color: '#D4D4D4',
                whiteSpace: 'pre',
                overflow: 'auto',
                maxHeight: 440,
              }}
            >
              {highlightedLines.map(({ key, highlighted }) => (
                <Box key={key} sx={{ display: 'flex' }}>
                  <Box
                    component="span"
                    sx={{
                      width: 36,
                      flexShrink: 0,
                      textAlign: 'right',
                      pr: 1.5,
                      color: 'rgba(255,255,255,0.25)',
                      userSelect: 'none',
                    }}
                  >
                    {key + 1}
                  </Box>
                  <Box component="span">
                    {highlighted}
                  </Box>
                </Box>
              ))}
            </Box>
          </Paper>
        </Grid>

        {/* Explanation */}
        <Grid size={{ xs: 12, md: 5 }}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, height: '100%' }}>
            <Paper elevation={0} sx={{ p: 3, borderRadius: CARD_RADIUS, border: '1px solid', borderColor: 'divider' }}>
              <Typography
                variant="subtitle1"
                gutterBottom
                sx={{
                  fontWeight: 700,
                  color: "primary.main"
                }}>
                {t('showcase.whatIsCql')}
              </Typography>
              <Typography
                variant="body2"
                sx={{
                  color: "text.secondary",
                  lineHeight: 1.8
                }}>
                {t('showcase.whatIsCqlContent')}
              </Typography>
            </Paper>
            <Paper elevation={0} sx={{ p: 3, borderRadius: CARD_RADIUS, border: '1px solid', borderColor: 'divider' }}>
              <Typography
                variant="subtitle1"
                gutterBottom
                sx={{
                  fontWeight: 700,
                  color: "primary.main"
                }}>
                {t('showcase.whyTwcore')}
              </Typography>
              <Typography
                variant="body2"
                sx={{
                  color: "text.secondary",
                  lineHeight: 1.8
                }}>
                {t('showcase.whyTwcoreContent')}
              </Typography>
            </Paper>
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              {[
                { label: '27+ ' + t('stats.resources'), color: '#0D7377' },
                { label: '13 ' + t('stats.profiles'), color: '#1B3A5C' },
                { label: '7 ' + t('stats.codeSystems'), color: '#14A3A8' },
                { label: '9 ' + t('stats.templates'), color: '#E8A838' },
              ].map((stat) => (
                <Chip
                  key={stat.label}
                  label={stat.label}
                  size="small"
                  sx={{
                    bgcolor: `${stat.color}15`,
                    color: stat.color,
                    fontWeight: 600,
                  }}
                />
              ))}
            </Box>
          </Box>
        </Grid>
      </Grid>
    </Box>
  );
}

/** Simple syntax highlighting for CQL showcase (no Monaco dependency) */
function highlightLine(line: string): React.ReactNode {
  if (!line.trim()) return '\n'

  // Comments
  if (line.trimStart().startsWith('//')) {
    return <span style={{ color: '#6A9955' }}>{line}</span>
  }

  // Keywords
  const keywords = /\b(library|using|include|codesystem|valueset|code|parameter|context|define|from|where|and|or|not|exists|during|is|display|version|Interval|Last|sort|by|return|ToConcept)\b/g
  const types = /\b(FHIR|FHIRHelpers|Patient|Condition|Observation|MedicationRequest|Encounter|DateTime|Interval)\b/g
  const strings = /'[^']*'/g
  const numbers = /\b\d+(\.\d+)?\b/g

  const parts: { start: number; end: number; color: string }[] = []

  let m: RegExpExecArray | null
  while ((m = keywords.exec(line)) !== null) {
    parts.push({ start: m.index, end: m.index + m[0].length, color: '#C586C0' })
  }
  while ((m = types.exec(line)) !== null) {
    if (!parts.some((p) => p.start <= m!.index && p.end >= m!.index + m![0].length)) {
      parts.push({ start: m.index, end: m.index + m[0].length, color: '#4EC9B0' })
    }
  }
  while ((m = strings.exec(line)) !== null) {
    parts.push({ start: m.index, end: m.index + m[0].length, color: '#CE9178' })
  }
  while ((m = numbers.exec(line)) !== null) {
    if (!parts.some((p) => p.start <= m!.index && p.end >= m!.index + m![0].length)) {
      parts.push({ start: m.index, end: m.index + m[0].length, color: '#B5CEA8' })
    }
  }

  if (parts.length === 0) return line

  parts.sort((a, b) => a.start - b.start)

  // Remove overlapping parts - keep the first one
  const filtered: typeof parts = []
  for (const p of parts) {
    if (filtered.length === 0 || p.start >= filtered[filtered.length - 1].end) {
      filtered.push(p)
    }
  }

  const result: React.ReactNode[] = []
  let pos = 0
  for (const p of filtered) {
    if (p.start > pos) {
      result.push(line.slice(pos, p.start))
    }
    result.push(
      <span key={p.start} style={{ color: p.color }}>
        {line.slice(p.start, p.end)}
      </span>
    )
    pos = p.end
  }
  if (pos < line.length) {
    result.push(line.slice(pos))
  }

  return result
}
