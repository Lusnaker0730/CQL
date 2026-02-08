import { createTheme, alpha } from '@mui/material/styles'

type PaletteMode = 'light' | 'dark'

const lightPalette = {
  primary: {
    main: '#0D7377',
    light: '#14A3A8',
    dark: '#095052',
    contrastText: '#ffffff',
  },
  secondary: {
    main: '#1B3A5C',
    light: '#2D5F8A',
    dark: '#0F2440',
    contrastText: '#ffffff',
  },
  success: {
    main: '#2E7D32',
    light: '#4CAF50',
    dark: '#1B5E20',
  },
  warning: {
    main: '#ED6C02',
    light: '#FF9800',
    dark: '#E65100',
  },
  error: {
    main: '#D32F2F',
    light: '#EF5350',
    dark: '#C62828',
  },
  background: {
    default: '#F4F7F9',
    paper: '#FFFFFF',
  },
  text: {
    primary: '#1A2B3C',
    secondary: '#546E7A',
  },
}

const darkPalette = {
  primary: {
    main: '#14A3A8',
    light: '#4DD0E1',
    dark: '#0D7377',
    contrastText: '#ffffff',
  },
  secondary: {
    main: '#90CAF9',
    light: '#BBDEFB',
    dark: '#42A5F5',
    contrastText: '#000000',
  },
  success: {
    main: '#66BB6A',
    light: '#81C784',
    dark: '#388E3C',
  },
  warning: {
    main: '#FFA726',
    light: '#FFB74D',
    dark: '#F57C00',
  },
  error: {
    main: '#EF5350',
    light: '#E57373',
    dark: '#D32F2F',
  },
  background: {
    default: '#121212',
    paper: '#1E1E1E',
  },
  text: {
    primary: '#E0E0E0',
    secondary: '#A0A0A0',
  },
}

