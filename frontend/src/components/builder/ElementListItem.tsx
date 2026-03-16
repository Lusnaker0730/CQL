import { memo } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import { alpha } from '@mui/material/styles'
import {
  Edit as EditIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material'
import TypeChip from './TypeChip'

interface ElementListItemProps {
  label: string
  secondaryLabel?: string
  onGoTo: () => void
  onEdit: () => void
  onDelete: () => void
}

const ElementListItem = memo(function ElementListItem({
  label,
  secondaryLabel,
  onGoTo,
  onEdit,
  onDelete,
}: ElementListItemProps) {
  const { t } = useTranslation('builder')
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        py: 0.5,
        px: 0.5,
        borderRadius: 0.5,
        '&:hover': { bgcolor: (theme) => alpha(theme.palette.primary.main, 0.04) },
        '&:hover .element-actions': { opacity: 1 },
      }}
    >
      <Box
        sx={{ flex: 1, minWidth: 0, cursor: 'pointer' }}
        onClick={onGoTo}
      >
        <Stack direction="row" spacing={0.5} alignItems="center">
          <Typography
            variant="body2"
            sx={{
              fontFamily: 'monospace',
              fontSize: '0.8rem',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              '&:hover': { color: 'primary.main', textDecoration: 'underline' },
            }}
          >
            {label}
          </Typography>
          <TypeChip resultType={secondaryLabel} />
        </Stack>
      </Box>
      <Stack
        className="element-actions"
        direction="row"
        spacing={0}
        sx={{ opacity: 0, transition: 'opacity 0.15s', flexShrink: 0 }}
      >
        <Tooltip title={t('common.goToDefinition')}>
          <span>
            <IconButton size="small" onClick={onEdit} sx={{ p: 0.25 }} aria-label={t('common.goToDefinition')}>
              <EditIcon sx={{ fontSize: 16 }} />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title={t('common.delete')}>
          <span>
            <IconButton size="small" onClick={onDelete} sx={{ p: 0.25, color: 'error.main' }} aria-label={t('common.delete')}>
              <DeleteIcon sx={{ fontSize: 16 }} />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>
    </Box>
  )
})

export default ElementListItem
