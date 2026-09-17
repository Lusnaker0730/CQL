import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  alpha,
  Box,
  Chip,
  Collapse,
  Container,
  IconButton,
  Paper,
  Stack,
  Typography,
} from '@mui/material'
import LibraryBooksIcon from '@mui/icons-material/LibraryBooks'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { Helmet } from 'react-helmet-async'
import { getTemplatesByCategory } from '../constants/twcdiTemplates'
import { useCopyToClipboard } from '../hooks/useCopyToClipboard'
import PublicLayout from '../components/common/PublicLayout'

/**
 * PAT-208 — public official CQL template catalog. Surfaces the bundled TWCDI templates
 * (TW Core Data for Interoperability) grouped by category so a clinic can browse and copy
 * ready-made CQL before signing up. No auth required.
 */
export default function TemplateCatalogPage() {
  const { t } = useTranslation('landing')
  const { t: tc } = useTranslation()
  const copy = useCopyToClipboard()
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
      <PublicLayout
        icon={<LibraryBooksIcon sx={{ color: 'common.white', fontSize: 22 }} />}
        title={t('catalog.pageTitle')}
        showApplyCta
      >
        <Container maxWidth="lg" sx={{ py: 4 }}>
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
      </PublicLayout>
    </>
  )
}