export function createAppTheme(mode: PaletteMode) {
  const palette = mode === 'dark' ? darkPalette : lightPalette

  return createTheme({
    palette: {
      mode,
      ...palette,
    },
    typography: {
      fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
      h1: { fontSize: '2.5rem', fontWeight: 700, color: palette.secondary.main, letterSpacing: '-0.02em' },
      h2: { fontSize: '2rem', fontWeight: 700, color: palette.secondary.main, letterSpacing: '-0.01em' },
      h3: { fontSize: '1.75rem', fontWeight: 600, color: palette.secondary.main },
      h4: { fontSize: '1.5rem', fontWeight: 600, color: palette.secondary.main },
      h5: { fontSize: '1.25rem', fontWeight: 600, color: palette.secondary.main },
      h6: { fontSize: '1rem', fontWeight: 600, color: palette.text.primary },
      subtitle1: { fontWeight: 600, color: palette.text.primary },
      subtitle2: { fontWeight: 500, color: palette.text.secondary, textTransform: 'uppercase' as const, fontSize: '0.75rem', letterSpacing: '0.08em' },
      body2: { color: palette.text.secondary },
      caption: { color: palette.text.secondary },
    },
    shape: {
      borderRadius: 10,
    },
    shadows: [
      'none',
      '0 1px 3px rgba(13,115,119,0.08)',
      '0 2px 6px rgba(13,115,119,0.10)',
      '0 3px 10px rgba(13,115,119,0.12)',
      '0 4px 14px rgba(13,115,119,0.14)',
      '0 6px 20px rgba(13,115,119,0.16)',
      '0 8px 26px rgba(13,115,119,0.18)',
      '0 10px 32px rgba(13,115,119,0.20)',
      '0 12px 38px rgba(13,115,119,0.22)',
      '0 14px 42px rgba(13,115,119,0.24)',
      '0 16px 48px rgba(13,115,119,0.26)',
      '0 18px 52px rgba(13,115,119,0.28)',
      '0 20px 56px rgba(13,115,119,0.30)',
      '0 22px 60px rgba(13,115,119,0.30)',
      '0 24px 64px rgba(13,115,119,0.30)',
      '0 26px 68px rgba(13,115,119,0.30)',
      '0 28px 72px rgba(13,115,119,0.30)',
      '0 30px 76px rgba(13,115,119,0.30)',
      '0 32px 80px rgba(13,115,119,0.30)',
      '0 34px 84px rgba(13,115,119,0.30)',
      '0 36px 88px rgba(13,115,119,0.30)',
      '0 38px 92px rgba(13,115,119,0.30)',
      '0 40px 96px rgba(13,115,119,0.30)',
      '0 42px 100px rgba(13,115,119,0.30)',
      '0 44px 104px rgba(13,115,119,0.30)',
    ],
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            scrollbarWidth: 'thin',
            scrollbarColor: `${palette.primary.main}40 transparent`,
          },
          '*::-webkit-scrollbar': {
            width: 8,
          },
          '*::-webkit-scrollbar-track': {
            background: 'transparent',
          },
          '*::-webkit-scrollbar-thumb': {
            backgroundColor: `${palette.primary.main}40`,
            borderRadius: 4,
          },
        },
      },
      MuiButton: {
        styleOverrides: {
          root: {
            textTransform: 'none',
            borderRadius: 8,
            fontWeight: 600,
            transition: 'all 0.2s ease-in-out',
            '&:hover': {
              transform: 'translateY(-1px)',
            },
            '&:active': {
              transform: 'translateY(0)',
            },
          },
          contained: {
            boxShadow: `0 2px 8px ${alpha(palette.primary.main, 0.25)}`,
            '&:hover': {
              boxShadow: `0 4px 14px ${alpha(palette.primary.main, 0.35)}`,
              transform: 'translateY(-1px)',
            },
          },
          outlined: {
            borderWidth: 1.5,
            '&:hover': {
              borderWidth: 1.5,
              backgroundColor: alpha(palette.primary.main, 0.04),
            },
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            boxShadow: '0 2px 8px rgba(13,115,119,0.08)',
            transition: 'all 0.25s ease',
            '&:hover': {
              transform: 'translateY(-2px)',
              boxShadow: '0 6px 20px rgba(13,115,119,0.15)',
            },
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            boxShadow: '0 1px 4px rgba(13,115,119,0.06)',
          },
        },
      },
      MuiAppBar: {
        styleOverrides: {
          root: {
            background: mode === 'dark'
              ? `linear-gradient(135deg, #1a1a2e 0%, #16213e 100%)`
              : `linear-gradient(135deg, ${lightPalette.secondary.dark} 0%, ${lightPalette.primary.dark} 100%)`,
            boxShadow: `0 2px 12px ${alpha(palette.secondary.dark, 0.3)}`,
          },
        },
      },
      MuiTextField: {
        styleOverrides: {
          root: {
            '& .MuiOutlinedInput-root': {
              transition: 'box-shadow 0.2s ease',
              '&:hover .MuiOutlinedInput-notchedOutline': {
                borderColor: palette.primary.light,
              },
              '&.Mui-focused': {
                boxShadow: `0 0 0 3px ${alpha(palette.primary.main, 0.15)}`,
              },
              '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                borderColor: palette.primary.main,
                borderWidth: 2,
              },
            },
          },
        },
      },
      MuiTab: {
        styleOverrides: {
          root: {
            textTransform: 'none',
            fontWeight: 500,
            minHeight: 42,
            '&.Mui-selected': {
              fontWeight: 600,
              color: palette.primary.main,
            },
          },
        },
      },
      MuiTabs: {
        styleOverrides: {
          indicator: {
            height: 3,
            borderRadius: '3px 3px 0 0',
            backgroundColor: palette.primary.main,
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: {
            fontWeight: 500,
            borderRadius: 6,
          },
          colorPrimary: {
            backgroundColor: alpha(palette.primary.main, 0.1),
            color: palette.primary.dark,
          },
        },
      },
      MuiTableHead: {
        styleOverrides: {
          root: {
            '& .MuiTableCell-head': {
              fontWeight: 600,
              color: palette.secondary.main,
              backgroundColor: alpha(palette.primary.main, 0.04),
              borderBottom: `2px solid ${alpha(palette.primary.main, 0.15)}`,
              textTransform: 'uppercase',
              fontSize: '0.75rem',
              letterSpacing: '0.05em',
            },
          },
        },
      },
      MuiTableRow: {
        styleOverrides: {
          root: {
            transition: 'background-color 0.15s ease',
            '&:hover': {
              backgroundColor: `${alpha(palette.primary.main, 0.03)} !important`,
            },
          },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          root: {
            borderBottom: `1px solid ${alpha(palette.primary.main, 0.08)}`,
          },
        },
      },
      MuiAlert: {
        styleOverrides: {
          root: {
            borderRadius: 10,
          },
          standardError: {
            backgroundColor: alpha(palette.error.main, 0.08),
            border: `1px solid ${alpha(palette.error.main, 0.2)}`,
          },
          standardWarning: {
            backgroundColor: alpha(palette.warning.main, 0.08),
            border: `1px solid ${alpha(palette.warning.main, 0.2)}`,
          },
          standardInfo: {
            backgroundColor: alpha(palette.primary.main, 0.08),
            border: `1px solid ${alpha(palette.primary.main, 0.2)}`,
          },
          standardSuccess: {
            backgroundColor: alpha(palette.success.main, 0.08),
            border: `1px solid ${alpha(palette.success.main, 0.2)}`,
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: 16,
            boxShadow: '0 12px 40px rgba(13,115,119,0.2)',
          },
        },
      },
      MuiDialogTitle: {
        styleOverrides: {
          root: {
            fontWeight: 600,
            color: palette.secondary.main,
          },
        },
      },
      MuiDivider: {
        styleOverrides: {
          root: {
            borderColor: alpha(palette.primary.main, 0.1),
          },
        },
      },
      MuiLinearProgress: {
        styleOverrides: {
          root: {
            height: 8,
            borderRadius: 4,
            backgroundColor: alpha(palette.primary.main, 0.1),
          },
        },
      },
      MuiSelect: {
        styleOverrides: {
          root: {
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
              borderColor: palette.primary.main,
            },
          },
        },
      },
    },
  })
}

// Keep backwards compat for any direct imports
export const theme = createAppTheme('light')
