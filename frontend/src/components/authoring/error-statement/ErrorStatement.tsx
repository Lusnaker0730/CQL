import { useCallback } from 'react'
import {
  Box, Stack, Typography, IconButton, Tooltip, TextField, Card, CardContent,
  MenuItem, Select, FormControl, InputLabel,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import type { ErrorStatement as ErrorStatementType, IfThenClause } from '../../../types/authoring'

const CONDITION_OPTIONS = [
  { label: 'Recommendations is null', value: 'null' },
  { label: "Doesn't Meet Inclusion Criteria", value: 'doesnt_meet_inclusion' },
  { label: 'Meets Exclusion Criteria', value: 'meets_exclusion' },
  { label: 'Errors were encountered', value: 'errors' },
]

interface ErrorStatementProps {
  errorStatement: ErrorStatementType | undefined
  onChange: (errorStatement: ErrorStatementType) => void
}

export default function ErrorStatementEditor({ errorStatement, onChange }: ErrorStatementProps) {
  const clauses = errorStatement?.ifThenClauses || []
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
        ifCondition: { label: 'Recommendations is null', value: 'null' },
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
        <Typography variant="h6">Error Handling</Typography>
        <GradientButton startIcon={<AddIcon />} onClick={handleAddClause}>
          Add Condition
        </GradientButton>
      </Stack>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Define messages to display when the artifact encounters null values or errors during execution.
      </Typography>

      {clauses.length === 0 && !elseClause ? (
        <Box sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
          <Typography variant="body2">
            No error conditions defined. Add conditions to handle null or erroneous data gracefully.
          </Typography>
        </Box>
      ) : (
        <Stack spacing={2}>
          {clauses.map((clause, index) => (
            <Card key={`${clause.ifCondition.value}-${index}`} variant="outlined">
              <CardContent>
                <Stack direction="row" alignItems="center" spacing={1} mb={2}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'warning.main' }}>
                    {index === 0 ? 'If' : 'Else If'}
                  </Typography>
                  <Box sx={{ flex: 1 }} />
                  <Tooltip title="Remove condition">
                    <IconButton size="small" color="error" onClick={() => handleRemoveClause(index)} aria-label="Remove condition">
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>

                <FormControl size="small" fullWidth sx={{ mb: 2 }}>
                  <InputLabel>Condition</InputLabel>
                  <Select
                    value={clause.ifCondition.value}
                    label="Condition"
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
                  label="Then display"
                  value={clause.thenClause}
                  onChange={(e) => handleUpdateClause(index, { thenClause: e.target.value })}
                  fullWidth
                  multiline
                  minRows={2}
                  size="small"
                  placeholder="Enter the error message to display when this condition is true..."
                />
              </CardContent>
            </Card>
          ))}

          <Card variant="outlined" sx={{ borderStyle: 'dashed' }}>
            <CardContent>
              <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
                Else (Default)
              </Typography>
              <TextField
                label="Default message"
                value={elseClause}
                onChange={(e) => handleElseChange(e.target.value)}
                fullWidth
                multiline
                minRows={2}
                size="small"
                placeholder="Message to display if no conditions above are met..."
              />
            </CardContent>
          </Card>
        </Stack>
      )}
    </Box>
  )
}
