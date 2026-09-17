import { Box, Typography } from '@mui/material'
import { alpha } from '@mui/material/styles'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

/** In-app public routes. Labels reuse each page's own title key so the footer can't drift
 *  out of sync with the page it points at. */
const INTERNAL_LINKS = [
  { labelKey: 'footer.learn', to: '/learn' },
  { labelKey: 'docs.pageTitle', to: '/docs' },
  { labelKey: 'catalog.pageTitle', to: '/templates' },
  { labelKey: 'status.pageTitle', to: '/status' },
  { labelKey: 'legal.terms.title', to: '/terms' },
  { labelKey: 'legal.privacy.title', to: '/privacy' },
] as const

const EXTERNAL_LINKS = [
  { label: 'CQL Specification', href: 'https://cql.hl7.org/' },
  { label: 'HL7 FHIR', href: 'https://www.hl7.org/fhir/' },
  { label: 'TWCORE IG', href: 'https://twcore.mohw.gov.tw/ig/twcore/' },
  { label: 'CDS Hooks', href: 'https://cds-hooks.org/' },
] as const

const CONTACT_EMAIL = 'aluminum001@gmail.com'

/**
 * Shared footer for every public (unauthenticated) page.
 *
 * Previously only LandingPage and LearnPage had a footer, and they carried different link sets
 * (Learn was missing the internal nav, the contact address and the CDS Hooks link), while
 * /docs, /templates, /status and /legal had no footer at all — so the route to /terms and
 * /privacy dead-ended the moment you navigated off the landing page.
 */
export default function PublicFooter() {
  const { t } = useTranslation('landing')
  const { t: tc } = useTranslation()
  const navigate = useNavigate()

  return (
    <Box component="footer" sx={{ py: 3, px: 3, bgcolor: 'secondary.dark', textAlign: 'center' }}>
      <Box sx={{ display: 'flex', justifyContent: 'center', gap: 3, mb: 1.5, flexWrap: 'wrap' }}>
        {INTERNAL_LINKS.map((link) => (
          <Typography
            key={link.to}
            component="button"
            onClick={() => navigate(link.to)}
            variant="body2"
            sx={(theme) => ({
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              p: 0,
              font: 'inherit',
              color: alpha(theme.palette.common.white, 0.6),
              '&:hover': { color: theme.palette.primary.light },
            })}
          >
            {t(link.labelKey)}
          </Typography>
        ))}
      </Box>
      <Box sx={{ display: 'flex', justifyContent: 'center', gap: 3, mb: 1.5, flexWrap: 'wrap' }}>
        {EXTERNAL_LINKS.map((link) => (
          <Typography
            key={link.label}
            component="a"
            href={link.href}
            target="_blank"
            rel="noopener noreferrer"
            variant="body2"
            sx={(theme) => ({
              color: alpha(theme.palette.common.white, 0.6),
              textDecoration: 'none',
              '&:hover': { color: theme.palette.primary.light },
            })}
          >
            {link.label}
          </Typography>
        ))}
      </Box>
      <Typography
        variant="caption"
        sx={(theme) => ({ display: 'block', mb: 0.5, color: alpha(theme.palette.common.white, 0.5) })}
      >
        {t('footer.contactPrompt')}{' '}
        <Box
          component="a"
          href={`mailto:${CONTACT_EMAIL}`}
          sx={(theme) => ({
            color: alpha(theme.palette.common.white, 0.75),
            textDecoration: 'none',
            '&:hover': { color: theme.palette.primary.light, textDecoration: 'underline' },
          })}
        >
          {CONTACT_EMAIL}
        </Box>
      </Typography>
      <Typography variant="caption" sx={(theme) => ({ color: alpha(theme.palette.common.white, 0.35) })}>
        {tc('app.title')} — Clinical Quality Language
      </Typography>
    </Box>
  )
}
