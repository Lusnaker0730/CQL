import { Tooltip, Typography, Box } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { ConjunctionKind } from '../../../types/authoring'
import { CONJ_CYCLE, conjColor } from '../../../utils/conjunctionTreeUtils'

interface ConjunctionConnectorProps {
  value: ConjunctionKind
  onChange: (newValue: ConjunctionKind) => void
}

export default function ConjunctionConnector({ value, onChange }: ConjunctionConnectorProps) {
  const { t } = useTranslation('authoring')
  const color = conjColor(value)
  const idx = CONJ_CYCLE.indexOf(value)
  const next = CONJ_CYCLE[(idx + 1) % CONJ_CYCLE.length]
  const tooltip = t(`conjunction.clickToChange${next}`)

  return (
    <Box sx={{ textAlign: 'center', py: 0.5 }}>
      <Tooltip title={tooltip} arrow>
        <Typography
          component="span"
          variant="caption"
          onClick={() => onChange(next)}
          sx={{
            px: 1.5,
            py: 0.25,
            borderRadius: 1,
            backgroundColor: color,
            color: 'white',
            fontWeight: 600,
            fontSize: '0.7rem',
            cursor: 'pointer',
            userSelect: 'none',
            transition: 'opacity 0.15s, transform 0.15s',
            display: 'inline-block',
            '&:hover': {
              opacity: 0.85,
              transform: 'scale(1.08)',
            },
          }}
        >
          {value.toUpperCase()}
        </Typography>
      </Tooltip>
    </Box>
  )
}
