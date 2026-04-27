import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Stack, Typography, FormControl, InputLabel, Select, MenuItem, TextField, Chip,
  Card, CardContent, IconButton, Tooltip,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import { useQueryBuilderResources, useQueryBuilderOperators } from '../../../hooks/useCqlImport'
import { generateId } from '../../../utils/validation'
import { codeBlockSx } from '../../../constants/authoringConstants'
import { escapeCqlString, escapeCqlIdentifier } from '../../../utils/cqlString'

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

export default function QueryBuilder({ onInsertCql }: QueryBuilderProps) {
  const { t } = useTranslation('authoring')
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

  // Property paths come from the resource metadata API; still split + validate
  // so a typo like `code['evil']` can't escape the dotted-identifier shape.
  const safeAccessor = (path: string): string =>
    path
      .split('.')
      .map((part) => /^[A-Za-z_][A-Za-z0-9_]*$/.test(part) ? part : `"${escapeCqlIdentifier(part)}"`)
      .join('.')

  const buildCqlQuery = (): string => {
    if (!resourceType) return ''

    let query = `[${resourceType}`
    if (valueSetName) {
      query += `: "${escapeCqlIdentifier(valueSetName)}"`
    }
    query += ']'

    const whereConditions = conditions
      .filter((c) => c.property && c.operator)
      .map((c) => {
        const op = allOperators.find((o) => o.id === c.operator)
        if (!op) return ''
        const accessor = safeAccessor(c.property)

        if (op.id === 'is_null') return `${accessor} is null`
        if (op.id === 'is_not_null') return `${accessor} is not null`
        if (op.id === 'in') return `${accessor} in "${escapeCqlIdentifier(c.value)}"`

        return `${accessor} ${op.symbol} ${formatValue(c.value, c.propertyType)}`
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
        return `'${escapeCqlString(value)}'`
      case 'integer':
      case 'decimal':
        return /^-?\d+(\.\d+)?$/.test(value.trim()) ? value.trim() : `'${escapeCqlString(value)}'`
      case 'boolean':
        return value === 'true' || value === 'false' ? value : `'${escapeCqlString(value)}'`
      default:
        return `'${escapeCqlString(value)}'`
    }
  }

  const generatedCql = buildCqlQuery()

  return (
    <Box>
      <Typography variant="h6" sx={{ mb: 2 }}>{t('queryBuilder.title')}</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('queryBuilder.description')}
      </Typography>

      <Stack spacing={2}>
        {/* Resource Type */}
        <FormControl size="small" fullWidth>
          <InputLabel>{t('queryBuilder.resourceType')}</InputLabel>
          <Select
            value={resourceType}
            label={t('queryBuilder.resourceType')}
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
            label={t('queryBuilder.valueSetOptional')}
            value={valueSetName}
            onChange={(e) => setValueSetName(e.target.value)}
            placeholder={t('queryBuilder.valueSetPlaceholder')}
            fullWidth
          />
        )}

        {/* Conditions */}
        {resourceType && (
          <>
            <Stack direction="row" alignItems="center" spacing={1}>
              <Typography variant="subtitle2">{t('queryBuilder.conditions')}</Typography>
              <GradientButton size="small" startIcon={<AddIcon />} onClick={handleAddCondition}>
                {t('queryBuilder.addCondition')}
              </GradientButton>
            </Stack>

            {conditions.map((cond) => (
              <Card key={cond.id} variant="outlined">
                <CardContent sx={{ py: 1, '&:last-child': { pb: 1 } }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    {/* Property */}
                    <FormControl size="small" sx={{ minWidth: 160 }}>
                      <InputLabel>{t('queryBuilder.propertyLabel')}</InputLabel>
                      <Select
                        value={cond.property}
                        label={t('queryBuilder.propertyLabel')}
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
                      <InputLabel>{t('queryBuilder.operatorLabel')}</InputLabel>
                      <Select
                        value={cond.operator}
                        label={t('queryBuilder.operatorLabel')}
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
                              <InputLabel>{t('queryBuilder.valueLabel')}</InputLabel>
                              <Select
                                value={cond.value}
                                label={t('queryBuilder.valueLabel')}
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
                            label={t('queryBuilder.valueLabel')}
                            value={cond.value}
                            onChange={(e) => handleUpdateCondition(cond.id, { value: e.target.value })}
                            sx={{ flex: 1 }}
                          />
                        )
                      })()
                    )}

                    <Tooltip title={t('queryBuilder.removeCondition')}>
                      <IconButton size="small" color="error" onClick={() => handleRemoveCondition(cond.id)} aria-label={t('queryBuilder.removeCondition')}>
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
            <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{t('queryBuilder.generatedCql')}</Typography>
            <Box
              sx={{
                ...codeBlockSx,
                minHeight: 40,
              }}
            >
              {generatedCql || t('queryBuilder.selectToStart')}
            </Box>

            {onInsertCql && generatedCql && (
              <GradientButton
                size="small"
                sx={{ mt: 1 }}
                onClick={() => onInsertCql(generatedCql)}
              >
                {t('queryBuilder.insertIntoEditor')}
              </GradientButton>
            )}
          </Box>
        )}
      </Stack>
    </Box>
  )
}
