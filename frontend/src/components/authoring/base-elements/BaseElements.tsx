import { useState, useCallback } from 'react'
import {
  Box, Stack, Typography, IconButton, Tooltip, TextField, Card, CardContent, Chip,
  Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import ConjunctionGroup from '../builder/ConjunctionGroup'
import type { BaseElement, ElementInstance, FormTemplateCategory, ModifierDefinition } from '../../../types/authoring'
import type { DynamicEntry } from '../element-select/ElementSelectDropdown'

function generateId(): string {
  return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
}

interface BaseElementsProps {
  baseElements: BaseElement[]
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  dynamicEntries?: DynamicEntry[]
  onChange: (baseElements: BaseElement[]) => void
}

export default function BaseElements({ baseElements, templates, modifiers, dynamicEntries, onChange }: BaseElementsProps) {
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)
  const pendingDeleteName = pendingDeleteId
    ? baseElements.find((be) => be.uniqueId === pendingDeleteId)?.name || 'this base element'
    : ''

  const handleAdd = () => {
    onChange([
      ...baseElements,
      {
        uniqueId: generateId(),
        name: 'Base Element ' + (baseElements.length + 1),
        type: 'baseElement',
        returnType: 'boolean',
        childInstances: [],
        conjunction: true,
      },
    ])
  }

  const handleRemove = (uniqueId: string) => {
    onChange(baseElements.filter((be) => be.uniqueId !== uniqueId))
  }

  const handleNameChange = (uniqueId: string, name: string) => {
    onChange(baseElements.map((be) => (be.uniqueId === uniqueId ? { ...be, name } : be)))
  }

  const getNameError = (be: BaseElement): string | undefined => {
    if (!be.name.trim()) return 'Name is required'
    if (baseElements.some((b) => b.uniqueId !== be.uniqueId && b.name.trim() === be.name.trim()))
      return 'Duplicate base element name'
    return undefined
  }

  const handleUpdateTree = useCallback(
    (uniqueId: string, childInstances: ElementInstance[]) => {
      onChange(
        baseElements.map((be) =>
          be.uniqueId === uniqueId ? { ...be, childInstances } : be
        )
      )
    },
    [baseElements, onChange]
  )

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">Base Elements</Typography>
        <GradientButton startIcon={<AddIcon />} onClick={handleAdd}>
          Add Base Element
        </GradientButton>
      </Stack>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Define reusable elements that can be referenced across inclusions, exclusions, and subpopulations.
      </Typography>

      {baseElements.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
          <Typography variant="body2">
            No base elements defined. Add base elements to create reusable logic blocks.
          </Typography>
        </Box>
      ) : (
        <Stack spacing={2}>
          {baseElements.map((be) => (
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
                  <Tooltip title="Remove base element">
                    <IconButton size="small" color="error" onClick={() => setPendingDeleteId(be.uniqueId)}>
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
                  onUpdateGroup={(updated) => handleUpdateTree(be.uniqueId, updated.childInstances)}
                  onAddElement={(el) => handleUpdateTree(be.uniqueId, [...(be.childInstances || []), el])}
                  onRemoveElement={(uid) => handleUpdateTree(be.uniqueId, (be.childInstances || []).filter((c) => c.uniqueId !== uid))}
                  onUpdateElement={(uid, updates) =>
                    handleUpdateTree(be.uniqueId, (be.childInstances || []).map((c) => (c.uniqueId === uid ? { ...c, ...updates } : c)))
                  }
                />
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}

      <Dialog open={!!pendingDeleteId} onClose={() => setPendingDeleteId(null)}>
        <DialogTitle>Delete Base Element</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to delete <strong>{pendingDeleteName}</strong>? Any references to this element in other parts of the artifact will break.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPendingDeleteId(null)}>Cancel</Button>
          <Button
            color="error"
            onClick={() => { if (pendingDeleteId) handleRemove(pendingDeleteId); setPendingDeleteId(null) }}
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
