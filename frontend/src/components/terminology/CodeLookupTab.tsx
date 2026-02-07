import { useState } from 'react'
import {
  Box,
  TextField,
  Stack,
  Typography,
  Button,
  CircularProgress,
  Alert,
  Chip,
  Paper,
} from '@mui/material'
import { Search as SearchIcon, ContentCopy as CopyIcon } from '@mui/icons-material'
import { useLookupCode } from '../../hooks/useTerminology'

const CODE_SYSTEMS = [
  { label: 'LOINC', url: 'http://loinc.org' },
  { label: 'SNOMED CT', url: 'http://snomed.info/sct' },
  { label: 'ICD-10-CM', url: 'http://hl7.org/fhir/sid/icd-10-cm' },
  { label: 'RxNorm', url: 'http://www.nlm.nih.gov/research/umls/rxnorm' },
  { label: 'CPT', url: 'http://www.ama-assn.org/go/cpt' },
]

export default function CodeLookupTab() {
  const [system, setSystem] = useState('')
  const [code, setCode] = useState('')
  const lookupMutation = useLookupCode()

  const handleLookup = () => {
    if (system && code) {
      lookupMutation.mutate({ system, code })
    }
  }

  const handleCopyCql = () => {
    if (!lookupMutation.data) return
    const d = lookupMutation.data
    const systemLabel = CODE_SYSTEMS.find((cs) => cs.url === d.system)?.label || d.system
    const cql = `code "${d.display}": '${d.code}' from "${systemLabel}"`
    navigator.clipboard.writeText(cql)
  }

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
          Quick select code system
        </Typography>
        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
          {CODE_SYSTEMS.map((cs) => (
            <Chip
              key={cs.url}
              label={cs.label}
              size="small"
              variant={system === cs.url ? 'filled' : 'outlined'}
              color={system === cs.url ? 'primary' : 'default'}
              onClick={() => setSystem(cs.url)}
              sx={{ cursor: 'pointer' }}
            />
          ))}
        </Stack>
      </Box>

      <TextField
        label="Code System URL"
        value={system}
        onChange={(e) => setSystem(e.target.value)}
        size="small"
        fullWidth
        placeholder="http://loinc.org"
      />

      <TextField
        label="Code"
        value={code}
        onChange={(e) => setCode(e.target.value)}
        size="small"
        fullWidth
        placeholder="e.g., 4548-4"
        onKeyDown={(e) => { if (e.key === 'Enter') handleLookup() }}
      />

      <Button
        variant="contained"
        onClick={handleLookup}
        disabled={lookupMutation.isPending || !system || !code}
        startIcon={lookupMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <SearchIcon />}
        sx={{
          background: 'linear-gradient(135deg, #0D7377 0%, #14A3A8 100%)',
          '&:hover': { background: 'linear-gradient(135deg, #095052 0%, #0D7377 100%)' },
          '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' },
        }}
      >
        {lookupMutation.isPending ? 'Looking up...' : 'Lookup Code'}
      </Button>

      {lookupMutation.isError && (
        <Alert severity="error">
          Lookup failed: {(lookupMutation.error as Error).message}
        </Alert>
      )}

      {lookupMutation.data && (
        <Paper
          elevation={0}
          sx={{
            p: 2,
            bgcolor: 'rgba(13,115,119,0.04)',
            border: '1px solid rgba(13,115,119,0.15)',
            borderRadius: '8px',
          }}
        >
          <Stack spacing={1.5}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="subtitle2" color="primary.dark">Code Details</Typography>
              <Button size="small" startIcon={<CopyIcon />} onClick={handleCopyCql}>
                Copy to CQL
              </Button>
            </Stack>

            <Box>
              <Typography variant="caption" color="text.secondary">System</Typography>
              <Typography variant="body2">{lookupMutation.data.system}</Typography>
            </Box>

            <Box>
              <Typography variant="caption" color="text.secondary">Code</Typography>
              <Typography variant="body2" fontWeight={600}>{lookupMutation.data.code}</Typography>
            </Box>

            <Box>
              <Typography variant="caption" color="text.secondary">Display</Typography>
              <Typography variant="body2">{lookupMutation.data.display}</Typography>
            </Box>

            {lookupMutation.data.name && (
              <Box>
                <Typography variant="caption" color="text.secondary">Name</Typography>
                <Typography variant="body2">{lookupMutation.data.name}</Typography>
              </Box>
            )}

            {lookupMutation.data.designations?.length > 0 && (
              <Box>
                <Typography variant="caption" color="text.secondary">Designations</Typography>
                <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
                  {lookupMutation.data.designations.map((d, i) => (
                    <Chip key={i} label={d} size="small" variant="outlined" />
                  ))}
                </Stack>
              </Box>
            )}
          </Stack>
        </Paper>
      )}
    </Stack>
  )
}
