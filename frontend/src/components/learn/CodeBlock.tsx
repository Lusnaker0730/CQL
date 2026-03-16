import { Box, IconButton, Tooltip } from '@mui/material'
import { alpha } from '@mui/material/styles'
import { ContentCopy as CopyIcon, Check as CheckIcon } from '@mui/icons-material'
import { useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { COPY_FEEDBACK_TIMEOUT_MS } from '../../constants/timing'

export const CODE_BLOCK_BG = '#1E1E2E'
export const CODE_BLOCK_FG = '#D4D4D4'

interface CodeBlockProps {
  code: string
  maxHeight?: number
}

export default function CodeBlock({ code, maxHeight = 320 }: CodeBlockProps) {
  const { t } = useTranslation('landing')
  const [copied, setCopied] = useState(false)

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(code).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), COPY_FEEDBACK_TIMEOUT_MS)
    })
  }, [code])

  return (
    <Box sx={{ position: 'relative', borderRadius: 2, overflow: 'hidden', border: '1px solid', borderColor: 'divider' }}>
      <Tooltip title={copied ? t('learn.examples.copied') : t('learn.examples.copyCode')}>
        <IconButton
          onClick={handleCopy}
          size="small"
          sx={(sxTheme) => ({
            position: 'absolute',
            top: 8,
            right: 8,
            zIndex: 1,
            color: alpha(sxTheme.palette.common.white, 0.5),
            '&:hover': {
              color: sxTheme.palette.common.white,
              bgcolor: alpha(sxTheme.palette.common.white, 0.1),
            },
          })}
        >
          {copied ? <CheckIcon fontSize="small" /> : <CopyIcon fontSize="small" />}
        </IconButton>
      </Tooltip>
      <Box
        component="pre"
        sx={{
          m: 0,
          p: 2,
          bgcolor: CODE_BLOCK_BG,
          color: CODE_BLOCK_FG,
          fontFamily: '"Fira Code", "Cascadia Code", monospace',
          fontSize: '0.78rem',
          lineHeight: 1.6,
          overflow: 'auto',
          maxHeight,
          whiteSpace: 'pre',
        }}
      >
        {code}
      </Box>
    </Box>
  )
}
