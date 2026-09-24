import { useTranslation } from 'react-i18next'
import { Box, Chip, MenuItem, Stack, TextField, Typography } from '@mui/material'
import type { FunctionArgument, FunctionArgumentMode, LiteralType } from '../../../types/authoring'
import { useArtifactScope } from '../../../contexts/ArtifactScopeContext'
import { LITERAL_TYPES, validateFunctionArguments } from '../../../utils/libraryFunctions'

interface Props {
  value: FunctionArgument[]
  onChange: (next: FunctionArgument[]) => void
}

/**
 * PAT-237 — the `arguments` field of a library function call: one row per declared operand,
 * each choosing where its value comes from (a base element, an artifact parameter, a typed
 * literal, the Patient, or the Measurement Period in an eCQM). Problems are shown inline with
 * the same rules the backend applies before it emits the call.
 */
export default function FunctionArgumentsEditor({ value, onChange }: Props) {
  const { t } = useTranslation('authoring')
  const scope = useArtifactScope()
  const errors = validateFunctionArguments(value, scope.hasMeasurementPeriod)

  const modes: FunctionArgumentMode[] = scope.hasMeasurementPeriod
    ? ['element', 'parameter', 'literal', 'patient', 'measurementPeriod']
    : ['element', 'parameter', 'literal', 'patient']

  const update = (index: number, patch: Partial<FunctionArgument>) => {
    onChange(value.map((arg, i) => (i === index ? { ...arg, ...patch } : arg)))
  }

  if (value.length === 0) {
    return (
      <Typography variant="caption" sx={{ color: 'text.secondary' }}>{t('functionCall.noArguments')}</Typography>
    )
  }

  return (
    <Stack spacing={1.5} data-testid="function-arguments">
      {value.map((arg, i) => {
        const error = errors[i]
        return (
          <Box key={`${arg.name}-${i}`} sx={{ p: 1, border: 1, borderColor: error ? 'error.main' : 'divider', borderRadius: 1 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>{arg.name}</Typography>
              {arg.type && <Chip size="small" label={arg.type} sx={{ height: 20, fontSize: '0.7rem' }} />}
            </Stack>
            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
              <TextField
                select
                size="small"
                label={t('functionCall.source')}
                value={arg.mode}
                onChange={(e) => update(i, { mode: e.target.value as FunctionArgumentMode, operand_id: undefined })}
                sx={{ minWidth: 190 }}
                slotProps={{ htmlInput: { 'aria-label': `${arg.name} source` } }}
              >
                {modes.map((m) => (
                  <MenuItem key={m} value={m}>{t(`functionCall.modes.${m}`)}</MenuItem>
                ))}
              </TextField>

              {arg.mode === 'element' && (
                <TextField
                  select
                  size="small"
                  label={t('functionCall.baseElement')}
                  value={arg.operand_id ?? ''}
                  onChange={(e) => update(i, { operand_id: e.target.value || undefined })}
                  sx={{ minWidth: 220 }}
                  slotProps={{ htmlInput: { 'aria-label': `${arg.name} base element` } }}
                  helperText={scope.baseElements.length === 0 ? t('functionCall.noBaseElements') : undefined}
                >
                  {scope.baseElements.map((be) => (
                    <MenuItem key={be.uniqueId} value={be.uniqueId}>
                      {be.name}{be.returnType ? ` (${be.returnType.replace(/_/g, ' ')})` : ''}
                    </MenuItem>
                  ))}
                </TextField>
              )}

              {arg.mode === 'parameter' && (
                <TextField
                  select
                  size="small"
                  label={t('functionCall.parameter')}
                  value={arg.operand_id ?? ''}
                  onChange={(e) => update(i, { operand_id: e.target.value || undefined })}
                  sx={{ minWidth: 220 }}
                  slotProps={{ htmlInput: { 'aria-label': `${arg.name} parameter` } }}
                  helperText={scope.parameters.length === 0 ? t('functionCall.noParameters') : undefined}
                >
                  {scope.parameters.map((p) => (
                    <MenuItem key={p.uniqueId} value={p.uniqueId}>{p.name}{p.type ? ` (${p.type})` : ''}</MenuItem>
                  ))}
                </TextField>
              )}

              {arg.mode === 'literal' && (
                <>
                  <TextField
                    select
                    size="small"
                    label={t('functionCall.literalType')}
                    value={arg.literal_type ?? ''}
                    onChange={(e) => update(i, { literal_type: e.target.value as LiteralType, literal_value: e.target.value === 'Boolean' ? 'true' : '' })}
                    sx={{ minWidth: 130 }}
                    slotProps={{ htmlInput: { 'aria-label': `${arg.name} literal type` } }}
                  >
                    {LITERAL_TYPES.map((lt) => (
                      <MenuItem key={lt} value={lt}>{lt}</MenuItem>
                    ))}
                  </TextField>
                  {arg.literal_type === 'Boolean' ? (
                    <TextField
                      select
                      size="small"
                      label={t('functionCall.value')}
                      value={arg.literal_value ?? 'true'}
                      onChange={(e) => update(i, { literal_value: e.target.value })}
                      sx={{ minWidth: 120 }}
                      slotProps={{ htmlInput: { 'aria-label': `${arg.name} value` } }}
                    >
                      <MenuItem value="true">true</MenuItem>
                      <MenuItem value="false">false</MenuItem>
                    </TextField>
                  ) : (
                    <TextField
                      size="small"
                      label={t('functionCall.value')}
                      type={arg.literal_type === 'Date' ? 'date' : arg.literal_type === 'DateTime' ? 'datetime-local' : 'text'}
                      value={arg.literal_value ?? ''}
                      onChange={(e) => update(i, { literal_value: e.target.value })}
                      error={error === 'invalidLiteral'}
                      placeholder={t(`functionCall.placeholders.${arg.literal_type ?? 'String'}`)}
                      sx={{ minWidth: 200 }}
                      slotProps={{
                        inputLabel: arg.literal_type === 'Date' || arg.literal_type === 'DateTime' ? { shrink: true } : undefined,
                        htmlInput: { 'aria-label': `${arg.name} value` },
                      }}
                    />
                  )}
                  {arg.literal_type === 'Quantity' && (
                    <TextField
                      size="small"
                      label={t('functionCall.unit')}
                      value={arg.literal_unit ?? ''}
                      onChange={(e) => update(i, { literal_unit: e.target.value })}
                      error={error === 'invalidUnit'}
                      placeholder="mg/dL"
                      sx={{ minWidth: 120 }}
                      slotProps={{ htmlInput: { 'aria-label': `${arg.name} unit` } }}
                    />
                  )}
                </>
              )}

              {arg.mode === 'patient' && (
                <Typography variant="body2" sx={{ alignSelf: 'center', color: 'text.secondary' }}>Patient</Typography>
              )}
              {arg.mode === 'measurementPeriod' && (
                <Typography variant="body2" sx={{ alignSelf: 'center', color: 'text.secondary' }}>"Measurement Period"</Typography>
              )}
            </Stack>
            {error && (
              <Typography variant="caption" color="error" role="alert" sx={{ display: 'block', mt: 0.5 }}>
                {t(`functionCall.errors.${error}`)}
              </Typography>
            )}
          </Box>
        )
      })}
    </Stack>
  )
}
