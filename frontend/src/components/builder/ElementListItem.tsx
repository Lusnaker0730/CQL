import { Box, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import {
  Edit as EditIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material'

interface ElementListItemProps {
  label: string
  secondaryLabel?: string
  onGoTo: () => void
  onEdit: () => void
  onDelete: () => void
}

export default function ElementListItem({
  label,
  secondaryLabel,
  onGoTo,
  onEdit,
  onDelete,
}: ElementListItemProps) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        py: 0.25,
        px: 0.5,
        borderRadius: 0.5,
        '&:hover': { bgcolor: 'rgba(13,115,119,0.04)' },
        '&:hover .element-actions': { opacity: 1 },
      }}
    >
      <Box
        sx={{ flex: 1, minWidth: 0, cursor: 'pointer' }}
        onClick={onGoTo}
      >
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
        {secondaryLabel && (
          <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.65rem' }}>
            {secondaryLabel}
          </Typography>
        )}
      </Box>
      <Stack
        className="element-actions"
        direction="row"
        spacing={0}
        sx={{ opacity: 0, transition: 'opacity 0.15s', flexShrink: 0 }}
      >
        <Tooltip title="Go to definition">
          <span>
            <IconButton size="small" onClick={onEdit} sx={{ p: 0.25 }} aria-label="Go to definition">
              <EditIcon sx={{ fontSize: 16 }} />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="Delete">
          <span>
            <IconButton size="small" onClick={onDelete} sx={{ p: 0.25, color: 'error.main' }} aria-label="Delete">
              <DeleteIcon sx={{ fontSize: 16 }} />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>
    </Box>
  )
}
