import { useState } from 'react'
import {
  Box, Stack, Typography, IconButton, Tooltip, TextField, Card, CardContent,
  Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import ConjunctionGroup from '../builder/ConjunctionGroup'
import type { Subpopulation, ElementInstance, FormTemplateCategory, ModifierDefinition } from '../../../types/authoring'
import type { DynamicEntry } from '../element-select/ElementSelectDropdown'

function generateId(): string {
  return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
}

interface SubpopulationsProps {
  subpopulations: Subpopulation[]
  templates: FormTemplateCategory[]
  modifiers: ModifierDefinition[]
  dynamicEntries?: DynamicEntry[]
  onChange: (subpopulations: Subpopulation[]) => void
}

export default function Subpopulations({ subpopulations, templates, modifiers, dynamicEntries, onChange }: SubpopulationsProps) {
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)
  const pendingDeleteName = pendingDeleteId
    ? subpopulations.find((sp) => sp.uniqueId === pendingDeleteId)?.subpopulationName || 'this subpopulation'
    : ''

  const handleAdd = () => {
    onChange([
      ...subpopulations,
      {
        uniqueId: generateId(),
        subpopulationName: 'Subpopulation ' + (subpopulations.length + 1),
        special: false,
        childInstances: [],
        conjunction: true,
        returnType: 'boolean',
      },
    ])
  }

  const handleRemove = (uniqueId: string) => {
    onChange(subpopulations.filter((sp) => sp.uniqueId !== uniqueId))
  }

  const handleNameChange = (uniqueId: string, name: string) => {
    onChange(subpopulations.map((sp) => (sp.uniqueId === uniqueId ? { ...sp, subpopulationName: name } : sp)))
  }

  const getNameError = (sp: Subpopulation): string | undefined => {
    if (!sp.subpopulationName.trim()) return 'Name is required'
    if (subpopulations.some((s) => s.uniqueId !== sp.uniqueId && s.subpopulationName.trim() === sp.subpopulationName.trim()))
      return 'Duplicate subpopulation name'
    return undefined
  }

  const handleUpdateTree = (uniqueId: string, childInstances: ElementInstance[]) => {
    onChange(
      subpopulations.map((sp) =>
        sp.uniqueId === uniqueId ? { ...sp, childInstances } : sp
      )
    )
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">Subpopulations</Typography>
        <GradientButton startIcon={<AddIcon />} onClick={handleAdd}>
          Add Subpopulation
        </GradientButton>
      </Stack>

      {subpopulations.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
          <Typography variant="body2">
            No subpopulations defined. Add subpopulations to create targeted recommendations for different patient groups.
          </Typography>
        </Box>
      ) : (
        <Stack spacing={2}>
          {subpopulations.filter((sp) => !sp.special).map((sp) => (
            <Card key={sp.uniqueId} variant="outlined">
              <CardContent>
                <Stack direction="row" alignItems="center" spacing={1} mb={2}>
                  <TextField
                    value={sp.subpopulationName}
                    onChange={(e) => handleNameChange(sp.uniqueId, e.target.value)}
                    size="small"
                    variant="standard"
                    sx={{ '& .MuiInput-input': { fontWeight: 600 } }}
                    error={!!getNameError(sp)}
                    helperText={getNameError(sp)}
                  />
                  <Box sx={{ flex: 1 }} />
                  <Tooltip title="Remove subpopulation">
                    <IconButton size="small" color="error" onClick={() => setPendingDeleteId(sp.uniqueId)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>
                <ConjunctionGroup
                  group={{
                    id: 'And',
                    name: 'And',
                    conjunction: true,
                    returnType: 'boolean',
                    childInstances: sp.childInstances || [],
                  }}
                  treeName={sp.subpopulationName}
                  templates={templates}
                  modifiers={modifiers}
                  dynamicEntries={dynamicEntries}
                  onUpdateGroup={(updated) => handleUpdateTree(sp.uniqueId, updated.childInstances)}
                  onAddElement={(el) => handleUpdateTree(sp.uniqueId, [...(sp.childInstances || []), el])}
                  onRemoveElement={(uid) => handleUpdateTree(sp.uniqueId, (sp.childInstances || []).filter((c) => c.uniqueId !== uid))}
                  onUpdateElement={(uid, updates) =>
                    handleUpdateTree(sp.uniqueId, (sp.childInstances || []).map((c) => (c.uniqueId === uid ? { ...c, ...updates } : c)))
                  }
                />
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}

      <Dialog open={!!pendingDeleteId} onClose={() => setPendingDeleteId(null)}>
        <DialogTitle>Delete Subpopulation</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to delete <strong>{pendingDeleteName}</strong>? This will also remove it from any recommendations that reference it.
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
