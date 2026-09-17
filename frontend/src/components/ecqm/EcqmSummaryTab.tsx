import { useTranslation } from 'react-i18next'
import { Box, TextField, MenuItem, Stack, Typography, Divider } from '@mui/material'
import type { EcqmArtifact, EcqmArtifactRequest } from '../../types/ecqm'
import { SCORING_TYPES, POPULATION_BASIS_OPTIONS, IMPROVEMENT_NOTATIONS } from '../../constants/ecqmConstants'

interface Props {
  artifact: EcqmArtifact
  onChange: (updates: Partial<EcqmArtifactRequest>) => void
}

export default function EcqmSummaryTab({ artifact, onChange }: Props) {
  const { t } = useTranslation('ecqm')
  return (
    <Box sx={{ p: 3, maxWidth: 800 }}>
      <Typography variant="h6" gutterBottom>{t('summary.title')}</Typography>
      <Stack spacing={2.5}>
        <TextField
          label={t('summary.name')} required fullWidth
          value={artifact.name}
          onChange={(e) => onChange({ name: e.target.value })}
        />
        <Stack direction="row" spacing={2}>
          <TextField
            label={t('summary.version')} sx={{ width: 150 }}
            value={artifact.version}
            onChange={(e) => onChange({ version: e.target.value })}
          />
          <TextField
            label={t('summary.status')} select sx={{ width: 150 }}
            value={artifact.status}
            onChange={(e) => onChange({ status: e.target.value })}
          >
            <MenuItem value="draft">{t('summary.statusDraft')}</MenuItem>
            <MenuItem value="active">{t('summary.statusActive')}</MenuItem>
            <MenuItem value="retired">{t('summary.statusRetired')}</MenuItem>
          </TextField>
          <TextField
            label={t('summary.scoringType')} select sx={{ flex: 1 }}
            value={artifact.scoringType}
            onChange={(e) => onChange({ scoringType: e.target.value })}
          >
            {SCORING_TYPES.map((st) => (
              <MenuItem key={st.id} value={st.id}>{t(`scoring.${st.id === 'continuous-variable' ? 'continuousVariable' : st.id}`)}</MenuItem>
            ))}
          </TextField>
        </Stack>
        <Stack direction="row" spacing={2}>
          <TextField
            label={t('summary.populationBasis')} select sx={{ flex: 1 }}
            value={artifact.populationBasis}
            onChange={(e) => onChange({ populationBasis: e.target.value })}
          >
            {POPULATION_BASIS_OPTIONS.map((pb) => (
              <MenuItem key={pb.id} value={pb.id}>{t(`populationBasisOptions.${pb.id}`)}</MenuItem>
            ))}
          </TextField>
          <TextField
            label={t('summary.improvementNotation')} select sx={{ flex: 1 }}
            value={artifact.improvementNotation}
            onChange={(e) => onChange({ improvementNotation: e.target.value })}
          >
            {IMPROVEMENT_NOTATIONS.map((n) => (
              <MenuItem key={n.id} value={n.id}>{t(`improvementOptions.${n.id}`)}</MenuItem>
            ))}
          </TextField>
        </Stack>
        <TextField
          label={t('summary.description')} fullWidth multiline rows={3}
          value={artifact.description || ''}
          onChange={(e) => onChange({ description: e.target.value })}
        />

        <Divider />
        <Typography variant="subtitle1" sx={{
          fontWeight: 600
        }}>{t('summary.identification')}</Typography>
        <Stack direction="row" spacing={2}>
          <TextField
            label={t('summary.cmsMeasureId')} sx={{ width: 200 }}
            value={artifact.cmsMeasureId || ''}
            onChange={(e) => onChange({ cmsMeasureId: e.target.value })}
          />
          <TextField
            label={t('summary.nqfNumber')} sx={{ width: 200 }}
            value={artifact.nqfNumber || ''}
            onChange={(e) => onChange({ nqfNumber: e.target.value })}
          />
          <TextField
            label={t('summary.measureSet')} sx={{ flex: 1 }}
            value={artifact.measureSet || ''}
            onChange={(e) => onChange({ measureSet: e.target.value })}
          />
        </Stack>

        <Divider />
        <Typography variant="subtitle1" sx={{
          fontWeight: 600
        }}>{t('summary.metadata')}</Typography>
        <TextField
          label={t('summary.url')} fullWidth
          value={artifact.url || ''}
          onChange={(e) => onChange({ url: e.target.value })}
        />
        <Stack direction="row" spacing={2}>
          <TextField
            label={t('summary.publisher')} sx={{ flex: 1 }}
            value={artifact.publisher || ''}
            onChange={(e) => onChange({ publisher: e.target.value })}
          />
          <TextField
            label={t('summary.steward')} sx={{ flex: 1 }}
            value={artifact.steward || ''}
            onChange={(e) => onChange({ steward: e.target.value })}
          />
        </Stack>
        <TextField
          label={t('summary.purpose')} fullWidth multiline rows={2}
          value={artifact.purpose || ''}
          onChange={(e) => onChange({ purpose: e.target.value })}
        />
        <TextField
          label={t('summary.rationale')} fullWidth multiline rows={2}
          value={artifact.rationale || ''}
          onChange={(e) => onChange({ rationale: e.target.value })}
        />
        <TextField
          label={t('summary.clinicalGuidance')} fullWidth multiline rows={2}
          value={artifact.clinicalGuidance || ''}
          onChange={(e) => onChange({ clinicalGuidance: e.target.value })}
        />
        <TextField
          label={t('summary.copyright')} fullWidth multiline rows={2}
          value={artifact.copyright || ''}
          onChange={(e) => onChange({ copyright: e.target.value })}
        />
        <TextField
          label={t('summary.disclaimer')} fullWidth multiline rows={2}
          value={artifact.disclaimer || ''}
          onChange={(e) => onChange({ disclaimer: e.target.value })}
        />
      </Stack>
    </Box>
  );
}
