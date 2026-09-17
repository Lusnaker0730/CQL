import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  Button,
  Typography,
  MenuItem,
  ToggleButtonGroup,
  ToggleButton,
  Alert,
} from '@mui/material'
import { alpha } from '@mui/material/styles'
import { Add as AddIcon } from '@mui/icons-material'
import ElementListItem from './ElementListItem'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import SnippetPreview from './SnippetPreview'
import RetrieveBuilder from './RetrieveBuilder'
import QueryBuilder from './QueryBuilder'
import OperatorPanel from './OperatorPanel'
import RecommendationBuilder from './RecommendationBuilder'
import ElementRefBuilder from './ElementRefBuilder'
import ConditionalBuilder from './ConditionalBuilder'
import { extractCqlName } from '../../utils/cqlNames'

interface DefinitionsSectionProps {
  expressions: { name: string; context?: string; resultType?: string }[]
  onInsert: (cqlSnippet: string) => void
  valueSets?: string[]
  codes?: string[]
  parameters?: string[]
  onDelete?: (identifier: string) => void
  onGoTo?: (identifier: string) => void
  onEdit?: (identifier: string, newSnippet: string) => void
}

/** Build contextual templates using actual valueSets/codes from the CQL */
function buildTemplates(valueSets: string[], codes: string[], expressions: { name: string }[]) {
  const firstVS = valueSets.length > 0 ? extractCqlName(valueSets[0]) : 'ValueSetName'
  const firstCode = codes.length > 0 ? extractCqlName(codes[0]) : 'CodeName'
  const firstExpr = expressions.length > 0 ? `"${expressions[0].name}"` : '<expression>'

  return [
    { labelKey: 'blank' as const, template: '' },
    { labelKey: 'ageFilter' as const, template: 'AgeInYearsAt(start of "Measurement Period") >= 18' },
    { labelKey: 'conditionCheck' as const, template: `exists [Condition: "${firstVS}"] C\n    where C.clinicalStatus.coding.code contains 'active'` },
    { labelKey: 'encounterCheck' as const, template: `exists [Encounter] E\n    where E.period during "Measurement Period"\n      and E.status = 'finished'` },
    { labelKey: 'medicationCheck' as const, template: `exists [MedicationRequest: "${firstVS}"] M\n    where M.authoredOn during "Measurement Period"\n      and M.status = 'active'` },
    { labelKey: 'observationValue' as const, template: `[Observation: "${firstCode}"] O\n    where O.effective in "Measurement Period"\n    sort by effective desc` },
    { labelKey: 'ifElse' as const, template: `if ${firstExpr} then\n  <result>\nelse\n  <default>` },
    { labelKey: 'caseWhen' as const, template: `case\n  when ${firstExpr} then <result1>\n  when <condition2> then <result2>\n  else <default>\nend` },
    { labelKey: 'nullCheck' as const, template: `if ${firstExpr} is not null then\n  ${firstExpr}.value\nelse\n  null` },
  ]
}

