import { useState, useMemo, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  Button,
  Typography,
  IconButton,
  Tooltip,
  ToggleButtonGroup,
  ToggleButton,
  Box,
} from '@mui/material'
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  ContentCopy as CopyIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import CqlPreviewBox from './CqlPreviewBox'
import { useCopyToClipboard } from '../../hooks/useCopyToClipboard'

interface ConditionalBuilderProps {
  onInsert: (cqlSnippet: string) => void
  onCancel: () => void
}

interface Branch {
  id: string
  condition: string
  result: string
}

let _nextId = 1
function nextId(): string {
  return String(_nextId++)
}

export default function ConditionalBuilder({ onInsert, onCancel }: ConditionalBuilderProps) {
  const { t } = useTranslation('builder')
  const copyToClipboard = useCopyToClipboard()
  const [name, setName] = useState('')
  const [style, setStyle] = useState<'if' | 'case'>('if')
  const [branches, setBranches] = useState<Branch[]>([
    { id: nextId(), condition: '', result: '' },
  ])
  const [elseResult, setElseResult] = useState('')

  const handleAddBranch = useCallback(() => {
    setBranches((prev) => [...prev, { id: nextId(), condition: '', result: '' }])
  }, [])

  const handleRemoveBranch = useCallback((id: string) => {
    setBranches((prev) => prev.filter((b) => b.id !== id))
  }, [])

  const handleBranchChange = useCallback((id: string, field: 'condition' | 'result', value: string) => {
    setBranches((prev) => prev.map((b) => b.id === id ? { ...b, [field]: value } : b))
  }, [])

  const cqlPreview = useMemo(() => {
    if (!name.trim()) return ''
    const filledBranches = branches.filter((b) => b.condition.trim() && b.result.trim())
    if (filledBranches.length === 0) return ''

    if (style === 'if') {
      const lines: string[] = []
      filledBranches.forEach((b, i) => {
        const keyword = i === 0 ? 'if' : 'else if'
        lines.push(`  ${keyword} ${b.condition} then`)
        lines.push(`    ${b.result}`)
      })
      if (elseResult.trim()) {
        lines.push('  else')
        lines.push(`    ${elseResult}`)
      }
      return `define "${name}":\n${lines.join('\n')}`
    } else {
      const lines: string[] = ['  case']
      filledBranches.forEach((b) => {
        lines.push(`    when ${b.condition} then ${b.result}`)
      })
      if (elseResult.trim()) {
        lines.push(`    else ${elseResult}`)
      }
      lines.push('  end')
      return `define "${name}":\n${lines.join('\n')}`
    }
  }, [name, style, branches, elseResult])

  const handleInsert = () => {
    if (!cqlPreview) return
    onInsert(cqlPreview)
  }

  const monoSx = { '& input': { fontFamily: 'monospace', fontSize: '0.8rem' } } as const

  return (
    <Stack spacing={1}>
      <TextField
        size="small"
        label={t('definitions.name')}
        value={name}
        onChange={(e) => setName(e.target.value)}
      />

      {/* Style toggle */}
      <Stack direction="row" alignItems="center" spacing={1}>
        <Typography variant="caption" fontWeight={600} color="text.secondary">
          {t('conditional.style')}
        </Typography>
        <ToggleButtonGroup
          size="small"
          exclusive
          value={style}
          onChange={(_, v) => { if (v) setStyle(v) }}
        >
          <ToggleButton value="if" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
            {t('conditional.ifElse')}
          </ToggleButton>
          <ToggleButton value="case" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
            {t('conditional.caseWhen')}
          </ToggleButton>
        </ToggleButtonGroup>
      </Stack>

      {/* Branches */}
      {branches.map((branch, idx) => (
        <Box
          key={branch.id}
          sx={{
            p: 1,
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 1,
            bgcolor: 'background.paper',
          }}
        >
          <Stack spacing={0.75}>
            <Stack direction="row" alignItems="center" justifyContent="space-between">
              <Typography variant="caption" fontWeight={600} color="primary.main" sx={{ fontFamily: 'monospace' }}>
                {style === 'if'
                  ? (idx === 0 ? t('conditional.if') : t('conditional.elseIf'))
                  : t('conditional.when')
                }
              </Typography>
              {branches.length > 1 && (
                <Tooltip title={t('conditional.removeBranch')}>
                  <IconButton size="small" onClick={() => handleRemoveBranch(branch.id)} sx={{ p: 0.25 }}>
                    <DeleteIcon sx={{ fontSize: 16 }} />
                  </IconButton>
                </Tooltip>
              )}
            </Stack>
            <TextField
              size="small"
              label={t('conditional.condition')}
              placeholder={t('conditional.conditionPlaceholder')}
              value={branch.condition}
              onChange={(e) => handleBranchChange(branch.id, 'condition', e.target.value)}
              sx={monoSx}
            />
            <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ fontFamily: 'monospace' }}>
              {t('conditional.then')}
            </Typography>
            <TextField
              size="small"
              label={t('conditional.result')}
              placeholder={t('conditional.resultPlaceholder')}
              value={branch.result}
              onChange={(e) => handleBranchChange(branch.id, 'result', e.target.value)}
              sx={monoSx}
            />
          </Stack>
        </Box>
      ))}

      <Button
        size="small"
        startIcon={<AddIcon />}
        onClick={handleAddBranch}
        sx={{ alignSelf: 'flex-start' }}
      >
        {t('conditional.addBranch')}
      </Button>

      {/* Else clause */}
      <Box
        sx={{
          p: 1,
          border: '1px dashed',
          borderColor: 'divider',
          borderRadius: 1,
          bgcolor: 'background.paper',
        }}
      >
        <Stack spacing={0.75}>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ fontFamily: 'monospace' }}>
            {t('conditional.else')}
          </Typography>
          <TextField
            size="small"
            label={t('conditional.result')}
            placeholder={t('conditional.resultPlaceholder')}
            value={elseResult}
            onChange={(e) => setElseResult(e.target.value)}
            sx={monoSx}
          />
        </Stack>
      </Box>

      {/* CQL Preview */}
      <CqlPreviewBox code={cqlPreview} />

      {/* Action buttons */}
      <Stack direction="row" spacing={1} alignItems="center">
        <GradientButton onClick={handleInsert} disabled={!cqlPreview}>
          {t('common.insert')}
        </GradientButton>
        {cqlPreview && (
          <Tooltip title={t('common.copyToClipboard')}>
            <IconButton
              size="small"
              onClick={() => copyToClipboard(cqlPreview)}
            >
              <CopyIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        <Button size="small" onClick={onCancel}>{t('common.cancel')}</Button>
      </Stack>
    </Stack>
  )
}
