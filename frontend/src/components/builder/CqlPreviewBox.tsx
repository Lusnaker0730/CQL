import { useState, useEffect } from 'react'
import DOMPurify from 'dompurify'
import { Box } from '@mui/material'
import { PREVIEW_MAX_HEIGHT } from '../../constants/layout'
import { useMonaco } from '@monaco-editor/react'
import { usePreferences } from '../../hooks/usePreferences'

interface CqlPreviewBoxProps {
  code: string
  maxHeight?: number
}

// Monaco's setTheme is global — applying it forces every editor instance
// (including the main editor) to repaint. Cache the last-applied theme so
// previews don't trigger redundant repaints on every keystroke.
let lastAppliedTheme: string | null = null

export default function CqlPreviewBox({ code, maxHeight = PREVIEW_MAX_HEIGHT }: CqlPreviewBoxProps) {
  const [colorizedHtml, setColorizedHtml] = useState<string>('')
  const monaco = useMonaco()
  const { preferences } = usePreferences()
  const isDark = preferences.themeMode === 'dark'

  useEffect(() => {
    if (!code || !monaco) {
      setColorizedHtml('')
      return
    }
    let cancelled = false
    const themeName = isDark ? 'cql-theme-dark' : 'cql-theme'
    if (lastAppliedTheme !== themeName) {
      monaco.editor.setTheme(themeName)
      lastAppliedTheme = themeName
    }

    monaco.editor.colorize(code, 'cql', { tabSize: 2 }).then((html) => {
      if (!cancelled) setColorizedHtml(html)
    }).catch(() => {
      if (!cancelled) setColorizedHtml('')
    })
    return () => { cancelled = true }
  }, [code, monaco, isDark])

  if (!code) return null

  return (
    <Box
      sx={{
        p: 1,
        bgcolor: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(27,58,92,0.04)',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 1,
        fontFamily: 'monospace',
        fontSize: '0.75rem',
        whiteSpace: 'pre',
        maxHeight,
        overflow: 'auto',
        '& .mtk1': { color: isDark ? '#D4D4D4' : '#1B3A5C' },
      }}
    >
      {colorizedHtml ? (
        <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(colorizedHtml) }} />
      ) : (
        code
      )}
    </Box>
  )
}
