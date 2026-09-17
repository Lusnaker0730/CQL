import type { ReactNode } from 'react'
import { Box, Button, Typography } from '@mui/material'
import { alpha } from '@mui/material/styles'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { publicHeaderGradient } from '../../theme'
import LanguageMenu from './LanguageMenu'
import PublicFooter from './PublicFooter'

interface PublicLayoutProps {
  /** Icon rendered next to the title in the header bar. */
  icon: ReactNode
  /** Header bar title — usually the page's own `pageTitle` i18n string. */
  title: string
  /** Show the "apply for clinic access" CTA in the header. */
  showApplyCta?: boolean
  children: ReactNode
}

/**
 * Chrome shared by every public (unauthenticated) content page: coloured header bar with a
 * back-to-home button, page identity, optional apply CTA and language switcher, then the page
 * body, then the shared footer.
 *
 * Learn, Docs, Templates, Status and Legal each had their own hand-rolled copy of this header
 * (five near-identical blocks) and either a different footer or none at all. Pages own their own
 * `<Container>` inside `children`, because LearnPage needs full-bleed strips (the attribution
 * bar and the tab rail) that a layout-owned container would have boxed in.
 */
export default function PublicLayout({ icon, title, showApplyCta = false, children }: PublicLayoutProps) {
  const navigate = useNavigate()
  const { t } = useTranslation('landing')

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <Box
        component="header"
        sx={{
          background: publicHeaderGradient,
          px: 3,
          py: 1.5,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 1,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
          <Button
            startIcon={<ArrowBackIcon />}
            onClick={() => navigate('/login')}
            sx={(theme) => ({ color: alpha(theme.palette.common.white, 0.9), mr: 2, flexShrink: 0 })}
          >
            {t('learn.backToHome')}
          </Button>
          {icon}
          <Typography variant="subtitle1" noWrap sx={{ fontWeight: 700, color: 'common.white' }}>
            {title}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>
          {showApplyCta && (
            <Button
              variant="contained"
              size="small"
              onClick={() => navigate('/apply')}
              sx={(theme) => ({ mr: 1, bgcolor: alpha(theme.palette.common.white, 0.2) })}
            >
              {t('hero.applyCta')}
            </Button>
          )}
          <LanguageMenu />
        </Box>
      </Box>

      <Box component="main" sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        {children}
      </Box>

      <PublicFooter />
    </Box>
  )
}
