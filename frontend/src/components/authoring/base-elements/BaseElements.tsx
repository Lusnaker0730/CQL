import { useState, useCallback } from 'react'
import { useTranslation, Trans } from 'react-i18next'
import {
  Box, Stack, Typography, IconButton, Tooltip, TextField, Card, CardContent, Chip,
  Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button, Menu, MenuItem, ListItemIcon, ListItemText,
} from '@mui/material'
// Sub-path icon imports (not barrel): see ArithmeticElement for rationale.
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import CalculateIcon from '@mui/icons-material/Calculate'
import TreeIcon from '@mui/icons-material/AccountTree'
import FunctionsIcon from '@mui/icons-material/Functions'
import GradientButton from '../../common/GradientButton'
import ConjunctionGroup from '../builder/ConjunctionGroup'
import ArithmeticElement from './ArithmeticElement'
import ArithmeticUnaryElement from './ArithmeticUnaryElement'
import type { BaseElement, ElementInstance, FormTemplateCategory, ModifierDefinition } from '../../../types/authoring'
import type { DynamicEntry } from '../element-select/ElementSelectDropdown'
import { generateId } from '../../../utils/validation'
import { getEffectiveReturnType } from '../../../utils/modifierUtils'

/** Compute the effective return type for a base element from its child elements. */
function computeBaseReturnType(children: ElementInstance[]): string {
  if (children.length === 0) return 'boolean'
  // If single non-conjunction child, use its effective return type (last valid modifier)
  if (children.length === 1 && !children[0].conjunction) {
    const child = children[0]
    return getEffectiveReturnType(child.returnType, child.modifiers)
  }
  // Multiple children or conjunction → boolean (combined logic)
  return 'boolean'
}

interface BaseElementsProps {
  baseElements: BaseElement[]
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  dynamicEntries?: DynamicEntry[]
  twcoreMode?: boolean
  onChange: (baseElements: BaseElement[]) => void
}

