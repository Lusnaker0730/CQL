import { useState, useCallback, useRef } from 'react'
import { Box, Typography, Paper, Button, Alert, Grid, Menu, MenuItem } from '@mui/material'
import {
  PlayArrow as TranslateIcon,
  ExpandMore as DropdownIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useSelector, useDispatch } from 'react-redux'
import type { RootState } from '../../store'
import { setCqlContent } from '../../store/editorSlice'
import CqlEditor from '../editor/CqlEditor'
import type { CqlEditorHandle } from '../editor/CqlEditor'
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

export default function CqlPlayground() {
  const { t } = useTranslation('landing')
  const dispatch = useDispatch()
  const isAuthenticated = useSelector((state: RootState) => state.auth.isAuthenticated)
  const cqlEditorRef = useRef<CqlEditorHandle>(null)
  const [result, setResult] = useState<{ success: boolean; message: string } | null>(null)
  const [loading, setLoading] = useState(false)
  const [templateAnchor, setTemplateAnchor] = useState<null | HTMLElement>(null)
  const [lineCount, setLineCount] = useState(CQL_PLAYGROUND_STARTER.split('\n').length)

  const handleContentChanged = useCallback((content: string) => {
    setLineCount(content.split('\n').length)
  }, [])

  const handleTranslate = useCallback(async () => {
    if (!isAuthenticated) return
    const code = cqlEditorRef.current?.getContent() ?? ''
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
      setResult({ success: false, message: t('learn.playground.translationFailed') })
    } finally {
      setLoading(false)
    }
  }, [isAuthenticated, t])

  const handleLoadTemplate = useCallback((templateCode: string) => {
    dispatch(setCqlContent(templateCode))
    setLineCount(templateCode.split('\n').length)
    setResult(null)
    setTemplateAnchor(null)
  }, [dispatch])

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} gutterBottom color="secondary.main">
        {t('learn.playground.title')}
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        {t('learn.playground.subtitle')}
      </Typography>

      <Grid container spacing={3}>
        <Grid size={12}>
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
                    {t(`learn.playground.templates.${key}`)}
                  </MenuItem>
                ))}
              </Menu>
              <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
                {t('learn.playground.lines', { count: lineCount })}
              </Typography>
            </Box>

            {/* Monaco Editor */}
            <CqlEditor
              ref={cqlEditorRef}
              height={400}
              onContentChanged={handleContentChanged}
            />
          </Paper>
        </Grid>

        {/* Result */}
        {!isAuthenticated && (
          <Grid size={12}>
            <Alert severity="info">{t('learn.playground.loginRequired')}</Alert>
          </Grid>
        )}
        {result && (
          <Grid size={12}>
            <Alert severity={result.success ? 'success' : 'error'} sx={{ whiteSpace: 'pre-line' }}>
              {result.message}
            </Alert>
          </Grid>
        )}
      </Grid>
    </Box>
  )
}
