import { useState, useEffect } from 'react'
import { Box } from '@mui/material'
import * as monaco from 'monaco-editor'

interface CqlPreviewBoxProps {
  code: string
  maxHeight?: number
}

export default function CqlPreviewBox({ code, maxHeight = 120 }: CqlPreviewBoxProps) {
  const [colorizedHtml, setColorizedHtml] = useState<string>('')

  useEffect(() => {
    if (!code) {
      setColorizedHtml('')
      return
    }
    let cancelled = false
    monaco.editor.colorize(code, 'cql', { tabSize: 2 }).then((html) => {
      if (!cancelled) setColorizedHtml(html)
    }).catch(() => {
      // Fallback: if colorize fails, clear html so plain text renders
      if (!cancelled) setColorizedHtml('')
    })
    return () => { cancelled = true }
  }, [code])

  if (!code) return null

  return (
    <Box
      sx={{
        p: 1,
        bgcolor: 'rgba(27,58,92,0.04)',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 1,
        fontFamily: 'monospace',
        fontSize: '0.75rem',
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-all',
        maxHeight,
        overflow: 'auto',
        '& .mtk1': { color: '#1B3A5C' },
      }}
    >
      {colorizedHtml ? (
        <div dangerouslySetInnerHTML={{ __html: colorizedHtml }} />
      ) : (
        code
      )}
    </Box>
  )
}
