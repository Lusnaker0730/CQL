import { useState, useMemo } from 'react'
import {
  Box, Stack, Typography, FormControl, InputLabel, Select, MenuItem, TextField, Chip,
  Card, CardContent, IconButton, Tooltip,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import { useQueryBuilderResources, useQueryBuilderOperators } from '../../../hooks/useCqlImport'

interface QueryCondition {
  id: string
  property: string
  propertyType: string
  operator: string
  value: string
}

interface QueryBuilderProps {
  onInsertCql?: (cql: string) => void
}

function generateId(): string {
  return crypto.randomUUID()
}

export default function QueryBuilder({ onInsertCql }: QueryBuilderProps) {
  const [resourceType, setResourceType] = useState('')
  const [conditions, setConditions] = useState<QueryCondition[]>([])
  const [valueSetName, setValueSetName] = useState('')

  const { data: resources = [] } = useQueryBuilderResources()
  const { data: allOperators = [] } = useQueryBuilderOperators()

  const selectedResource = useMemo(
    () => resources.find((r) => r.name === resourceType),
    [resources, resourceType]
  )

  const handleAddCondition = () => {
    setConditions([
      ...conditions,
      { id: generateId(), property: '', propertyType: '', operator: '', value: '' },
    ])
  }

  const handleRemoveCondition = (id: string) => {
    setConditions(conditions.filter((c) => c.id !== id))
  }

  const handleUpdateCondition = (id: string, updates: Partial<QueryCondition>) => {
    setConditions(conditions.map((c) => (c.id === id ? { ...c, ...updates } : c)))
  }

  const handlePropertyChange = (id: string, propertyName: string) => {
    const prop = selectedResource?.properties.find((p) => p.name === propertyName)
    handleUpdateCondition(id, {
      property: propertyName,
      propertyType: prop?.type || '',
      operator: '',
      value: '',
    })
  }

  const getOperatorsForType = (type: string) => {
    return allOperators.filter((op) => op.applicableTypes.includes(type))
  }

  const buildCqlQuery = (): string => {
    if (!resourceType) return ''

    let query = `[${resourceType}`
    if (valueSetName) {
      query += `: "${valueSetName}"`
    }
    query += ']'

    const whereConditions = conditions
      .filter((c) => c.property && c.operator)
      .map((c) => {
        const op = allOperators.find((o) => o.id === c.operator)
        if (!op) return ''

        if (op.id === 'is_null') return `${c.property} is null`
        if (op.id === 'is_not_null') return `${c.property} is not null`
        if (op.id === 'in') return `${c.property} in "${c.value}"`

        return `${c.property} ${op.symbol} ${formatValue(c.value, c.propertyType)}`
      })
      .filter(Boolean)

    if (whereConditions.length > 0) {
      query += ` where ${whereConditions.join(' and ')}`
    }

    return query
  }

  const formatValue = (value: string, type: string): string => {
    if (!value) return "''"
    switch (type) {
      case 'code':
      case 'string':
        return `'${value}'`
      case 'integer':
      case 'decimal':
        return value
      case 'boolean':
        return value
      default:
        return `'${value}'`
    }
  }

  const generatedCql = buildCqlQuery()

  return (
    <Box>
      <Typography variant="h6" sx={{ mb: 2 }}>Query Builder</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Build FHIR resource queries visually. Select a resource type, add filter conditions,
        and generate the CQL query.
      </Typography>

      <Stack spacing={2}>
        {/* Resource Type */}
        <FormControl size="small" fullWidth>
          <InputLabel>Resource Type</InputLabel>
          <Select
            value={resourceType}
            label="Resource Type"
            onChange={(e) => {
              setResourceType(e.target.value)
              setConditions([])
              setValueSetName('')
            }}
          >
            {resources.map((r) => (
              <MenuItem key={r.name} value={r.name}>{r.name}</MenuItem>
            ))}
          </Select>
        </FormControl>

        {/* Value Set filter */}
        {resourceType && (
          <TextField
            size="small"
            label="Value Set (optional)"
            value={valueSetName}
            onChange={(e) => setValueSetName(e.target.value)}
            placeholder="e.g., Diabetes Value Set"
            fullWidth
          />
        )}

        {/* Conditions */}
        {resourceType && (
          <>
            <Stack direction="row" alignItems="center" spacing={1}>
              <Typography variant="subtitle2">Conditions</Typography>
              <GradientButton size="small" startIcon={<AddIcon />} onClick={handleAddCondition}>
                Add Condition
              </GradientButton>
            </Stack>

            {conditions.map((cond) => (
              <Card key={cond.id} variant="outlined">
                <CardContent sx={{ py: 1, '&:last-child': { pb: 1 } }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {/* Property */}
                    <FormControl size="small" sx={{ minWidth: 160 }}>
                      <InputLabel>Property</InputLabel>
                      <Select
                        value={cond.property}
                        label="Property"
                        onChange={(e) => handlePropertyChange(cond.id, e.target.value)}
                      >
                        {selectedResource?.properties.map((p) => (
                          <MenuItem key={p.name} value={p.name}>
                            {p.name}
                            <Chip label={p.type} size="small" sx={{ ml: 1 }} variant="outlined" />
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>

                    {/* Operator */}
                    <FormControl size="small" sx={{ minWidth: 150 }}>
                      <InputLabel>Operator</InputLabel>
                      <Select
                        value={cond.operator}
                        label="Operator"
                        onChange={(e) => handleUpdateCondition(cond.id, { operator: e.target.value })}
                        disabled={!cond.property}
                      >
                        {getOperatorsForType(cond.propertyType).map((op) => (
                          <MenuItem key={op.id} value={op.id}>{op.label}</MenuItem>
                        ))}
                      </Select>
                    </FormControl>

                    {/* Value */}
                    {cond.operator && cond.operator !== 'is_null' && cond.operator !== 'is_not_null' && (
                      (() => {
                        const prop = selectedResource?.properties.find((p) => p.name === cond.property)
                        if (prop?.values) {
                          return (
                            <FormControl size="small" sx={{ minWidth: 140 }}>
                              <InputLabel>Value</InputLabel>
                              <Select
                                value={cond.value}
                                label="Value"
                                onChange={(e) => handleUpdateCondition(cond.id, { value: e.target.value })}
                              >
                                {prop.values.map((v) => (
                                  <MenuItem key={v} value={v}>{v}</MenuItem>
                                ))}
                              </Select>
                            </FormControl>
                          )
                        }
                        return (
                          <TextField
                            size="small"
                            label="Value"
                            value={cond.value}
                            onChange={(e) => handleUpdateCondition(cond.id, { value: e.target.value })}
                            sx={{ flex: 1 }}
                          />
                        )
                      })()
                    )}

                    <Tooltip title="Remove condition">
                      <IconButton size="small" color="error" onClick={() => handleRemoveCondition(cond.id)} aria-label="Remove condition">
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                </CardContent>
              </Card>
            ))}
          </>
        )}

        {/* Preview */}
        {resourceType && (
          <Box>
            <Typography variant="subtitle2" sx={{ mb: 0.5 }}>Generated CQL</Typography>
            <Box
              sx={{
                p: 1.5,
                borderRadius: 1,
                backgroundColor: (theme) => theme.palette.mode === 'dark' ? '#1e1e1e' : '#f5f5f5',
                fontFamily: '"Fira Code", "Consolas", monospace',
                fontSize: '0.85rem',
                whiteSpace: 'pre-wrap',
                border: 1,
                borderColor: 'divider',
                minHeight: 40,
              }}
            >
              {generatedCql || '(Select a resource type to start building)'}
            </Box>

            {onInsertCql && generatedCql && (
              <GradientButton
                size="small"
                sx={{ mt: 1 }}
                onClick={() => onInsertCql(generatedCql)}
              >
                Insert into Editor
              </GradientButton>
            )}
          </Box>
        )}
      </Stack>
    </Box>
  )
}
