import { useState } from 'react'
import {
  AppBar,
  Toolbar,
  Typography,
  Button,
  Stack,
  IconButton,
  Box,
  Chip,
  ButtonBase,
} from '@mui/material'
import {
  LocalHospital as MedicalIcon,
  GitHub as GitHubIcon,
  Brightness4 as DarkModeIcon,
  Brightness7 as LightModeIcon,
  Logout as LogoutIcon,
  Person as PersonIcon,
  Settings as SettingsIcon,
  HelpOutline as HelpIcon,
  ManageSearch as ManageSearchIcon,
} from '@mui/icons-material'
import { useNavigate, useLocation } from 'react-router-dom'
import { useSelector, useDispatch } from 'react-redux'
import { useTranslation } from 'react-i18next'
import type { RootState } from '../../store'
import { logout } from '../../store/authSlice'
import { usePreferences } from '../../hooks/usePreferences'
import PreferencesDialog from '../common/PreferencesDialog'
import HelpDrawer from '../common/HelpDrawer'
import LanguageSwitcher from '../common/LanguageSwitcher'
import NotificationBell from './NotificationBell'
import TerminologyLookupDrawer from '../terminology/TerminologyLookupDrawer'
import { useTerminologyDrawer } from '../../hooks/useTerminologyDrawer'

const baseNavItems = [
  { labelKey: 'nav.editor', path: '/' },
  { labelKey: 'nav.cdsHooks', path: '/cds' },
  { labelKey: 'nav.measures', path: '/measures' },
  { labelKey: 'nav.authoring', path: '/authoring' },
  { labelKey: 'nav.fhirBrowser', path: '/fhir' },
  { labelKey: 'nav.terminology', path: '/terminology' },
]

