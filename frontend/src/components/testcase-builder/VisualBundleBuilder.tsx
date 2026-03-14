import { Box, Paper, Divider, Typography, Stack, Chip } from '@mui/material'
import {
  Person as PersonIcon,
  LocalHospital as EncounterIcon,
  MedicalServices as ClinicalIcon,
  ArrowForward as ArrowIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import ResourceEntryList from './ResourceEntryList'
import AddResourceButton from './AddResourceButton'
import ResourceForm from './ResourceForm'
import { useBundleBuilder } from '../../contexts/BundleBuilderContext'

interface VisualBundleBuilderProps {
  onDirty: () => void
}

export default function VisualBundleBuilder({ onDirty }: VisualBundleBuilderProps) {
  const { t } = useTranslation('measures')
  const { state } = useBundleBuilder()

  return (
    <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
      <Box sx={{ display: 'flex', minHeight: 320 }}>
        {/* Left: Resource list */}
        <Box sx={{ width: 220, borderRight: 1, borderColor: 'divider', flexShrink: 0 }}>
          <Box sx={{ p: 1, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Typography variant="caption" fontWeight={600} color="text.secondary">
              {t('testCaseBuilder.bundleEntries')}
            </Typography>
            <AddResourceButton onDirty={onDirty} />
          </Box>
          <Divider />
          <ResourceEntryList onDirty={onDirty} />
        </Box>

        {/* Right: Resource form or guidance */}
        <Box sx={{ flex: 1, p: 2, overflow: 'auto', maxHeight: 500 }}>
          {state.activeEntryId ? (
            <ResourceForm onDirty={onDirty} />
          ) : (
            <Stack
              alignItems="center"
              justifyContent="center"
              spacing={2}
              sx={{ height: '100%', opacity: 0.7, py: 4 }}
            >
              {state.entries.length === 0 ? (
                <>
                  <Typography variant="subtitle2" color="text.secondary">
                    {t('testCaseBuilder.buildTestBundle')}
                  </Typography>
                  <Stack direction="row" alignItems="center" spacing={1} sx={{ flexWrap: 'wrap', justifyContent: 'center' }}>
                    <Chip icon={<PersonIcon />} label={t('testCaseBuilder.stepPatient')} size="small" variant="outlined" />
                    <ArrowIcon fontSize="small" color="disabled" />
                    <Chip icon={<EncounterIcon />} label={t('testCaseBuilder.stepEncounter')} size="small" variant="outlined" />
                    <ArrowIcon fontSize="small" color="disabled" />
                    <Chip icon={<ClinicalIcon />} label={t('testCaseBuilder.stepClinical')} size="small" variant="outlined" />
                  </Stack>
                  <Typography variant="caption" color="text.disabled" textAlign="center" maxWidth={300}>
                    {t('testCaseBuilder.startGuide')}
                  </Typography>
                </>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  {t('testCaseBuilder.selectResource')}
                </Typography>
              )}
            </Stack>
          )}
        </Box>
      </Box>
    </Paper>
  )
}
