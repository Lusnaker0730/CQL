import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  alpha,
  Box,
  Button,
  Chip,
  Collapse,
  Container,
  IconButton,
  Menu,
  MenuItem,
  Paper,
  Stack,
  Typography,
  useTheme,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import TranslateIcon from '@mui/icons-material/Translate'
import LibraryBooksIcon from '@mui/icons-material/LibraryBooks'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { Helmet } from 'react-helmet-async'
import { getTemplatesByCategory } from '../constants/twcdiTemplates'
import { useCopyToClipboard } from '../hooks/useCopyToClipboard'

/**
 * PAT-208 — public official CQL template catalog. Surfaces the bundled TWCDI templates
 * (TW Core Data for Interoperability) grouped by category so a clinic can browse and copy
 * ready-made CQL before signing up. No auth required.
 */
export default function TemplateCatalogPage() {
  const theme = useTheme()
  const navigate = useNavigate()
  const { t, i18n } = useTranslation('landing')
  const { t: tc } = useTranslation()
  const copy = useCopyToClipboard()
  const [langAnchor, setLangAnchor] = useState<null | HTMLElement>(null)
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})

  const categories = useMemo(() => Array.from(getTemplatesByCategory().entries()), [])
  const totalCount = useMemo(
    () => categories.reduce((sum, [, list]) => sum + list.length, 0),
    [categories],
  )

  return (
    <>
      <Helmet>
        <title>{t('catalog.pageTitle')} — {tc('app.title')}</title>
        <meta name="description" content={t('catalog.subtitle')} />
      </Helmet>
      <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
        {/* Header */}
        <Box
          sx={{
            background: `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.secondary.main} 100%)`,
            px: 3,
            py: 1.5,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Button
              startIcon={<ArrowBackIcon />}
              onClick={() => navigate('/login')}
              sx={{ color: alpha(theme.palette.common.white, 0.9), textTransform: 'none', mr: 2 }}
            >
              {t('learn.backToHome')}
            </Button>
            <LibraryBooksIcon sx={{ color: 'common.white', fontSize: 22 }} />
            <Typography variant="subtitle1" sx={{ fontWeight: 700, color: 'common.white' }}>
              {t('catalog.pageTitle')}
            </Typography>
          </Box>
          <Box>
            <Button
              variant="contained"
              size="small"
              onClick={() => navigate('/apply')}
              sx={{ mr: 1, textTransform: 'none', bgcolor: alpha(theme.palette.common.white, 0.2) }}
            >
              {t('hero.applyCta')}
            </Button>
            <IconButton onClick={(e) => setLangAnchor(e.currentTarget)} sx={{ color: alpha(theme.palette.common.white, 0.8) }}>
              <TranslateIcon fontSize="small" />
            </IconButton>
            <Menu anchorEl={langAnchor} open={Boolean(langAnchor)} onClose={() => setLangAnchor(null)}>
              <MenuItem onClick={() => { i18n.changeLanguage('en'); setLangAnchor(null) }} selected={i18n.language === 'en'}>
                {tc('language.english')}
              </MenuItem>
              <MenuItem onClick={() => { i18n.changeLanguage('zh-TW'); setLangAnchor(null) }} selected={i18n.language === 'zh-TW'}>
                {tc('language.traditionalChinese')}
              </MenuItem>
            </Menu>
          </Box>
        </Box>

        <Container maxWidth="lg" sx={{ py: 4, flex: 1 }}>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
            {t('catalog.title')}
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
            {t('catalog.subtitle', { count: totalCount })}
          </Typography>

          {categories.map(([category, list]) => (
            <Box key={category} sx={{ mb: 4 }}>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 1.5 }}>
                {category}{' '}
                <Chip size="small" label={list.length} sx={{ ml: 0.5 }} />
              </Typography>
              <Stack spacing={1.5}>
                {list.map((tpl) => {
                  const key = `${category}::${tpl.name}`
                  const isOpen = !!expanded[key]
                  return (
                    <Paper key={key} variant="outlined" sx={{ p: 2 }}>
                      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1 }}>
                        <Box>
                          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                            {tpl.name}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {tpl.description}
                          </Typography>
                        </Box>
                        <Stack direction="row" spacing={0.5} sx={{ flexShrink: 0 }}>
                          <IconButton
                            size="small"
                            aria-label={t('catalog.copyCode')}
                            onClick={() => copy(tpl.cql)}
                          >
                            <ContentCopyIcon fontSize="small" />
                          </IconButton>
                          <IconButton
                            size="small"
                            aria-label={t('catalog.toggleCode')}
                            onClick={() => setExpanded((s) => ({ ...s, [key]: !isOpen }))}
                            sx={{ transform: isOpen ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }}
                          >
                            <ExpandMoreIcon fontSize="small" />
                          </IconButton>
                        </Stack>
                      </Box>
                      <Collapse in={isOpen} unmountOnExit>
                        <Box
                          component="pre"
                          sx={{
                            mt: 1.5,
                            p: 1.5,
                            borderRadius: 1,
                            bgcolor: (th) => alpha(th.palette.text.primary, 0.04),
                            overflowX: 'auto',
                            fontSize: 13,
                            fontFamily: 'monospace',
                            m: 0,
                          }}
                        >
                          {tpl.cql}
                        </Box>
                      </Collapse>
                    </Paper>
                  )
                })}
              </Stack>
            </Box>
          ))}
        </Container>
      </Box>
    </>
  )
}
