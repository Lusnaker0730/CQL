import {
  AppBar,
  Toolbar,
  Typography,
  Button,
  Stack,
  IconButton,
  useTheme,
} from '@mui/material'
import {
  Code as CodeIcon,
  GitHub as GitHubIcon,
  Brightness4 as DarkModeIcon,
} from '@mui/icons-material'
import { useNavigate, useLocation } from 'react-router-dom'

const navItems = [
  { label: 'Editor', path: '/' },
  { label: 'CDS Hooks', path: '/cds' },
  { label: 'Measures', path: '/measures' },
  { label: 'FHIR Browser', path: '/fhir' },
]

export default function Header() {
  const navigate = useNavigate()
  const location = useLocation()
  const theme = useTheme()

  return (
    <AppBar position="static" elevation={1}>
      <Toolbar>
        <CodeIcon sx={{ mr: 1 }} />
        <Typography
          variant="h6"
          component="div"
          sx={{ flexGrow: 0, mr: 4, cursor: 'pointer' }}
          onClick={() => navigate('/')}
        >
          CQL Platform
        </Typography>

        <Stack direction="row" spacing={1} sx={{ flexGrow: 1 }}>
          {navItems.map((item) => (
            <Button
              key={item.path}
              color="inherit"
              onClick={() => navigate(item.path)}
              sx={{
                borderBottom:
                  location.pathname === item.path
                    ? `2px solid ${theme.palette.common.white}`
                    : 'none',
                borderRadius: 0,
                '&:hover': {
                  backgroundColor: 'rgba(255,255,255,0.1)',
                },
              }}
            >
              {item.label}
            </Button>
          ))}
        </Stack>

        <Stack direction="row" spacing={1}>
          <IconButton color="inherit" title="Toggle dark mode">
            <DarkModeIcon />
          </IconButton>
          <IconButton
            color="inherit"
            href="https://github.com"
            target="_blank"
            title="GitHub"
          >
            <GitHubIcon />
          </IconButton>
        </Stack>
      </Toolbar>
    </AppBar>
  )
}
