import { Box, TextField, MenuItem, Stack, Typography, Divider } from '@mui/material'
import type { EcqmArtifact, EcqmArtifactRequest } from '../../types/ecqm'
import { SCORING_TYPES, POPULATION_BASIS_OPTIONS, IMPROVEMENT_NOTATIONS } from '../../constants/ecqmConstants'

interface Props {
  artifact: EcqmArtifact
  onChange: (updates: Partial<EcqmArtifactRequest>) => void
}

export default function EcqmSummaryTab({ artifact, onChange }: Props) {
  return (
    <Box sx={{ p: 3, maxWidth: 800 }}>
      <Typography variant="h6" gutterBottom>Measure Summary</Typography>
      <Stack spacing={2.5}>
        <TextField
          label="Name" required fullWidth
          value={artifact.name}
          onChange={(e) => onChange({ name: e.target.value })}
        />
        <Stack direction="row" spacing={2}>
          <TextField
            label="Version" sx={{ width: 150 }}
            value={artifact.version}
            onChange={(e) => onChange({ version: e.target.value })}
          />
          <TextField
            label="Status" select sx={{ width: 150 }}
            value={artifact.status}
            onChange={(e) => onChange({ status: e.target.value })}
          >
            <MenuItem value="draft">Draft</MenuItem>
            <MenuItem value="active">Active</MenuItem>
            <MenuItem value="retired">Retired</MenuItem>
          </TextField>
          <TextField
            label="Scoring Type" select sx={{ flex: 1 }}
            value={artifact.scoringType}
            onChange={(e) => onChange({ scoringType: e.target.value })}
          >
            {SCORING_TYPES.map((st) => (
              <MenuItem key={st.id} value={st.id}>{st.label}</MenuItem>
            ))}
          </TextField>
        </Stack>
        <Stack direction="row" spacing={2}>
          <TextField
            label="Population Basis" select sx={{ flex: 1 }}
            value={artifact.populationBasis}
            onChange={(e) => onChange({ populationBasis: e.target.value })}
          >
            {POPULATION_BASIS_OPTIONS.map((pb) => (
              <MenuItem key={pb.id} value={pb.id}>{pb.label}</MenuItem>
            ))}
          </TextField>
          <TextField
            label="Improvement Notation" select sx={{ flex: 1 }}
            value={artifact.improvementNotation}
            onChange={(e) => onChange({ improvementNotation: e.target.value })}
          >
            {IMPROVEMENT_NOTATIONS.map((n) => (
              <MenuItem key={n.id} value={n.id}>{n.label}</MenuItem>
            ))}
          </TextField>
        </Stack>
        <TextField
          label="Description" fullWidth multiline rows={3}
          value={artifact.description || ''}
          onChange={(e) => onChange({ description: e.target.value })}
        />

        <Divider />
        <Typography variant="subtitle1" fontWeight={600}>Identification</Typography>
        <Stack direction="row" spacing={2}>
          <TextField
            label="CMS Measure ID" sx={{ width: 200 }}
            value={artifact.cmsMeasureId || ''}
            onChange={(e) => onChange({ cmsMeasureId: e.target.value })}
          />
          <TextField
            label="NQF Number" sx={{ width: 200 }}
            value={artifact.nqfNumber || ''}
            onChange={(e) => onChange({ nqfNumber: e.target.value })}
          />
          <TextField
            label="Measure Set" sx={{ flex: 1 }}
            value={artifact.measureSet || ''}
            onChange={(e) => onChange({ measureSet: e.target.value })}
          />
        </Stack>

        <Divider />
        <Typography variant="subtitle1" fontWeight={600}>Metadata</Typography>
        <TextField
          label="URL" fullWidth
          value={artifact.url || ''}
          onChange={(e) => onChange({ url: e.target.value })}
        />
        <Stack direction="row" spacing={2}>
          <TextField
            label="Publisher" sx={{ flex: 1 }}
            value={artifact.publisher || ''}
            onChange={(e) => onChange({ publisher: e.target.value })}
          />
          <TextField
            label="Steward" sx={{ flex: 1 }}
            value={artifact.steward || ''}
            onChange={(e) => onChange({ steward: e.target.value })}
          />
        </Stack>
        <TextField
          label="Purpose" fullWidth multiline rows={2}
          value={artifact.purpose || ''}
          onChange={(e) => onChange({ purpose: e.target.value })}
        />
        <TextField
          label="Rationale" fullWidth multiline rows={2}
          value={artifact.rationale || ''}
          onChange={(e) => onChange({ rationale: e.target.value })}
        />
        <TextField
          label="Clinical Guidance" fullWidth multiline rows={2}
          value={artifact.clinicalGuidance || ''}
          onChange={(e) => onChange({ clinicalGuidance: e.target.value })}
        />
        <TextField
          label="Copyright" fullWidth multiline rows={2}
          value={artifact.copyright || ''}
          onChange={(e) => onChange({ copyright: e.target.value })}
        />
        <TextField
          label="Disclaimer" fullWidth multiline rows={2}
          value={artifact.disclaimer || ''}
          onChange={(e) => onChange({ disclaimer: e.target.value })}
        />
      </Stack>
    </Box>
  )
}
