import {
  Stack,
  Chip,
  Typography,
  IconButton,
  Tooltip,
  Box,
} from '@mui/material'
import {
  Star as StarIcon,
  StarBorder as StarBorderIcon,
  DeleteSweep as ClearIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import type { HistoryEntry } from '../../hooks/useFhirQueryHistory'

interface QueryHistoryProps {
  recent: HistoryEntry[]
  favorites: HistoryEntry[]
  onSelect: (entry: HistoryEntry) => void
  onToggleFavorite: (id: string) => void
  onRemove: (id: string) => void
  onClearHistory: () => void
}

export default function QueryHistory({
  recent,
  favorites,
  onSelect,
  onToggleFavorite,
  onRemove,
  onClearHistory,
}: QueryHistoryProps) {
  const { t } = useTranslation('fhir')

  if (recent.length === 0 && favorites.length === 0) return null

  const formatLabel = (entry: HistoryEntry) => {
    const params = entry.params ? `?${entry.params}` : ''
    const label = `${entry.resourceType}${params}`
    return label.length > 40 ? label.substring(0, 37) + '...' : label
  }

  return (
    <Box sx={{ mb: 1 }}>
      {favorites.length > 0 && (
        <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap" sx={{ mb: 0.5 }}>
          <Typography variant="caption" color="text.secondary" sx={{ mr: 0.5 }}>
            {t('history.favorites')}
          </Typography>
          {favorites.map(entry => (
            <Chip
              key={entry.id}
              label={formatLabel(entry)}
              size="small"
              icon={<StarIcon sx={{ fontSize: 14, color: '#f59e0b !important' }} />}
              onClick={() => onSelect(entry)}
              onDelete={() => onToggleFavorite(entry.id)}
              deleteIcon={
                <Tooltip title={t('history.removeFromFavorites')}>
                  <StarIcon sx={{ fontSize: 14, color: '#f59e0b !important' }} />
                </Tooltip>
              }
              sx={{
                fontSize: '0.7rem',
                height: 24,
                bgcolor: 'rgba(245,158,11,0.08)',
                '&:hover': { bgcolor: 'rgba(245,158,11,0.15)' },
              }}
            />
          ))}
        </Stack>
      )}

      {recent.length > 0 && (
        <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap">
          <Typography variant="caption" color="text.secondary" sx={{ mr: 0.5 }}>
            {t('history.recent')}
          </Typography>
          {recent.map(entry => (
            <Chip
              key={entry.id}
              label={formatLabel(entry)}
              size="small"
              onClick={() => onSelect(entry)}
              onDelete={() => onRemove(entry.id)}
              sx={{ fontSize: '0.7rem', height: 24 }}
              icon={
                <Tooltip title={t('history.addToFavorites')}>
                  <IconButton
                    size="small"
                    onClick={(e) => { e.stopPropagation(); onToggleFavorite(entry.id) }}
                    sx={{ p: 0 }}
                    aria-label={t('history.toggleFavorite')}
                  >
                    <StarBorderIcon sx={{ fontSize: 14 }} />
                  </IconButton>
                </Tooltip>
              }
            />
          ))}
          <Tooltip title={t('history.clearHistory')}>
            <IconButton size="small" onClick={onClearHistory} sx={{ ml: 0.5 }} aria-label={t('history.clearHistory')}>
              <ClearIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
      )}
    </Box>
  )
}
