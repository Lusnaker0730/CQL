import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  alpha,
  Alert,
  Box,
  Button,
  Container,
  IconButton,
  Menu,
  MenuItem,
  Stack,
  Typography,
  useTheme,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import TranslateIcon from '@mui/icons-material/Translate'
import GavelIcon from '@mui/icons-material/Gavel'
import { Helmet } from 'react-helmet-async'

/**
 * PAT-211 — public legal page framework for Terms of Service and Privacy Policy.
 *
 * IMPORTANT: the content here is a clearly-marked DRAFT placeholder. A prominent banner states
 * the document is pending legal review and is not binding. The section headings are the shape a
 * real policy would take; the body text is placeholder pending authoritative legal wording. This
 * deliberately does NOT present fabricated legal terms as authoritative.
 */
export default function LegalPage({ doc }: { doc: 'terms' | 'privacy' }) {
  const theme = useTheme()
  const navigate = useNavigate()
  const { t, i18n } = useTranslation('landing')
  const { t: tc } = useTranslation()
  const [langAnchor, setLangAnchor] = useState<null | HTMLElement>(null)

  // Section keys per document. The body text for each is placeholder (see i18n).
  const sectionKeys: Record<'terms' | 'privacy', string[]> = {
    terms: ['service', 'eligibility', 'responsibilities', 'ip', 'disclaimer', 'liability', 'changes', 'governingLaw'],
    privacy: ['dataCollected', 'purpose', 'phi', 'retention', 'sharing', 'security', 'rights', 'contact'],
  }
  const sections = sectionKeys[doc]

  return (
    <>
      <Helmet>
        <title>{t(`legal.${doc}.title`)} — {tc('app.title')}</title>
      </Helmet>
      <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
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
            <GavelIcon sx={{ color: 'common.white', fontSize: 20 }} />
            <Typography variant="subtitle1" sx={{ fontWeight: 700, color: 'common.white' }}>
              {t(`legal.${doc}.title`)}
            </Typography>
          </Box>
          <Box>
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

        <Container maxWidth="md" sx={{ py: 4, flex: 1 }}>
          {/* Draft / pending-legal-review banner — the content below is NOT binding. */}
          <Alert severity="warning" sx={{ mb: 3 }}>
            {t('legal.draftBanner')}
          </Alert>

          <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
            {t(`legal.${doc}.title`)}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 3 }}>
            {t('legal.draftLabel')}
          </Typography>

          <Stack spacing={3}>
            {sections.map((s, i) => (
              <Box key={s}>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 0.5 }}>
                  {i + 1}. {t(`legal.${doc}.sections.${s}.heading`)}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-line' }}>
                  {t(`legal.${doc}.sections.${s}.body`)}
                </Typography>
              </Box>
            ))}
          </Stack>
        </Container>
      </Box>
    </>
  )
}
