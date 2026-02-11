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
} from '@mui/icons-material'
import { useNavigate, useLocation } from 'react-router-dom'
import { useSelector, useDispatch } from 'react-redux'
import type { RootState } from '../../store'
import { logout } from '../../store/authSlice'
import { usePreferences } from '../../hooks/usePreferences'
import PreferencesDialog from '../common/PreferencesDialog'
import HelpDrawer from '../common/HelpDrawer'

const baseNavItems = [
  { label: 'Editor', path: '/' },
  { label: 'CDS Hooks', path: '/cds' },
  { label: 'Measures', path: '/measures' },
  { label: 'Authoring', path: '/authoring' },
  { label: 'FHIR Browser', path: '/fhir' },
  { label: 'Terminology', path: '/terminology' },
]

export default function Header() {
  const navigate = useNavigate()
  const location = useLocation()
  const dispatch = useDispatch()
  const user = useSelector((state: RootState) => state.auth.user)
  const { preferences, updatePreferences } = usePreferences()
  const [prefsOpen, setPrefsOpen] = useState(false)
  const [helpOpen, setHelpOpen] = useState(false)

  const navItems = user?.role === 'ADMIN'
    ? [...baseNavItems, { label: 'Users', path: '/admin/users' }, { label: 'Audit Log', path: '/admin/audit' }]
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
            aria-label="Go to home page"
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
                TWCORE CQL Platform
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
                Clinical Quality Language
              </Typography>
            </Box>
          </ButtonBase>

          <nav aria-label="Main navigation">
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
                    {item.label}
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
            <IconButton
              color="inherit"
              onClick={toggleDarkMode}
              aria-label={preferences.themeMode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
              title={preferences.themeMode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
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
              aria-label="Settings"
              title="Settings"
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
              aria-label="Help"
              title="Help"
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
              aria-label="GitHub"
              title="GitHub"
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
              aria-label="Logout"
              title="Logout"
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
    </>
  )
}
