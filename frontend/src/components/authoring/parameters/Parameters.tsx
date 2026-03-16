import { useState, useMemo } from 'react'
import { useTranslation, Trans } from 'react-i18next'
import {
  Box, Stack, Typography, IconButton, Tooltip, TextField, Card, CardContent,
  MenuItem, Select, FormControl, InputLabel,
  Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon, Search as SearchIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import UcumUnitField from '../fields/UcumUnitField'
import ChooseCodeDialog from '../fields/ChooseCodeDialog'
import type { Parameter } from '../../../types/authoring'
import { generateId } from '../../../utils/validation'

interface ParametersProps {
  parameters: Parameter[]
  onChange: (parameters: Parameter[]) => void
}

export default function Parameters({ parameters, onChange }: ParametersProps) {
  const { t } = useTranslation('authoring')
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)
  const [codeDialogParamId, setCodeDialogParamId] = useState<string | null>(null)

  const PARAMETER_TYPES = useMemo(() => [
    { value: 'boolean', label: t('parameters.typeBoolean'), hint: t('parameters.typeBooleanHint') },
    { value: 'integer', label: t('parameters.typeInteger'), hint: t('parameters.typeIntegerHint') },
    { value: 'decimal', label: t('parameters.typeDecimal'), hint: t('parameters.typeDecimalHint') },
    { value: 'string', label: t('parameters.typeString'), hint: t('parameters.typeStringHint') },
    { value: 'datetime', label: t('parameters.typeDatetime'), hint: t('parameters.typeDatetimeHint') },
    { value: 'time', label: t('parameters.typeTime'), hint: t('parameters.typeTimeHint') },
    { value: 'code', label: t('parameters.typeCode'), hint: t('parameters.typeCodeHint') },
    { value: 'concept', label: t('parameters.typeConcept'), hint: t('parameters.typeConceptHint') },
    { value: 'quantity', label: t('parameters.typeQuantity'), hint: t('parameters.typeQuantityHint') },
    { value: 'interval<integer>', label: t('parameters.typeIntervalInt'), hint: t('parameters.typeIntervalIntHint') },
    { value: 'interval<datetime>', label: t('parameters.typeIntervalDt'), hint: t('parameters.typeIntervalDtHint') },
  ], [t])
  const pendingDeleteName = pendingDeleteId
    ? parameters.find((p) => p.uniqueId === pendingDeleteId)?.name || 'this parameter'
    : ''

  const handleAdd = () => {
    onChange([
      ...parameters,
      {
        uniqueId: generateId(),
        name: t('parameters.defaultName', { number: parameters.length + 1 }),
        type: 'boolean',
        value: undefined,
        comment: '',
      },
    ])
  }

  const handleRemove = (uniqueId: string) => {
    onChange(parameters.filter((p) => p.uniqueId !== uniqueId))
  }

  const handleUpdate = (uniqueId: string, updates: Partial<Parameter>) => {
    onChange(parameters.map((p) => (p.uniqueId === uniqueId ? { ...p, ...updates } : p)))
  }

  const getNameError = (param: Parameter): string | undefined => {
    if (!param.name.trim()) return t('parameters.nameRequired')
    if (parameters.some((p) => p.uniqueId !== param.uniqueId && p.name.trim() === param.name.trim()))
      return t('parameters.duplicateName')
    return undefined
  }

  const renderValueField = (param: Parameter) => {
    switch (param.type) {
      case 'boolean':
        return (
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>{t('parameters.defaultLabel')}</InputLabel>
            <Select
              value={param.value === true ? 'true' : param.value === false ? 'false' : ''}
              label={t('parameters.defaultLabel')}
              onChange={(e) => {
                const v = e.target.value
                handleUpdate(param.uniqueId, { value: v === 'true' ? true : v === 'false' ? false : undefined })
              }}
            >
              <MenuItem value="">{t('parameters.none')}</MenuItem>
              <MenuItem value="true">{t('parameters.true')}</MenuItem>
              <MenuItem value="false">{t('parameters.false')}</MenuItem>
            </Select>
          </FormControl>
        )
      case 'integer':
      case 'decimal':
        return (
          <TextField
            size="small"
            label={t('parameters.defaultValue')}
            type="number"
            value={param.value ?? ''}
            onChange={(e) => {
              const v = e.target.value
              handleUpdate(param.uniqueId, {
                value: v === '' ? undefined : param.type === 'integer' ? parseInt(v) : parseFloat(v),
              })
            }}
            sx={{ minWidth: 140 }}
          />
        )
      case 'string':
        return (
          <TextField
            size="small"
            label={t('parameters.defaultValue')}
            value={(param.value as string) || ''}
            onChange={(e) => handleUpdate(param.uniqueId, { value: e.target.value || undefined })}
            sx={{ minWidth: 200 }}
          />
        )
      case 'datetime':
        return (
          <TextField
            size="small"
            label={t('parameters.defaultValue')}
            type="datetime-local"
            value={(param.value as string) || ''}
            onChange={(e) => handleUpdate(param.uniqueId, { value: e.target.value || undefined })}
            sx={{ minWidth: 220 }}
            InputLabelProps={{ shrink: true }}
          />
        )
      case 'time':
        return (
          <TextField
            size="small"
            label={t('parameters.defaultValue')}
            type="time"
            value={(param.value as string) || ''}
            onChange={(e) => handleUpdate(param.uniqueId, { value: e.target.value || undefined })}
            sx={{ minWidth: 150 }}
            InputLabelProps={{ shrink: true }}
          />
        )
      case 'code': {
        const codeVal = (param.value as { system?: string; code?: string }) || {}
        return (
          <Stack direction="row" spacing={1} alignItems="center">
            <TextField
              size="small"
              label={t('parameters.codeSystemUri')}
              value={codeVal.system || ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...codeVal, system: e.target.value || undefined },
              })}
              sx={{ minWidth: 200 }}
              placeholder="http://loinc.org"
            />
            <TextField
              size="small"
              label={t('parameters.typeCode')}
              value={codeVal.code || ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...codeVal, code: e.target.value || undefined },
              })}
              sx={{ minWidth: 120 }}
              placeholder="12345-6"
            />
            <Tooltip title={t('parameters.searchCode')}>
              <IconButton size="small" onClick={() => setCodeDialogParamId(param.uniqueId)}>
                <SearchIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Stack>
        )
      }
      case 'concept': {
        const conceptVal = (param.value as { system?: string; code?: string; display?: string }) || {}
        return (
          <Stack direction="row" spacing={1}>
            <TextField
              size="small"
              label={t('parameters.display')}
              value={conceptVal.display || ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...conceptVal, display: e.target.value || undefined },
              })}
              sx={{ minWidth: 160 }}
              placeholder={t('parameters.displayPlaceholder')}
            />
            <TextField
              size="small"
              label={t('parameters.codeSystemUri')}
              value={conceptVal.system || ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...conceptVal, system: e.target.value || undefined },
              })}
              sx={{ minWidth: 180 }}
              placeholder="http://loinc.org"
            />
            <TextField
              size="small"
              label={t('parameters.typeCode')}
              value={conceptVal.code || ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...conceptVal, code: e.target.value || undefined },
              })}
              sx={{ minWidth: 100 }}
              placeholder="12345-6"
            />
          </Stack>
        )
      }
      case 'quantity': {
        const qtyVal = (param.value as { value?: number; unit?: string }) || {}
        return (
          <Stack direction="row" spacing={1}>
            <TextField
              size="small"
              label={t('parameters.value')}
              type="number"
              value={qtyVal.value ?? ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...qtyVal, value: e.target.value === '' ? undefined : parseFloat(e.target.value) },
              })}
              sx={{ width: 120 }}
            />
            <UcumUnitField
              label={t('parameters.unit')}
              value={qtyVal.unit || ''}
              onChange={(unit) => handleUpdate(param.uniqueId, {
                value: { ...qtyVal, unit: unit || undefined },
              })}
              sx={{ width: 200 }}
            />
          </Stack>
        )
      }
      case 'interval<integer>': {
        const intVal = (param.value as { low?: number; high?: number }) || {}
        return (
          <Stack direction="row" spacing={1} alignItems="center">
            <TextField
              size="small"
              label={t('parameters.intervalLow')}
              type="number"
              value={intVal.low ?? ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...intVal, low: e.target.value === '' ? undefined : parseInt(e.target.value) },
              })}
              sx={{ width: 120 }}
            />
            <Typography variant="body2" color="text.secondary">–</Typography>
            <TextField
              size="small"
              label={t('parameters.intervalHigh')}
              type="number"
              value={intVal.high ?? ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...intVal, high: e.target.value === '' ? undefined : parseInt(e.target.value) },
              })}
              sx={{ width: 120 }}
            />
          </Stack>
        )
      }
      case 'interval<datetime>': {
        const dtVal = (param.value as { low?: string; high?: string }) || {}
        return (
          <Stack direction="row" spacing={1} alignItems="center">
            <TextField
              size="small"
              label={t('parameters.intervalStart')}
              type="datetime-local"
              value={dtVal.low || ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...dtVal, low: e.target.value || undefined },
              })}
              sx={{ minWidth: 200 }}
              InputLabelProps={{ shrink: true }}
            />
            <Typography variant="body2" color="text.secondary">–</Typography>
            <TextField
              size="small"
              label={t('parameters.intervalEnd')}
              type="datetime-local"
              value={dtVal.high || ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...dtVal, high: e.target.value || undefined },
              })}
              sx={{ minWidth: 200 }}
              InputLabelProps={{ shrink: true }}
            />
          </Stack>
        )
      }
      default:
        return null
    }
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">{t('parameters.title')}</Typography>
        <GradientButton startIcon={<AddIcon />} onClick={handleAdd}>
          {t('parameters.add')}
        </GradientButton>
      </Stack>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('parameters.description')}
      </Typography>

      {parameters.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
          <Typography variant="body2">
            {t('parameters.emptyState')}
          </Typography>
        </Box>
      ) : (
        <Stack spacing={2}>
          {parameters.map((param) => {
            const nameError = getNameError(param)
            return (
            <Card key={param.uniqueId} variant="outlined">
              <CardContent>
                <Stack direction="row" alignItems="center" spacing={2}>
                  <TextField
                    value={param.name}
                    onChange={(e) => handleUpdate(param.uniqueId, { name: e.target.value })}
                    size="small"
                    label={t('parameters.nameLabel')}
                    sx={{ flex: 1 }}
                    error={!!nameError}
                    helperText={nameError}
                  />
                  <Tooltip title={PARAMETER_TYPES.find((t) => t.value === param.type)?.hint || ''} placement="top">
                    <FormControl size="small" sx={{ minWidth: 160 }}>
                      <InputLabel>{t('parameters.typeLabel')}</InputLabel>
                      <Select
                        value={param.type}
                        label={t('parameters.typeLabel')}
                        onChange={(e) => handleUpdate(param.uniqueId, { type: e.target.value, value: undefined })}
                      >
                        {PARAMETER_TYPES.map((t) => (
                          <MenuItem key={t.value} value={t.value}>
                            <Box>
                              <Typography variant="body2">{t.label}</Typography>
                              <Typography variant="caption" color="text.secondary">{t.hint}</Typography>
                            </Box>
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Tooltip>
                  {renderValueField(param)}
                  <Tooltip title={t('parameters.removeTooltip')}>
                    <IconButton size="small" color="error" onClick={() => setPendingDeleteId(param.uniqueId)} aria-label={t('parameters.removeTooltip')}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>
                <TextField
                  value={param.comment || ''}
                  onChange={(e) => handleUpdate(param.uniqueId, { comment: e.target.value })}
                  size="small"
                  label={t('parameters.commentLabel')}
                  fullWidth
                  sx={{ mt: 1.5 }}
                  placeholder={t('parameters.commentPlaceholder')}
                />
              </CardContent>
            </Card>
          )})}
        </Stack>
      )}

      <ChooseCodeDialog
        open={!!codeDialogParamId}
        onClose={() => setCodeDialogParamId(null)}
        onSelect={(codeRef) => {
          if (codeDialogParamId) {
            handleUpdate(codeDialogParamId, {
              value: { system: codeRef.codeSystem?.id, code: codeRef.code },
            })
          }
          setCodeDialogParamId(null)
        }}
      />

      <Dialog open={!!pendingDeleteId} onClose={() => setPendingDeleteId(null)}>
        <DialogTitle>{t('parameters.deleteTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            <Trans i18nKey="parameters.deleteConfirm" ns="authoring" values={{ name: pendingDeleteName }} components={{ strong: <strong /> }} />
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPendingDeleteId(null)}>{t('actions.cancel', { ns: 'common' })}</Button>
          <Button
            color="error"
            onClick={() => { if (pendingDeleteId) handleRemove(pendingDeleteId); setPendingDeleteId(null) }}
          >
            {t('actions.delete', { ns: 'common' })}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
