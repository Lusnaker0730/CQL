import { useState } from 'react'
import { IconButton, Menu, MenuItem, ListItemIcon, ListItemText } from '@mui/material'
import { alpha } from '@mui/material/styles'
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection.
import TranslateIcon from '@mui/icons-material/Translate'
import CheckIcon from '@mui/icons-material/Check'
import { useTranslation } from 'react-i18next'

const LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'zh-TW', label: '繁體中文' },
] as const

export default function LanguageSwitcher() {
  const { i18n, t } = useTranslation()
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)

  const handleClose = () => setAnchorEl(null)

  const handleChange = (code: string) => {
    i18n.changeLanguage(code)
    handleClose()
  }

  return (
    <>
      <IconButton
        color="inherit"
        onClick={(e) => setAnchorEl(e.currentTarget)}
        aria-label={t('language.switchLanguage')}
        title={t('language.switchLanguage')}
        sx={(theme) => ({
          backgroundColor: alpha(theme.palette.common.white, 0.08),
          '&:hover': { backgroundColor: alpha(theme.palette.common.white, 0.15) },
          transition: 'background-color 0.2s ease',
        })}
      >
        <TranslateIcon sx={{ fontSize: 20 }} />
      </IconButton>
      <Menu anchorEl={anchorEl} open={!!anchorEl} onClose={handleClose}>
        {LANGUAGES.map((lang) => (
          <MenuItem key={lang.code} onClick={() => handleChange(lang.code)} selected={i18n.language === lang.code}>
            {i18n.language === lang.code && (
              <ListItemIcon sx={{ minWidth: 32 }}>
                <CheckIcon fontSize="small" />
              </ListItemIcon>
            )}
            <ListItemText inset={i18n.language !== lang.code}>{lang.label}</ListItemText>
          </MenuItem>
        ))}
      </Menu>
    </>
  )
}
