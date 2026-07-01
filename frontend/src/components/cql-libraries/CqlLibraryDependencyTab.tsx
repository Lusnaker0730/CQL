import { useTranslation } from 'react-i18next'
import {
  Box, Chip, CircularProgress, Divider, Stack, Typography,
} from '@mui/material'
import { useLibraryDependencies, useLibraryDependents } from '../../hooks/useCql'
import SectionHeader from '../common/SectionHeader'

interface CqlLibraryDependencyTabProps {
  libraryId: string
  libraryName: string
}

export default function CqlLibraryDependencyTab({ libraryId, libraryName }: CqlLibraryDependencyTabProps) {
  const { t } = useTranslation('cqlLibraries')

  const { data: dependencies, isLoading: depsLoading } = useLibraryDependencies(libraryId)
  const { data: dependents, isLoading: dependentsLoading } = useLibraryDependents(libraryName)

  const isLoading = depsLoading || dependentsLoading

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress />
      </Box>
    )
  }

  return (
    <Box sx={{ maxWidth: 800 }}>
      <SectionHeader title={t('dependency.title')} />
      {/* Direct Dependencies */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle2" gutterBottom>{t('dependency.directDeps')}</Typography>
        {dependencies && dependencies.length > 0 ? (
          <Stack direction="row" spacing={0.5} useFlexGap sx={{
            flexWrap: "wrap"
          }}>
            {dependencies.map((dep) => (
              <Chip
                key={dep.id}
                label={`${dep.name} v${dep.version}`}
                size="small"
                variant="outlined"
                color="primary"
              />
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
      <Divider sx={{ my: 2 }} />
      {/* Dependents (who depends on this library) */}
      <Box>
        <Typography variant="subtitle2" gutterBottom>{t('dependency.dependents')}</Typography>
        {dependents && dependents.length > 0 ? (
          <Stack direction="row" spacing={0.5} useFlexGap sx={{
            flexWrap: "wrap"
          }}>
            {dependents.map((dep) => (
              <Chip
                key={dep.id}
                label={`${dep.name} v${dep.version}`}
                size="small"
                variant="outlined"
                color="secondary"
              />
            ))}
          </Stack>
        ) : (
          <Typography variant="body2" sx={{
            color: "text.secondary"
          }}>
            {t('dependency.noDependents')}
          </Typography>
        )}
      </Box>
    </Box>
  );
}
