import { useState } from 'react'
import {
  TextField,
  Stack,
  Button,
  CircularProgress,
  Alert,
} from '@mui/material'
import { CheckCircle as ValidateIcon } from '@mui/icons-material'
import { useValidateCode } from '../../hooks/useTerminology'

export default function CodeValidationTab() {
  const [system, setSystem] = useState('')
  const [code, setCode] = useState('')
  const [valueSetUrl, setValueSetUrl] = useState('')
  const validateMutation = useValidateCode()

  const handleValidate = () => {
    if (system && code && valueSetUrl) {
      validateMutation.mutate({ system, code, valueSetUrl })
    }
  }

  return (
    <Stack spacing={2}>
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
      />

      <TextField
        label="ValueSet URL"
        value={valueSetUrl}
        onChange={(e) => setValueSetUrl(e.target.value)}
        size="small"
        fullWidth
        placeholder="http://cts.nlm.nih.gov/fhir/ValueSet/..."
      />

      <Button
        variant="contained"
        onClick={handleValidate}
        disabled={validateMutation.isPending || !system || !code || !valueSetUrl}
        startIcon={validateMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <ValidateIcon />}
        sx={{
          background: 'linear-gradient(135deg, #0D7377 0%, #14A3A8 100%)',
          '&:hover': { background: 'linear-gradient(135deg, #095052 0%, #0D7377 100%)' },
          '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' },
        }}
      >
        {validateMutation.isPending ? 'Validating...' : 'Validate Code'}
      </Button>

      {validateMutation.isError && (
        <Alert severity="error">
          Validation failed: {(validateMutation.error as Error).message}
        </Alert>
      )}

      {validateMutation.data && (
        <Alert
          severity={validateMutation.data.result ? 'success' : 'error'}
          variant="filled"
        >
          {validateMutation.data.result
            ? `Code "${validateMutation.data.code}" is valid in the specified ValueSet.`
            : `Code "${validateMutation.data.code}" is NOT valid in the specified ValueSet.`}
        </Alert>
      )}
    </Stack>
  )
}