export default function DefinitionsSection({
  expressions,
  onInsert,
  valueSets = [],
  codes = [],
  parameters = [],
  onDelete,
  onGoTo,
  onEdit,
}: DefinitionsSectionProps) {
  const { t } = useTranslation('builder')
  const [showForm, setShowForm] = useState(false)
  const [mode, setMode] = useState<'template' | 'retrieve' | 'query' | 'operator' | 'recommendation' | 'conditional' | 'elementRef'>('template')
  const [name, setName] = useState('')
  const [context, setContext] = useState('Patient')
  const [templateIdx, setTemplateIdx] = useState(0)
  const [expression, setExpression] = useState('')
  const [editingItem, setEditingItem] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')

  const TEMPLATES = useMemo(
    () => buildTemplates(valueSets, codes, expressions),
    [valueSets, codes, expressions],
  )

  const handleTemplateChange = (idx: number) => {
    setTemplateIdx(idx)
    setExpression(TEMPLATES[idx].template)
  }

  const handleAdd = () => {
    if (!name.trim() || !expression.trim()) return
    const contextLine = context !== 'Patient' ? `context ${context}\n` : ''
    const snippet = `${contextLine}define "${name}":\n  ${expression.split('\n').join('\n  ')}`
    setPreviewSnippet(snippet)
  }

  const handleConfirmInsert = () => {
    if (editingItem) {
      onEdit?.(editingItem, previewSnippet)
    } else {
      onInsert(previewSnippet)
    }
    resetForm()
  }

  const handleStartEdit = (expr: { name: string }) => {
    setEditingItem(expr.name)
    setName(expr.name)
    setExpression('')
    setShowForm(true)
    setMode('template')
  }

  const resetForm = () => {
    setShowForm(false)
    setName('')
    setContext('Patient')
    setTemplateIdx(0)
    setExpression('')
    setEditingItem(null)
    setPreviewSnippet('')
  }

  const handleConfirmDelete = () => {
    if (deleteTarget) {
      onDelete?.(deleteTarget)
      setDeleteTarget(null)
    }
  }

  return (
    <Stack spacing={0.5}>
      {expressions.length > 0 ? (
        expressions.map((expr, idx) => (
          <ElementListItem
            key={idx}
            label={expr.name}
            secondaryLabel={expr.resultType || undefined}
            onGoTo={() => onGoTo?.(expr.name)}
            onEdit={() => handleStartEdit(expr)}
            onDelete={() => setDeleteTarget(expr.name)}
          />
        ))
      ) : (
        <Typography
          variant="body2"
          sx={{
            color: "text.secondary",
            fontStyle: 'italic'
          }}>
          {t('common.noItemsFound', { type: t('sections.definitions').toLowerCase() })}
        </Typography>
      )}
      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          {t('common.addItem', { type: 'Definition' })}
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: (theme) => alpha(theme.palette.primary.main, 0.03), borderRadius: 1 }}>
          <ToggleButtonGroup
            size="small"
            exclusive
            value={mode}
            onChange={(_, v) => { if (v) setMode(v) }}
            sx={{ alignSelf: 'flex-start' }}
          >
            <ToggleButton value="template" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              {t('definitions.template')}
            </ToggleButton>
            <ToggleButton value="retrieve" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              {t('definitions.retrieve')}
            </ToggleButton>
            <ToggleButton value="query" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              {t('definitions.query')}
            </ToggleButton>
            <ToggleButton value="operator" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              {t('definitions.operators')}
            </ToggleButton>
            <ToggleButton value="recommendation" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              {t('definitions.recommendation')}
            </ToggleButton>
            <ToggleButton value="conditional" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              {t('definitions.conditional')}
            </ToggleButton>
            <ToggleButton value="elementRef" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              {t('definitions.elementRef')}
            </ToggleButton>
          </ToggleButtonGroup>

          {mode === 'template' ? (
            <>
              {editingItem && (
                <Alert severity="info" sx={{ py: 0, fontSize: '0.8rem' }}>
                  {t('definitions.editWarning')}
                </Alert>
              )}
              <TextField
                size="small"
                label={t('definitions.name')}
                value={name}
                onChange={(e) => setName(e.target.value)}
              />

              <TextField
                select
                size="small"
                label={t('definitions.context')}
                value={context}
                onChange={(e) => setContext(e.target.value)}
              >
                <MenuItem value="Patient">{t('definitions.contextPatient')}</MenuItem>
                <MenuItem value="Population">{t('definitions.contextPopulation')}</MenuItem>
              </TextField>

              <TextField
                select
                size="small"
                label={t('definitions.template')}
                value={templateIdx}
                onChange={(e) => handleTemplateChange(Number(e.target.value))}
              >
                {TEMPLATES.map((tmpl, i) => (
                  <MenuItem key={i} value={i}>{t(`definitions.templates.${tmpl.labelKey}`)}</MenuItem>
                ))}
              </TextField>

              <TextField
                size="small"
                label={t('definitions.expression')}
                multiline
                rows={3}
                value={expression}
                onChange={(e) => setExpression(e.target.value)}
                sx={{ '& textarea': { fontFamily: 'monospace', fontSize: '0.8rem' } }}
              />

              {previewSnippet ? (
                <SnippetPreview
                  snippet={previewSnippet}
                  onInsert={handleConfirmInsert}
                  onCancel={() => setPreviewSnippet('')}
                  insertLabel={editingItem ? t('common.update') : t('common.insert')}
                />
              ) : (
                <Stack direction="row" spacing={1}>
                  <Button size="small" variant="outlined" onClick={handleAdd}
                    disabled={!name.trim() || !expression.trim()}>
                    {editingItem ? t('common.previewUpdate') : t('common.previewInsert')}
                  </Button>
                  <Button size="small" onClick={resetForm}>{t('common.cancel')}</Button>
                </Stack>
              )}
            </>
          ) : mode === 'retrieve' ? (
            <RetrieveBuilder
              valueSets={valueSets}
              codes={codes}
              onInsert={(snippet) => {
                onInsert(snippet)
                resetForm()
              }}
              onCancel={resetForm}
            />
          ) : mode === 'query' ? (
            <QueryBuilder
              valueSets={valueSets}
              codes={codes}
              onInsert={(snippet) => {
                onInsert(snippet)
                resetForm()
              }}
              onCancel={resetForm}
            />
          ) : mode === 'recommendation' ? (
            <RecommendationBuilder
              expressions={expressions.map((e) => e.name)}
              onInsert={(snippet) => {
                onInsert(snippet)
                resetForm()
              }}
              onCancel={resetForm}
            />
          ) : mode === 'elementRef' ? (
            <ElementRefBuilder
              expressions={expressions}
              parameters={parameters}
              onInsert={(snippet) => {
                onInsert(snippet)
                resetForm()
              }}
              onCancel={resetForm}
            />
          ) : mode === 'conditional' ? (
            <ConditionalBuilder
              onInsert={(snippet) => {
                onInsert(snippet)
                resetForm()
              }}
              onCancel={resetForm}
            />
          ) : (
            <OperatorPanel
              expressions={expressions}
              parameters={parameters}
              onInsert={(snippet) => {
                onInsert(snippet)
                resetForm()
              }}
              onCancel={resetForm}
            />
          )}
        </Stack>
      )}
      <ConfirmDeleteDialog
        open={!!deleteTarget}
        title={t('common.deleteElement')}
        itemName={deleteTarget || ''}
        message={t('common.deleteConfirm', { name: deleteTarget })}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
      />
    </Stack>
  );
}
