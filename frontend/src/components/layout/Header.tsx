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
  Brightness4 as DarkModeIcon,
  Brightness7 as LightModeIcon,
  Logout as LogoutIcon,
  Person as PersonIcon,
  Settings as SettingsIcon,
  HelpOutlined as HelpIcon,
  ManageSearch as ManageSearchIcon,
} from '@mui/icons-material'
import { useNavigate, useLocation } from 'react-router-dom'
import { useSelector, useDispatch } from 'react-redux'
import { useTranslation } from 'react-i18next'
import type { RootState } from '../../store'
import { logout } from '../../store/authSlice'
import { authApi } from '../../api/authApi'
import { usePreferences } from '../../hooks/usePreferences'
import PreferencesDialog from '../common/PreferencesDialog'
import HelpDrawer from '../common/HelpDrawer'
import LanguageSwitcher from '../common/LanguageSwitcher'
import NotificationBell from './NotificationBell'
import TerminologyLookupDrawer from '../terminology/TerminologyLookupDrawer'
import { useTerminologyDrawer } from '../../hooks/useTerminologyDrawer'

const TOOLBAR_BTN_BG = 'rgba(255,255,255,0.08)'
const TOOLBAR_BTN_HOVER = 'rgba(255,255,255,0.15)'
const TOOLBAR_BTN_ACTIVE = 'rgba(255,255,255,0.22)'

const toolbarIconBtnSx = {
  backgroundColor: TOOLBAR_BTN_BG,
  '&:hover': { backgroundColor: TOOLBAR_BTN_HOVER },
  transition: 'background-color 0.2s ease',
} as const

const baseNavItems = [
  { labelKey: 'nav.editor', path: '/' },
  { labelKey: 'nav.cdsHooks', path: '/cds' },
  { labelKey: 'nav.measures', path: '/measures' },
  { labelKey: 'nav.authoring', path: '/authoring' },
  { labelKey: 'nav.ecqm', path: '/ecqm' },
  { labelKey: 'nav.cqlLibraries', path: '/cql-libraries' },
  { labelKey: 'nav.fhirBrowser', path: '/fhir' },
  { labelKey: 'nav.terminology', path: '/terminology' },
  { labelKey: 'nav.patientGenerator', path: '/patient-generator' },
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

  // Audit is tenant-scoped (any ADMIN sees only their own clinic); the rest are
  // platform-operator only (BUG-131). Clinic-tenant admins get neither the nav entries
  // nor — the real boundary — access, since the backend 403s these endpoints.
  const platformOperator = user?.platformOperator === true
  const navItems =
    user?.role === 'ADMIN'
      ? [
          ...baseNavItems,
          ...(platformOperator
            ? [
                { labelKey: 'nav.users', path: '/admin/users' },
                { labelKey: 'nav.tenants', path: '/admin/tenants' },
                { labelKey: 'nav.clinicApplications', path: '/admin/clinic-applications' },
              ]
            : // A clinic ADMIN manages the staff of their own tenant (PAT-214). The
              // platform operator has the fuller platform-wide Users page instead.
              [{ labelKey: 'nav.tenantUsers', path: '/tenant/users' }]),
          { labelKey: 'nav.auditLog', path: '/admin/audit' },
        ]
      : baseNavItems

  const handleLogout = () => {
    authApi.logout().catch(() => {})
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
                background: TOOLBAR_BTN_HOVER,
                backdropFilter: 'blur(10px)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                border: `1px solid ${TOOLBAR_BTN_ACTIVE}`,
              }}
            >
              <MedicalIcon sx={{ fontSize: 22, color: 'common.white' }} />
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
                  color: 'common.white',
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
                  color: 'common.white',
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
                      backgroundColor: isActive ? TOOLBAR_BTN_ACTIVE : 'transparent',
                      '&:hover': {
                        backgroundColor: isActive ? TOOLBAR_BTN_ACTIVE : TOOLBAR_BTN_BG,
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

          <Stack direction="row" spacing={0.5} sx={{
            alignItems: "center"
          }}>
            {user && (
              <Chip
                icon={<PersonIcon sx={{ color: 'rgba(255,255,255,0.8) !important', fontSize: 16 }} />}
                label={user.username}
                size="small"
                sx={{
                  color: 'common.white',
                  backgroundColor: TOOLBAR_BTN_BG,
                  border: `1px solid ${TOOLBAR_BTN_ACTIVE}`,
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
              sx={{ ...toolbarIconBtnSx, backgroundColor: terminologyOpen ? TOOLBAR_BTN_ACTIVE : TOOLBAR_BTN_BG }}
            >
              <ManageSearchIcon sx={{ fontSize: 20 }} />
            </IconButton>
            <IconButton
              color="inherit"
              onClick={toggleDarkMode}
              aria-label={preferences.themeMode === 'dark' ? t('toolbar.switchToLight') : t('toolbar.switchToDark')}
              title={preferences.themeMode === 'dark' ? t('toolbar.switchToLight') : t('toolbar.switchToDark')}
              sx={toolbarIconBtnSx}
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
              sx={toolbarIconBtnSx}
            >
              <SettingsIcon sx={{ fontSize: 20 }} />
            </IconButton>
            <IconButton
              color="inherit"
              onClick={() => setHelpOpen(true)}
              aria-label={t('toolbar.help')}
              title={t('toolbar.help')}
              sx={toolbarIconBtnSx}
            >
              <HelpIcon sx={{ fontSize: 20 }} />
            </IconButton>
            <IconButton
              color="inherit"
              onClick={handleLogout}
              aria-label={t('toolbar.logout')}
              title={t('toolbar.logout')}
              sx={toolbarIconBtnSx}
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
  );
}
