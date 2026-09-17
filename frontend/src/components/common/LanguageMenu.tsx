import { useState, useCallback } from 'react'
import { IconButton, Menu, MenuItem } from '@mui/material'
import { alpha, type SxProps, type Theme } from '@mui/material/styles'
import TranslateIcon from '@mui/icons-material/Translate'
import { useTranslation } from 'react-i18next'

interface LanguageMenuProps {
  /** Override the trigger button styling. Defaults to the translucent-white treatment used on
   *  the coloured public header bars. */
  sx?: SxProps<Theme>
}

/**
 * Language switcher for the public pages.
 *
 * Landing, Learn, Docs, Templates and Legal each carried their own copy of this
 * IconButton + Menu pair; they are all this component now.
 */
export default function LanguageMenu({ sx }: LanguageMenuProps) {
  const { t, i18n } = useTranslation()
  const [anchor, setAnchor] = useState<null | HTMLElement>(null)

  const close = useCallback(() => setAnchor(null), [])
  const change = useCallback(
    (lang: string) => {
      i18n.changeLanguage(lang)
      setAnchor(null)
    },
    [i18n],
  )

  return (
    <>
      <IconButton
        onClick={(e) => setAnchor(e.currentTarget)}
        aria-label={t('language.switchLanguage')}
        sx={sx ?? ((theme) => ({ color: alpha(theme.palette.common.white, 0.8) }))}
      >
        <TranslateIcon fontSize="small" />
      </IconButton>
      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={close}>
        <MenuItem onClick={() => change('en')} selected={i18n.language === 'en'}>
          {t('language.english')}
        </MenuItem>
        <MenuItem onClick={() => change('zh-TW')} selected={i18n.language === 'zh-TW'}>
          {t('language.traditionalChinese')}
        </MenuItem>
      </Menu>
    </>
  )
}
