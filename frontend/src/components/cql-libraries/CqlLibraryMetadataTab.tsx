import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Chip, Divider, Stack, TextField, Typography,
} from '@mui/material'
import type { CqlLibrary } from '../../types'
import StatusChip from '../common/StatusChip'
import SectionHeader from '../common/SectionHeader'

interface CqlLibraryMetadataTabProps {
  library: CqlLibrary
  onChange: (updates: Partial<CqlLibrary>) => void
  readOnly?: boolean
}

export default function CqlLibraryMetadataTab({ library, onChange, readOnly = false }: CqlLibraryMetadataTabProps) {
  const { t } = useTranslation('cqlLibraries')

  const formattedCreated = useMemo(
    () => library.createdAt ? new Date(library.createdAt).toLocaleString() : '-',
    [library.createdAt]
  )
  const formattedUpdated = useMemo(
    () => library.updatedAt ? new Date(library.updatedAt).toLocaleString() : '-',
    [library.updatedAt]
  )

  return (
    <Box sx={{ maxWidth: 800 }}>
      <SectionHeader title={t('workspace.tabs.metadata')} />
      <Stack spacing={2.5}>
        <TextField
          label={t('metadata.description')}
          fullWidth
          multiline
          rows={3}
          value={library.description || ''}
          onChange={(e) => onChange({ description: e.target.value })}
          disabled={readOnly}
        />

        <Divider />

        <Stack direction="row" spacing={3} sx={{
          alignItems: "center"
        }}>
          <Box>
            <Typography variant="caption" sx={{
              color: "text.secondary"
            }}>{t('metadata.status')}</Typography>
            <Box sx={{ mt: 0.5 }}>
              <StatusChip status={library.status} />
            </Box>
          </Box>
          <Box>
            <Typography variant="caption" sx={{
              color: "text.secondary"
            }}>{t('metadata.accessLevel')}</Typography>
            <Typography variant="body2" sx={{ mt: 0.5 }}>
              {library.accessLevel || '-'}
            </Typography>
          </Box>
          <Box>
            <Typography variant="caption" sx={{
              color: "text.secondary"
            }}>{t('metadata.owner')}</Typography>
            <Typography variant="body2" sx={{ mt: 0.5 }}>
              {library.ownerUsername || '-'}
            </Typography>
          </Box>
        </Stack>

        <Divider />

        <Box>
          <Typography variant="subtitle2" gutterBottom>{t('metadata.dependencies')}</Typography>
          {library.dependencies && library.dependencies.length > 0 ? (
            <Stack direction="row" spacing={0.5} useFlexGap sx={{
              flexWrap: "wrap"
            }}>
              {library.dependencies.map((dep) => (
                <Chip key={dep} label={dep} size="small" variant="outlined" />
              ))}
            </Stack>
          ) : (
            <Typography variant="body2" sx={{
              color: "text.secondary"
            }}>
              {t('dependency.noDeps')}
            </Typography>
          )}
        </Box>

        <Divider />

        <Stack direction="row" spacing={4}>
          <Box>
            <Typography variant="caption" sx={{
              color: "text.secondary"
            }}>{t('metadata.createdAt')}</Typography>
            <Typography variant="body2">{formattedCreated}</Typography>
          </Box>
          <Box>
            <Typography variant="caption" sx={{
              color: "text.secondary"
            }}>{t('metadata.updatedAt')}</Typography>
            <Typography variant="body2">{formattedUpdated}</Typography>
          </Box>
        </Stack>
      </Stack>
    </Box>
  );
}
