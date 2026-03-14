import { useState, useCallback, useMemo } from 'react'
import { Box, Typography, Paper, Button, Alert, Grid, Menu, MenuItem } from '@mui/material'
import {
  PlayArrow as TranslateIcon,
  ExpandMore as DropdownIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useSelector } from 'react-redux'
import type { RootState } from '../../store'
import {
  CQL_PLAYGROUND_STARTER,
  CQL_EXAMPLE_DIABETES,
  CQL_EXAMPLE_HYPERTENSION,
  CQL_EXAMPLE_MEDICATION,
  CQL_EXAMPLE_ENCOUNTER,
} from '../../constants/cqlExamples'

const TEMPLATES = [
  { key: 'starter', code: CQL_PLAYGROUND_STARTER },
  { key: 'diabetes', code: CQL_EXAMPLE_DIABETES },
  { key: 'hypertension', code: CQL_EXAMPLE_HYPERTENSION },
  { key: 'medication', code: CQL_EXAMPLE_MEDICATION },
  { key: 'encounter', code: CQL_EXAMPLE_ENCOUNTER },
] as const

const TEMPLATE_LABELS: Record<string, string> = {
  starter: 'Starter Template',
  diabetes: 'Diabetes Care',
  hypertension: 'Hypertension',
  medication: 'Medication Safety',
  encounter: 'Encounter Analysis',
}

export default function CqlPlayground() {
  const { t } = useTranslation('landing')
  const isAuthenticated = useSelector((state: RootState) => state.auth.isAuthenticated)
  const [code, setCode] = useState(CQL_PLAYGROUND_STARTER)
  const [result, setResult] = useState<{ success: boolean; message: string } | null>(null)
  const [loading, setLoading] = useState(false)
  const [templateAnchor, setTemplateAnchor] = useState<null | HTMLElement>(null)

  const handleTranslate = useCallback(async () => {
    if (!isAuthenticated) return
    setLoading(true)
    setResult(null)
    try {
      const { cqlApi } = await import('../../api')
      const response = await cqlApi.translate({ cql: code })
      if (response.errors.length > 0) {
        setResult({
          success: false,
          message: t('learn.playground.errors', { count: response.errors.length }) + '\n' +
            response.errors.map((e) => `- ${e.message}`).join('\n'),
        })
      } else {
        setResult({ success: true, message: t('learn.playground.success') })
      }
    } catch {
      setResult({ success: false, message: 'Translation failed. Please check your CQL syntax.' })
    } finally {
      setLoading(false)
    }
  }, [code, isAuthenticated, t])

  const handleLoadTemplate = useCallback((templateCode: string) => {
    setCode(templateCode)
    setResult(null)
    setTemplateAnchor(null)
  }, [])

  const lineCount = useMemo(() => code.split('\n').length, [code])

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} gutterBottom color="secondary.main">
        {t('learn.playground.title')}
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        {t('learn.playground.subtitle')}
      </Typography>

      <Grid container spacing={3}>
        <Grid item xs={12}>
          <Paper elevation={0} sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }}>
            {/* Toolbar */}
            <Box sx={{ px: 2, py: 1, borderBottom: 1, borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 1 }}>
              <Button
                variant="contained"
                size="small"
                startIcon={loading ? undefined : <TranslateIcon />}
                onClick={handleTranslate}
                disabled={loading || !isAuthenticated}
                sx={{ textTransform: 'none' }}
              >
                {loading ? t('learn.playground.translating') : isAuthenticated ? t('learn.playground.translate') : t('learn.playground.loginToTranslate')}
              </Button>
              <Button
                size="small"
                endIcon={<DropdownIcon />}
                onClick={(e) => setTemplateAnchor(e.currentTarget)}
                sx={{ textTransform: 'none', ml: 1 }}
              >
                {t('learn.playground.loadTemplate')}
              </Button>
              <Menu
                anchorEl={templateAnchor}
                open={Boolean(templateAnchor)}
                onClose={() => setTemplateAnchor(null)}
              >
                {TEMPLATES.map(({ key, code: templateCode }) => (
                  <MenuItem key={key} onClick={() => handleLoadTemplate(templateCode)}>
                    {TEMPLATE_LABELS[key]}
                  </MenuItem>
                ))}
              </Menu>
              <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
                {lineCount} lines
              </Typography>
            </Box>

            {/* Editor */}
            <Box sx={{ display: 'flex' }}>
              {/* Line numbers */}
              <Box
                sx={{
                  p: 2,
                  pr: 1,
                  bgcolor: '#1E1E2E',
                  color: 'rgba(255,255,255,0.25)',
                  fontFamily: '"Fira Code", monospace',
                  fontSize: '0.82rem',
                  lineHeight: 1.6,
                  textAlign: 'right',
                  userSelect: 'none',
                  minWidth: 40,
                  whiteSpace: 'pre-line',
                }}
              >
                {Array.from({ length: lineCount }, (_, i) => i + 1).join('\n')}
              </Box>
              {/* Textarea */}
              <Box
                component="textarea"
                value={code}
                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setCode(e.target.value)}
                spellCheck={false}
                sx={{
                  flex: 1,
                  p: 2,
                  pl: 1,
                  bgcolor: '#1E1E2E',
                  color: '#D4D4D4',
                  fontFamily: '"Fira Code", "Cascadia Code", monospace',
                  fontSize: '0.82rem',
                  lineHeight: 1.6,
                  border: 'none',
                  outline: 'none',
                  resize: 'vertical',
                  minHeight: 400,
                  width: '100%',
                  whiteSpace: 'pre',
                  overflowX: 'auto',
                }}
              />
            </Box>
          </Paper>
        </Grid>

        {/* Result */}
        {!isAuthenticated && (
          <Grid item xs={12}>
            <Alert severity="info">{t('learn.playground.loginRequired')}</Alert>
          </Grid>
        )}
        {result && (
          <Grid item xs={12}>
            <Alert severity={result.success ? 'success' : 'error'} sx={{ whiteSpace: 'pre-line' }}>
              {result.message}
            </Alert>
          </Grid>
        )}
      </Grid>
    </Box>
  )
}
