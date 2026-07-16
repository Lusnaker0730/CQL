import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { Alert, Box, Button, Checkbox, FormControlLabel, Paper, Stack, Typography } from '@mui/material'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import { ehrApi } from '../../api/ehrApi'
import type { FhirBundleImportResult } from '../../types'

/**
 * PAT-206 — upload a FHIR bundle file (e.g. a 健康存摺 / My Health Bank export) and import it
 * as a patient without needing a live EHR connection. Since 健康存摺 already exports FHIR/JSON,
 * this is the plug-and-play ingress for clinics that can hand us a file but have no FHIR endpoint.
 */
export default function FhirBundleUpload() {
  const { t } = useTranslation('fhir')
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [validate, setValidate] = useState(false)

  const mutation = useMutation<FhirBundleImportResult, Error, File>({
    mutationFn: (f) => ehrApi.importFhirBundle(f, undefined, validate),
  })

  const handlePick = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0] ?? null
    setFile(f)
    mutation.reset()
  }

  return (
    <Paper variant="outlined" sx={{ p: 2.5, mb: 2 }}>
      <Stack spacing={1.5}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          {t('bundleUpload.title')}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t('bundleUpload.description')}
        </Typography>

        <Box>
          <input
            ref={inputRef}
            type="file"
            accept="application/json,.json"
            hidden
            onChange={handlePick}
          />
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Button
              variant="outlined"
              startIcon={<UploadFileIcon />}
              onClick={() => inputRef.current?.click()}
            >
              {t('bundleUpload.chooseFile')}
            </Button>
            {file && (
              <Typography variant="body2" noWrap sx={{ maxWidth: 320 }} title={file.name}>
                {file.name}
              </Typography>
            )}
            <Button
              variant="contained"
              disabled={!file || mutation.isPending}
              onClick={() => file && mutation.mutate(file)}
            >
              {mutation.isPending ? t('bundleUpload.importing') : t('bundleUpload.import')}
            </Button>
          </Stack>
          <FormControlLabel
            sx={{ mt: 0.5 }}
            control={
              <Checkbox
                size="small"
                checked={validate}
                onChange={(e) => setValidate(e.target.checked)}
              />
            }
            label={
              <Typography variant="body2" color="text.secondary">
                {t('bundleUpload.validate')}
              </Typography>
            }
          />
        </Box>

        {mutation.isError && (
          <Alert severity="error">
            {t('bundleUpload.error', {
              message: (mutation.error as { message?: string })?.message ?? '',
            })}
          </Alert>
        )}
        {mutation.isSuccess && (
          <Alert severity={mutation.data.invalidResources ? 'warning' : 'success'}>
            {t('bundleUpload.success', {
              name: mutation.data.patientImport.patientName ?? '—',
              count: mutation.data.patientImport.resourceCount ?? 0,
            })}
            {mutation.data.validated && (
              <>
                {' '}
                {t('bundleUpload.conformance', {
                  valid: mutation.data.validResources ?? 0,
                  total: mutation.data.totalResources ?? 0,
                })}
              </>
            )}
          </Alert>
        )}
      </Stack>
    </Paper>
  )
}
