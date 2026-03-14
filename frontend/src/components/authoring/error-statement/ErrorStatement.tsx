import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Stack, Typography, IconButton, Tooltip, TextField, Card, CardContent,
  MenuItem, Select, FormControl, InputLabel,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import type { ErrorStatement as ErrorStatementType, IfThenClause } from '../../../types/authoring'

interface ErrorStatementProps {
  errorStatement: ErrorStatementType | undefined
  onChange: (errorStatement: ErrorStatementType) => void
}

export default function ErrorStatementEditor({ errorStatement, onChange }: ErrorStatementProps) {
  const { t } = useTranslation('authoring')
  const clauses = errorStatement?.ifThenClauses || []

  const CONDITION_OPTIONS = [
    { label: t('errorStatement.condNull'), value: 'null' },
    { label: t('errorStatement.condNoInclusion'), value: 'doesnt_meet_inclusion' },
    { label: t('errorStatement.condMeetsExclusion'), value: 'meets_exclusion' },
    { label: t('errorStatement.condErrors'), value: 'errors' },
  ]
  const elseClause = errorStatement?.elseClause || ''

  const updateClauses = useCallback(
    (newClauses: IfThenClause[], newElse?: string) => {
      onChange({
        ifThenClauses: newClauses,
        elseClause: newElse !== undefined ? newElse : elseClause,
      })
    },
    [elseClause, onChange]
  )

  const handleAddClause = () => {
    updateClauses([
      ...clauses,
      {
        ifCondition: { label: t('errorStatement.condNull'), value: 'null' },
        statements: [],
        thenClause: '',
      },
    ])
  }

  const handleRemoveClause = (index: number) => {
    updateClauses(clauses.filter((_, i) => i !== index))
  }

  const handleUpdateClause = (index: number, updates: Partial<IfThenClause>) => {
    updateClauses(clauses.map((c, i) => (i === index ? { ...c, ...updates } : c)))
  }

  const handleElseChange = (value: string) => {
    onChange({
      ifThenClauses: clauses,
      elseClause: value,
    })
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">{t('errorStatement.title')}</Typography>
        <GradientButton startIcon={<AddIcon />} onClick={handleAddClause}>
          {t('errorStatement.add')}
        </GradientButton>
      </Stack>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('errorStatement.description')}
      </Typography>

      {clauses.length === 0 && !elseClause ? (
        <Box sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
          <Typography variant="body2">
            {t('errorStatement.emptyState')}
          </Typography>
        </Box>
      ) : (
        <Stack spacing={2}>
          {clauses.map((clause, index) => (
            <Card key={`${clause.ifCondition.value}-${index}`} variant="outlined">
              <CardContent>
                <Stack direction="row" alignItems="center" spacing={1} mb={2}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'warning.main' }}>
                    {index === 0 ? t('errorStatement.if') : t('errorStatement.elseIf')}
                  </Typography>
                  <Box sx={{ flex: 1 }} />
                  <Tooltip title={t('errorStatement.removeCondition')}>
                    <IconButton size="small" color="error" onClick={() => handleRemoveClause(index)} aria-label={t('errorStatement.removeCondition')}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>

                <FormControl size="small" fullWidth sx={{ mb: 2 }}>
                  <InputLabel>{t('errorStatement.conditionLabel')}</InputLabel>
                  <Select
                    value={clause.ifCondition.value}
                    label={t('errorStatement.conditionLabel')}
                    onChange={(e) => {
                      const opt = CONDITION_OPTIONS.find((o) => o.value === e.target.value)
                      if (opt) {
                        handleUpdateClause(index, {
                          ifCondition: { label: opt.label, value: opt.value },
                        })
                      }
                    }}
                  >
                    {CONDITION_OPTIONS.map((opt) => (
                      <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <TextField
                  label={t('errorStatement.thenDisplay')}
                  value={clause.thenClause}
                  onChange={(e) => handleUpdateClause(index, { thenClause: e.target.value })}
                  fullWidth
                  multiline
                  minRows={2}
                  size="small"
                  placeholder={t('errorStatement.thenPlaceholder')}
                />
              </CardContent>
            </Card>
          ))}

          <Card variant="outlined" sx={{ borderStyle: 'dashed' }}>
            <CardContent>
              <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                {t('errorStatement.elseDefault')}
              </Typography>
              <TextField
                label={t('errorStatement.defaultMessage')}
                value={elseClause}
                onChange={(e) => handleElseChange(e.target.value)}
                fullWidth
                multiline
                minRows={2}
                size="small"
                placeholder={t('errorStatement.defaultPlaceholder')}
              />
            </CardContent>
          </Card>
        </Stack>
      )}
    </Box>
  )
}
