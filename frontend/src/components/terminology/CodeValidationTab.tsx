import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  TextField,
  Stack,
  CircularProgress,
  Alert,
} from '@mui/material'
import { CheckCircle as ValidateIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useValidateCode } from '../../hooks/useTerminology'

export default function CodeValidationTab() {
  const { t } = useTranslation('terminology')
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
        label={t('codeValidation.systemLabel')}
        value={system}
        onChange={(e) => setSystem(e.target.value)}
        size="small"
        fullWidth
        placeholder={t('codeValidation.systemPlaceholder')}
      />

      <TextField
        label={t('codeValidation.codeLabel')}
        value={code}
        onChange={(e) => setCode(e.target.value)}
        size="small"
        fullWidth
        placeholder={t('codeValidation.codePlaceholder')}
      />

      <TextField
        label={t('codeValidation.valueSetUrlLabel')}
        value={valueSetUrl}
        onChange={(e) => setValueSetUrl(e.target.value)}
        size="small"
        fullWidth
        placeholder={t('codeValidation.valueSetUrlPlaceholder')}
      />

      <GradientButton
        onClick={handleValidate}
        disabled={validateMutation.isPending || !system || !code || !valueSetUrl}
        startIcon={validateMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <ValidateIcon />}
        sx={{
          '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' },
        }}
      >
        {validateMutation.isPending ? t('codeValidation.validating') : t('codeValidation.validateCode')}
      </GradientButton>

      {validateMutation.isError && (
        <Alert severity="error">
          {t('codeValidation.validationFailed', { error: (validateMutation.error as Error).message })}
        </Alert>
      )}

      {validateMutation.data && (
        <Alert
          severity={validateMutation.data.result ? 'success' : 'error'}
          variant="filled"
        >
          {validateMutation.data.result
            ? t('codeValidation.codeValid', { code: validateMutation.data.code })
            : t('codeValidation.codeInvalid', { code: validateMutation.data.code })}
        </Alert>
      )}
    </Stack>
  )
}