export default function Header() {
  const navigate = useNavigate()
  const location = useLocation()
  const dispatch = useDispatch()
  const { t } = useTranslation()
  const user = useSelector((state: RootState) => state.auth.user)
  const { preferences, updatePreferences } = usePreferences()
  const [prefsOpen, setPrefsOpen] = useState(false)
  const [helpOpen, setHelpOpen] = useState(false)
  const { isOpen: terminologyOpen, openDrawer: openTerminology, closeDrawer: closeTerminology } = useTerminologyDrawer()

  const navItems = user?.role === 'ADMIN'
    ? [...baseNavItems, { labelKey: 'nav.users', path: '/admin/users' }, { labelKey: 'nav.auditLog', path: '/admin/audit' }]
    : baseNavItems

  const handleLogout = () => {
    dispatch(logout())
    navigate('/login')
  }

  const toggleDarkMode = () => {
    updatePreferences({ themeMode: preferences.themeMode === 'dark' ? 'light' : 'dark' })
  }

  return (
    <>
      <AppBar position="static" elevation={0}>
        <Toolbar>
          <ButtonBase
            aria-label={t('nav.goToHome')}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.5,
              mr: 4,
              borderRadius: '8px',
              p: 0.5,
            }}
            onClick={() => navigate('/')}
          >
            <Box
              sx={{
                width: 36,
                height: 36,
                borderRadius: '10px',
                background: 'rgba(255,255,255,0.15)',
                backdropFilter: 'blur(10px)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                border: '1px solid rgba(255,255,255,0.2)',
              }}
            >
              <MedicalIcon sx={{ fontSize: 22, color: 'white' }} />
            </Box>
            <Box sx={{ textAlign: 'left' }}>
              <Typography
                variant="h6"
                component="div"
                sx={{
                  fontWeight: 700,
                  fontSize: '1.05rem',
                  lineHeight: 1.2,
                  letterSpacing: '0.02em',
                  color: 'white',
                }}
              >
                {t('app.title')}
              </Typography>
              <Typography
                sx={{
                  fontSize: '0.65rem',
                  opacity: 0.7,
                  letterSpacing: '0.1em',
                  textTransform: 'uppercase',
                  lineHeight: 1,
                  color: 'white',
                }}
              >
                {t('app.subtitle')}
              </Typography>
            </Box>
          </ButtonBase>

          <nav aria-label={t('nav.mainNavigation')}>
            <Stack direction="row" spacing={0.5} sx={{ flexGrow: 1 }}>
              {navItems.map((item) => {
                const isActive = location.pathname === item.path
                return (
                  <Button
                    key={item.path}
                    color="inherit"
                    onClick={() => navigate(item.path)}
                    aria-current={isActive ? 'page' : undefined}
                    sx={{
                      borderRadius: '20px',
                      px: 2,
                      py: 0.5,
                      fontSize: '0.85rem',
                      fontWeight: isActive ? 600 : 400,
                      backgroundColor: isActive
                        ? 'rgba(255,255,255,0.18)'
                        : 'transparent',
                      '&:hover': {
                        backgroundColor: isActive
                          ? 'rgba(255,255,255,0.22)'
                          : 'rgba(255,255,255,0.08)',
                      },
                      transition: 'all 0.2s ease',
                    }}
                  >
                    {t(item.labelKey)}
                  </Button>
                )
              })}
            </Stack>
          </nav>

          <Box sx={{ flexGrow: 1 }} />

          <Stack direction="row" spacing={0.5} alignItems="center">
            {user && (
              <Chip
                icon={<PersonIcon sx={{ color: 'rgba(255,255,255,0.8) !important', fontSize: 16 }} />}
                label={user.username}
                size="small"
                sx={{
                  color: 'white',
                  backgroundColor: 'rgba(255,255,255,0.12)',
                  border: '1px solid rgba(255,255,255,0.2)',
                  mr: 0.5,
                  '& .MuiChip-label': { fontSize: '0.8rem' },
                }}
              />
            )}
            <NotificationBell />
            <LanguageSwitcher />
            <IconButton
              color="inherit"
              onClick={() => terminologyOpen ? closeTerminology() : openTerminology()}
              aria-label={t('toolbar.terminologyLookup')}
              title={t('toolbar.terminologyLookup')}
              sx={{
                backgroundColor: terminologyOpen ? 'rgba(255,255,255,0.22)' : 'rgba(255,255,255,0.08)',
                '&:hover': { backgroundColor: 'rgba(255,255,255,0.15)' },
                transition: 'background-color 0.2s ease',
              }}
            >
              <ManageSearchIcon sx={{ fontSize: 20 }} />
            </IconButton>
            <IconButton
              color="inherit"
              onClick={toggleDarkMode}
              aria-label={preferences.themeMode === 'dark' ? t('toolbar.switchToLight') : t('toolbar.switchToDark')}
              title={preferences.themeMode === 'dark' ? t('toolbar.switchToLight') : t('toolbar.switchToDark')}
              sx={{
                backgroundColor: 'rgba(255,255,255,0.08)',
                '&:hover': { backgroundColor: 'rgba(255,255,255,0.15)' },
                transition: 'background-color 0.2s ease',
              }}
            >
              {preferences.themeMode === 'dark' ? (
                <LightModeIcon sx={{ fontSize: 20 }} />
              ) : (
                <DarkModeIcon sx={{ fontSize: 20 }} />
              )}
            </IconButton>
            <IconButton
              color="inherit"
              onClick={() => setPrefsOpen(true)}
              aria-label={t('toolbar.settings')}
              title={t('toolbar.settings')}
              sx={{
                backgroundColor: 'rgba(255,255,255,0.08)',
                '&:hover': { backgroundColor: 'rgba(255,255,255,0.15)' },
                transition: 'background-color 0.2s ease',
              }}
            >
              <SettingsIcon sx={{ fontSize: 20 }} />
            </IconButton>
            <IconButton
              color="inherit"
              onClick={() => setHelpOpen(true)}
              aria-label={t('toolbar.help')}
              title={t('toolbar.help')}
              sx={{
                backgroundColor: 'rgba(255,255,255,0.08)',
                '&:hover': { backgroundColor: 'rgba(255,255,255,0.15)' },
                transition: 'background-color 0.2s ease',
              }}
            >
              <HelpIcon sx={{ fontSize: 20 }} />
            </IconButton>
            <IconButton
              color="inherit"
              href="https://github.com"
              target="_blank"
              rel="noopener noreferrer"
              aria-label={t('toolbar.github')}
              title={t('toolbar.github')}
              sx={{
                backgroundColor: 'rgba(255,255,255,0.08)',
                '&:hover': { backgroundColor: 'rgba(255,255,255,0.15)' },
                transition: 'background-color 0.2s ease',
              }}
            >
              <GitHubIcon sx={{ fontSize: 20 }} />
            </IconButton>
            <IconButton
              color="inherit"
              onClick={handleLogout}
              aria-label={t('toolbar.logout')}
              title={t('toolbar.logout')}
              sx={{
                backgroundColor: 'rgba(255,255,255,0.08)',
                '&:hover': { backgroundColor: 'rgba(255,255,255,0.15)' },
                transition: 'background-color 0.2s ease',
              }}
            >
              <LogoutIcon sx={{ fontSize: 20 }} />
            </IconButton>
          </Stack>
        </Toolbar>
      </AppBar>

      <PreferencesDialog open={prefsOpen} onClose={() => setPrefsOpen(false)} />
      <HelpDrawer open={helpOpen} onClose={() => setHelpOpen(false)} />
      <TerminologyLookupDrawer />
    </>
  )
}