export default function BaseElements({ baseElements, templates, modifiers, dynamicEntries, twcoreMode, onChange }: BaseElementsProps) {
  const { t } = useTranslation('authoring')
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)
  const pendingDeleteName = pendingDeleteId
    ? baseElements.find((be) => be.uniqueId === pendingDeleteId)?.name || t('baseElements.fallbackName')
    : ''

  const [addMenuAnchor, setAddMenuAnchor] = useState<HTMLElement | null>(null)

  const handleAdd = () => {
    onChange([
      ...baseElements,
      {
        uniqueId: generateId(),
        name: t('baseElements.defaultName', { number: baseElements.length + 1 }),
        type: 'baseElement',
        returnType: 'boolean',
        childInstances: [],
        conjunction: true,
      },
    ])
    setAddMenuAnchor(null)
  }

  const handleAddArithmetic = () => {
    // PAT-163: new arithmetic elements start with the N-ary shape (2 operands +
    // 1 operator); the editor never writes legacy left_*/right_* fields anymore.
    onChange([
      ...baseElements,
      {
        uniqueId: generateId(),
        name: t('baseElements.calculatedDefaultName', { number: baseElements.filter((be) => be.type === 'arithmeticExpression').length + 1 }),
        type: 'arithmeticExpression',
        returnType: 'system_quantity',
        fields: [
          { id: 'operands', type: 'json', name: 'operands', value: [
            { mode: 'element' },
            { mode: 'element' },
          ] as unknown as string },
          { id: 'operators', type: 'json', name: 'operators', value: ['+'] as unknown as string },
        ],
        childInstances: [],
        conjunction: false,
      },
    ])
    setAddMenuAnchor(null)
  }

  const handleAddUnary = () => {
    onChange([
      ...baseElements,
      {
        uniqueId: generateId(),
        name: t('baseElements.unaryDefaultName', { number: baseElements.filter((be) => be.type === 'arithmeticUnary').length + 1 }),
        type: 'arithmeticUnary',
        returnType: 'system_quantity',
        fields: [
          { id: 'function', type: 'string', name: 'Function', value: 'Abs' },
          { id: 'operand_mode', type: 'string', name: 'Operand Mode', value: 'element' },
          { id: 'operand_id', type: 'string', name: 'Operand', value: '' },
          { id: 'operand_literal', type: 'string', name: 'Operand Literal', value: '' },
        ],
        childInstances: [],
        conjunction: false,
      },
    ])
    setAddMenuAnchor(null)
  }

  const handleRemove = (uniqueId: string) => {
    onChange(baseElements.filter((be) => be.uniqueId !== uniqueId))
  }

  const handleNameChange = (uniqueId: string, name: string) => {
    onChange(baseElements.map((be) => (be.uniqueId === uniqueId ? { ...be, name } : be)))
  }

  const getNameError = (be: BaseElement): string | undefined => {
    if (!be.name.trim()) return t('baseElements.nameRequired')
    if (baseElements.some((b) => b.uniqueId !== be.uniqueId && b.name.trim() === be.name.trim()))
      return t('baseElements.duplicateName')
    return undefined
  }

  const handleUpdateTree = useCallback(
    (uniqueId: string, childInstances: ElementInstance[]) => {
      onChange(
        baseElements.map((be) =>
          be.uniqueId === uniqueId
            ? { ...be, childInstances, returnType: computeBaseReturnType(childInstances) }
            : be
        )
      )
    },
    [baseElements, onChange]
  )

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">{t('baseElements.title')}</Typography>
        <GradientButton startIcon={<AddIcon />} onClick={(e) => setAddMenuAnchor(e.currentTarget)}>
          {t('baseElements.add')}
        </GradientButton>
        <Menu anchorEl={addMenuAnchor} open={!!addMenuAnchor} onClose={() => setAddMenuAnchor(null)}>
          <MenuItem onClick={handleAdd}>
            <ListItemIcon><TreeIcon fontSize="small" /></ListItemIcon>
            <ListItemText>{t('arithmetic.logicElement')}</ListItemText>
          </MenuItem>
          <MenuItem onClick={handleAddArithmetic}>
            <ListItemIcon><CalculateIcon fontSize="small" /></ListItemIcon>
            <ListItemText>{t('arithmetic.arithmeticElement')}</ListItemText>
          </MenuItem>
          <MenuItem onClick={handleAddUnary}>
            <ListItemIcon><FunctionsIcon fontSize="small" /></ListItemIcon>
            <ListItemText>{t('arithmeticUnary.unaryElement')}</ListItemText>
          </MenuItem>
        </Menu>
      </Stack>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('baseElements.description')}
      </Typography>

      {baseElements.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
          <Typography variant="body2">
            {t('baseElements.emptyState')}
          </Typography>
        </Box>
      ) : (
        <Stack spacing={2}>
          {baseElements.map((be) =>
            be.type === 'arithmeticExpression' ? (
              <ArithmeticElement
                key={be.uniqueId}
                element={be}
                availableOperands={baseElements
                  .filter((b) => b.uniqueId !== be.uniqueId)
                  .map((b) => ({ uniqueId: b.uniqueId, name: b.name, returnType: b.returnType }))}
                onUpdate={(updates) =>
                  onChange(baseElements.map((b) => (b.uniqueId === be.uniqueId ? { ...b, ...updates } : b)))
                }
                onDelete={() => setPendingDeleteId(be.uniqueId)}
              />
            ) : be.type === 'arithmeticUnary' ? (
              <ArithmeticUnaryElement
                key={be.uniqueId}
                element={be}
                availableOperands={baseElements
                  .filter((b) => b.uniqueId !== be.uniqueId)
                  .map((b) => ({ uniqueId: b.uniqueId, name: b.name, returnType: b.returnType }))}
                onUpdate={(updates) =>
                  onChange(baseElements.map((b) => (b.uniqueId === be.uniqueId ? { ...b, ...updates } : b)))
                }
                onDelete={() => setPendingDeleteId(be.uniqueId)}
              />
            ) : (
              <Card key={be.uniqueId} variant="outlined">
                <CardContent>
                  <Stack direction="row" alignItems="center" spacing={1} mb={2}>
                    <TextField
                      value={be.name}
                      onChange={(e) => handleNameChange(be.uniqueId, e.target.value)}
                      size="small"
                      variant="standard"
                      sx={{ '& .MuiInput-input': { fontWeight: 600 } }}
                      error={!!getNameError(be)}
                      helperText={getNameError(be)}
                    />
                    <Chip label={be.returnType} size="small" variant="outlined" />
                    <Box sx={{ flex: 1 }} />
                    <Tooltip title={t('baseElements.removeTooltip')}>
                      <IconButton size="small" color="error" onClick={() => setPendingDeleteId(be.uniqueId)} aria-label={t('baseElements.removeTooltip')}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                  <ConjunctionGroup
                    group={{
                      id: 'And',
                      name: 'And',
                      conjunction: true,
                      returnType: be.returnType,
                      childInstances: be.childInstances || [],
                    }}
                    treeName={be.name}
                    templates={templates}
                    modifiers={modifiers}
                    dynamicEntries={dynamicEntries}
                    twcoreMode={twcoreMode}
                    hideElementName
                    onUpdateGroup={(updated) => handleUpdateTree(be.uniqueId, updated.childInstances)}
                    onAddElement={(el) => handleUpdateTree(be.uniqueId, [...(be.childInstances || []), el])}
                    onRemoveElement={(uid) => handleUpdateTree(be.uniqueId, (be.childInstances || []).filter((c) => c.uniqueId !== uid))}
                    onUpdateElement={(uid, updates) =>
                      handleUpdateTree(be.uniqueId, (be.childInstances || []).map((c) => (c.uniqueId === uid ? { ...c, ...updates } : c)))
                    }
                  />
                </CardContent>
              </Card>
            )
          )}
        </Stack>
      )}

      <Dialog open={!!pendingDeleteId} onClose={() => setPendingDeleteId(null)}>
        <DialogTitle>{t('baseElements.deleteTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            <Trans i18nKey="baseElements.deleteConfirm" ns="authoring" values={{ name: pendingDeleteName }} components={{ strong: <strong /> }} />
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
