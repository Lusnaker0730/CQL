import { useState } from 'react'
import { Box, Typography, Stack, Collapse, IconButton, Paper } from '@mui/material'
import {
  KeyboardArrowDown as ExpandIcon,
  KeyboardArrowUp as CollapseIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'

interface ElmJsonViewerProps {
  elmJson: string
}

/**
 * Collapsible compiled-ELM JSON viewer. Pretty-prints if the input parses;
 * otherwise renders the raw string verbatim (parse failures shouldn't
 * suppress the output — authors still benefit from seeing what the engine
 * compiled to even if the JSON is malformed).
 */
export default function ElmJsonViewer({ elmJson }: ElmJsonViewerProps) {
  const { t } = useTranslation('common')
  const [open, setOpen] = useState(false)

  let pretty = elmJson
  try {
    pretty = JSON.stringify(JSON.parse(elmJson), null, 2)
  } catch {
    // keep raw — malformed ELM is still useful to an author debugging
  }

  return (
    <Box>
      <Stack direction="row" alignItems="center" spacing={0.5}>
        <IconButton size="small" onClick={() => setOpen(!open)}>
          {open ? <CollapseIcon fontSize="small" /> : <ExpandIcon fontSize="small" />}
        </IconButton>
        <Typography variant="caption" color="text.secondary">
          {t('debug.compiledElm')}
        </Typography>
      </Stack>
      <Collapse in={open} timeout="auto" unmountOnExit>
        <Paper variant="outlined" sx={{ p: 1, mt: 0.5, bgcolor: 'background.default', maxHeight: 400, overflow: 'auto' }}>
          <Box component="pre" sx={{ m: 0, fontSize: '0.7rem' }}>
            {pretty}
          </Box>
        </Paper>
      </Collapse>
    </Box>
  )
}
