import { useTranslation } from 'react-i18next'
import {
  Alert,
  Box,
  Container,
  Stack,
  Typography,
} from '@mui/material'
import GavelIcon from '@mui/icons-material/Gavel'
import { Helmet } from 'react-helmet-async'
import PublicLayout from '../components/common/PublicLayout'

/**
 * PAT-211 — public legal page framework for Terms of Service and Privacy Policy.
 *
 * IMPORTANT: the content here is a clearly-marked DRAFT placeholder. A prominent banner states
 * the document is pending legal review and is not binding. The section headings are the shape a
 * real policy would take; the body text is placeholder pending authoritative legal wording. This
 * deliberately does NOT present fabricated legal terms as authoritative.
 */
export default function LegalPage({ doc }: { doc: 'terms' | 'privacy' }) {
  const { t } = useTranslation('landing')
  const { t: tc } = useTranslation()

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
      <PublicLayout
        icon={<GavelIcon sx={{ color: 'common.white', fontSize: 20 }} />}
        title={t(`legal.${doc}.title`)}
      >
        <Container maxWidth="md" sx={{ py: 4 }}>
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
      </PublicLayout>
    </>
  )
}
