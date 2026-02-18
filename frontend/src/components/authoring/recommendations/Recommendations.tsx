import { useState, useCallback } from 'react'
import {
  Box, Stack, Typography, IconButton, Tooltip, TextField, Card, CardContent,
  MenuItem, Select, FormControl, InputLabel, Chip, Divider, Collapse, Switch, FormControlLabel,
  Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button,
} from '@mui/material'
import {
  Add as AddIcon, Delete as DeleteIcon, ArrowUpward, ArrowDownward,
  LinkOutlined, HelpOutline as HelpIcon,
  MedicalServices as MedIcon, Assignment as ServiceIcon,
} from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import type { Recommendation, Subpopulation, Suggestion, SuggestionAction } from '../../../types/authoring'
import { generateId } from '../../../utils/validation'
import { SPECIAL_SUBPOPS } from '../../../constants/authoringConstants'

const GRADES = [
  { value: '', label: 'None' },
  { value: 'A', label: 'Grade A — Strong recommendation' },
  { value: 'B', label: 'Grade B — Moderate recommendation' },
  { value: 'C', label: 'Grade C — Optional' },
  { value: 'D', label: 'Grade D — Against recommendation' },
  { value: 'I', label: 'Grade I — Insufficient evidence' },
]

interface RecommendationsProps {
  recommendations: Recommendation[]
  subpopulations: Subpopulation[]
  onChange: (recommendations: Recommendation[]) => void
}

export default function Recommendations({ recommendations, subpopulations, onChange }: RecommendationsProps) {
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)
  const pendingDeleteIndex = pendingDeleteId
    ? recommendations.findIndex((r) => r.uid === pendingDeleteId)
    : -1

  const handleAdd = () => {
    onChange([
      ...recommendations,
      {
        uid: generateId(),
        text: '',
        grade: '',
        rationale: '',
        comment: '',
        subpopulations: [],
        links: [],
        suggestions: [],
      },
    ])
  }

  const handleRemove = (uid: string) => {
    onChange(recommendations.filter((r) => r.uid !== uid))
  }

  const handleUpdate = useCallback(
    (uid: string, updates: Partial<Recommendation>) => {
      onChange(recommendations.map((r) => (r.uid === uid ? { ...r, ...updates } : r)))
    },
    [recommendations, onChange]
  )

  const handleMoveUp = (index: number) => {
    if (index === 0) return
    const copy = [...recommendations]
    ;[copy[index - 1], copy[index]] = [copy[index], copy[index - 1]]
    onChange(copy)
  }

  const handleMoveDown = (index: number) => {
    if (index >= recommendations.length - 1) return
    const copy = [...recommendations]
    ;[copy[index], copy[index + 1]] = [copy[index + 1], copy[index]]
    onChange(copy)
  }

  const handleToggleSubpop = (uid: string, sp: Subpopulation, checked: boolean) => {
    const rec = recommendations.find((r) => r.uid === uid)
    if (!rec) return
    const current = rec.subpopulations || []
    const updated = checked
      ? [...current, { uniqueId: sp.uniqueId, subpopulationName: sp.subpopulationName }]
      : current.filter((s) => s.uniqueId !== sp.uniqueId)
    handleUpdate(uid, { subpopulations: updated })
  }

  const handleAddLink = (uid: string) => {
    const rec = recommendations.find((r) => r.uid === uid)
    if (!rec) return
    handleUpdate(uid, {
      links: [...(rec.links || []), { type: 'absolute', label: '', url: '' }],
    })
  }

  const handleUpdateLink = (uid: string, linkIndex: number, field: string, value: string) => {
    const rec = recommendations.find((r) => r.uid === uid)
    if (!rec) return
    const links = [...(rec.links || [])]
    links[linkIndex] = { ...links[linkIndex], [field]: value }
    handleUpdate(uid, { links })
  }

  const handleRemoveLink = (uid: string, linkIndex: number) => {
    const rec = recommendations.find((r) => r.uid === uid)
    if (!rec) return
    handleUpdate(uid, { links: (rec.links || []).filter((_, i) => i !== linkIndex) })
  }

  // ----- Suggestion handlers -----
  const handleAddSuggestion = (uid: string) => {
    const rec = recommendations.find((r) => r.uid === uid)
    if (!rec) return
    handleUpdate(uid, {
      suggestions: [...(rec.suggestions || []), { uid: generateId(), label: '', actions: [] }],
    })
  }

  const handleUpdateSuggestion = (uid: string, sugIdx: number, updates: Partial<Suggestion>) => {
    const rec = recommendations.find((r) => r.uid === uid)
    if (!rec) return
    const suggestions = [...(rec.suggestions || [])]
    suggestions[sugIdx] = { ...suggestions[sugIdx], ...updates }
    handleUpdate(uid, { suggestions })
  }

  const handleRemoveSuggestion = (uid: string, sugIdx: number) => {
    const rec = recommendations.find((r) => r.uid === uid)
    if (!rec) return
    handleUpdate(uid, { suggestions: (rec.suggestions || []).filter((_, i) => i !== sugIdx) })
  }

  const handleAddAction = (uid: string, sugIdx: number) => {
    const rec = recommendations.find((r) => r.uid === uid)
    if (!rec) return
    const suggestions = [...(rec.suggestions || [])]
    const sug = { ...suggestions[sugIdx] }
    sug.actions = [...(sug.actions || []), { type: 'create', description: '', resource: { resourceType: 'MedicationRequest' } }]
    suggestions[sugIdx] = sug
    handleUpdate(uid, { suggestions })
  }

  const handleUpdateAction = (uid: string, sugIdx: number, actIdx: number, updates: Partial<SuggestionAction>) => {
    const rec = recommendations.find((r) => r.uid === uid)
    if (!rec) return
    const suggestions = [...(rec.suggestions || [])]
    const sug = { ...suggestions[sugIdx] }
    const actions = [...(sug.actions || [])]
    actions[actIdx] = { ...actions[actIdx], ...updates }
    sug.actions = actions
    suggestions[sugIdx] = sug
    handleUpdate(uid, { suggestions })
  }

  const handleRemoveAction = (uid: string, sugIdx: number, actIdx: number) => {
    const rec = recommendations.find((r) => r.uid === uid)
    if (!rec) return
    const suggestions = [...(rec.suggestions || [])]
    const sug = { ...suggestions[sugIdx] }
    sug.actions = (sug.actions || []).filter((_, i) => i !== actIdx)
    suggestions[sugIdx] = sug
    handleUpdate(uid, { suggestions })
  }

  const nonSpecialSubpops = subpopulations.filter((sp) => !sp.special)
  const allSubpopsForRec = [...nonSpecialSubpops, ...SPECIAL_SUBPOPS]

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">Recommendations</Typography>
        <GradientButton startIcon={<AddIcon />} onClick={handleAdd}>
          Add Recommendation
        </GradientButton>
      </Stack>

      {recommendations.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
          <Typography variant="body2">
            No recommendations defined. Add recommendations that will be shown when the CDS rule triggers.
          </Typography>
        </Box>
      ) : (
        <Stack spacing={2}>
          {recommendations.map((rec, index) => (
            <Card key={rec.uid} variant="outlined">
              <CardContent>
                <Stack direction="row" alignItems="center" spacing={1} mb={2}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                    Recommendation {index + 1}
                  </Typography>
                  <Box sx={{ flex: 1 }} />
                  <Tooltip title="Move up">
                    <span>
                      <IconButton size="small" disabled={index === 0} onClick={() => handleMoveUp(index)} aria-label="Move up">
                        <ArrowUpward fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                  <Tooltip title="Move down">
                    <span>
                      <IconButton size="small" disabled={index >= recommendations.length - 1} onClick={() => handleMoveDown(index)} aria-label="Move down">
                        <ArrowDownward fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                  <Tooltip title="Remove recommendation">
                    <IconButton size="small" color="error" onClick={() => setPendingDeleteId(rec.uid)} aria-label="Remove recommendation">
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>

                <FormControlLabel
                  control={
                    <Switch
                      checked={!!rec.cdsCardMode}
                      onChange={(e) => handleUpdate(rec.uid, { cdsCardMode: e.target.checked })}
                      size="small"
                    />
                  }
                  label={<Typography variant="body2">CDS Card Mode</Typography>}
                  sx={{ mb: 1 }}
                />

                <TextField
                  label={rec.cdsCardMode ? 'Summary (Card Headline)' : 'Recommendation Text'}
                  value={rec.text}
                  onChange={(e) => handleUpdate(rec.uid, { text: e.target.value })}
                  fullWidth
                  multiline
                  minRows={2}
                  size="small"
                  sx={{ mb: 2 }}
                  error={!rec.text.trim()}
                  helperText={!rec.text.trim() ? 'Recommendation text is required' : undefined}
                />

                <Collapse in={!!rec.cdsCardMode}>
                  <Stack spacing={2} sx={{ mb: 2 }}>
                    <TextField
                      label="Detail (Card Body)"
                      value={rec.detail || ''}
                      onChange={(e) => handleUpdate(rec.uid, { detail: e.target.value })}
                      fullWidth
                      multiline
                      minRows={2}
                      size="small"
                      placeholder="Markdown-formatted body text for the card..."
                    />
                    <Stack direction="row" spacing={2}>
                      <FormControl size="small" sx={{ minWidth: 160 }}>
                        <InputLabel>Indicator</InputLabel>
                        <Select
                          value={rec.indicator || 'info'}
                          label="Indicator"
                          onChange={(e) => handleUpdate(rec.uid, { indicator: e.target.value })}
                        >
                          <MenuItem value="info">Info</MenuItem>
                          <MenuItem value="warning">Warning</MenuItem>
                          <MenuItem value="critical">Critical</MenuItem>
                        </Select>
                      </FormControl>
                      <TextField
                        label="Source Label"
                        value={rec.sourceLabel || ''}
                        onChange={(e) => handleUpdate(rec.uid, { sourceLabel: e.target.value })}
                        size="small"
                        sx={{ flex: 1 }}
                        placeholder="e.g. CDS Connect"
                      />
                      <FormControl size="small" sx={{ minWidth: 180 }}>
                        <InputLabel>Selection Behavior</InputLabel>
                        <Select
                          value={rec.selectionBehavior || ''}
                          label="Selection Behavior"
                          onChange={(e) => handleUpdate(rec.uid, { selectionBehavior: e.target.value })}
                        >
                          <MenuItem value="">(None)</MenuItem>
                          <MenuItem value="at-most-one">At Most One</MenuItem>
                          <MenuItem value="any">Any</MenuItem>
                        </Select>
                      </FormControl>
                    </Stack>
                  </Stack>
                </Collapse>

                <Stack direction="row" spacing={2} mb={2} alignItems="center">
                  <FormControl size="small" sx={{ minWidth: 260 }}>
                    <InputLabel>Strength of Recommendation</InputLabel>
                    <Select
                      value={rec.grade || ''}
                      label="Strength of Recommendation"
                      onChange={(e) => handleUpdate(rec.uid, { grade: e.target.value })}
                    >
                      {GRADES.map((g) => (
                        <MenuItem key={g.value} value={g.value}>{g.label}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <Tooltip
                    title="Grade A: Strong evidence supports the recommendation. Grade B: Moderate evidence. Grade C: Optional, weak evidence. Grade D: Evidence suggests against. Grade I: Insufficient evidence to recommend."
                    placement="right"
                  >
                    <HelpIcon fontSize="small" sx={{ color: 'text.secondary', cursor: 'help' }} />
                  </Tooltip>
                </Stack>

                <TextField
                  label="Rationale"
                  value={rec.rationale || ''}
                  onChange={(e) => handleUpdate(rec.uid, { rationale: e.target.value })}
                  fullWidth
                  multiline
                  minRows={1}
                  size="small"
                  sx={{ mb: 2 }}
                />

                <TextField
                  label="Comment"
                  value={rec.comment || ''}
                  onChange={(e) => handleUpdate(rec.uid, { comment: e.target.value })}
                  fullWidth
                  size="small"
                  sx={{ mb: 2 }}
                />

                {/* Subpopulation assignment */}
                <Divider sx={{ my: 1.5 }} />
                <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: 'block' }}>
                  Apply to Subpopulations
                </Typography>
                <Stack direction="row" spacing={0.5} flexWrap="wrap" sx={{ mb: 2 }}>
                  {allSubpopsForRec.map((sp) => {
                    const isSelected = (rec.subpopulations || []).some((s) => s.uniqueId === sp.uniqueId)
                    return (
                      <Chip
                        key={sp.uniqueId}
                        label={sp.subpopulationName}
                        size="small"
                        color={isSelected ? (sp.special ? 'warning' : 'primary') : 'default'}
                        variant={isSelected ? 'filled' : 'outlined'}
                        onClick={() => handleToggleSubpop(rec.uid, sp, !isSelected)}
                        sx={{ mb: 0.5 }}
                      />
                    )
                  })}
                </Stack>

                {/* Links */}
                <Divider sx={{ my: 1.5 }} />
                <Stack direction="row" alignItems="center" spacing={1} mb={1}>
                  <Typography variant="caption" color="text.secondary">Links</Typography>
                  <IconButton size="small" onClick={() => handleAddLink(rec.uid)} aria-label="Add link">
                    <LinkOutlined fontSize="small" />
                  </IconButton>
                </Stack>
                {(rec.links || []).map((link, li) => (
                  <Stack key={`link-${rec.uid}-${li}`} direction="row" spacing={1} mb={1} alignItems="center">
                    <FormControl size="small" sx={{ minWidth: 100 }}>
                      <Select
                        value={link.type}
                        onChange={(e) => handleUpdateLink(rec.uid, li, 'type', e.target.value)}
                      >
                        <MenuItem value="absolute">Link</MenuItem>
                        <MenuItem value="smart">SMART App</MenuItem>
                      </Select>
                    </FormControl>
                    <TextField
                      size="small"
                      label="Label"
                      value={link.label}
                      onChange={(e) => handleUpdateLink(rec.uid, li, 'label', e.target.value)}
                      sx={{ flex: 1 }}
                    />
                    <TextField
                      size="small"
                      label="URL"
                      value={link.url}
                      onChange={(e) => handleUpdateLink(rec.uid, li, 'url', e.target.value)}
                      sx={{ flex: 2 }}
                    />
                    <IconButton size="small" color="error" onClick={() => handleRemoveLink(rec.uid, li)} aria-label="Remove link">
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                ))}

                {/* Suggestions */}
                <Divider sx={{ my: 1.5 }} />
                <Stack direction="row" alignItems="center" spacing={1} mb={1}>
                  <Typography variant="caption" color="text.secondary">Suggestions</Typography>
                  <IconButton size="small" onClick={() => handleAddSuggestion(rec.uid)} aria-label="Add suggestion">
                    <AddIcon fontSize="small" />
                  </IconButton>
                </Stack>
                {(rec.suggestions || []).map((sug, si) => (
                  <Card key={sug.uid} variant="outlined" sx={{ mb: 1, backgroundColor: 'action.hover' }}>
                    <CardContent sx={{ py: 1, '&:last-child': { pb: 1 } }}>
                      <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                        <TextField
                          size="small"
                          label="Suggestion Label"
                          value={sug.label}
                          onChange={(e) => handleUpdateSuggestion(rec.uid, si, { label: e.target.value })}
                          sx={{ flex: 1 }}
                        />
                        {rec.cdsCardMode && (
                          <FormControlLabel
                            control={
                              <Switch
                                checked={!!sug.isRecommended}
                                onChange={(e) => handleUpdateSuggestion(rec.uid, si, { isRecommended: e.target.checked })}
                                size="small"
                              />
                            }
                            label={<Typography variant="caption">Recommended</Typography>}
                          />
                        )}
                        <Button size="small" startIcon={<AddIcon />} onClick={() => handleAddAction(rec.uid, si)}>
                          Add Action
                        </Button>
                        <IconButton size="small" color="error" onClick={() => handleRemoveSuggestion(rec.uid, si)} aria-label="Remove suggestion">
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Stack>
                      {(sug.actions || []).map((act, ai) => (
                        <Stack key={`action-${sug.uid}-${ai}`} direction="row" spacing={1} alignItems="center" mb={0.5}>
                          <Chip
                            icon={act.resource?.resourceType === 'MedicationRequest' ? <MedIcon /> : <ServiceIcon />}
                            label="Create"
                            size="small"
                            variant="outlined"
                          />
                          <FormControl size="small" sx={{ minWidth: 180 }}>
                            <Select
                              value={(act.resource?.resourceType as string) || 'MedicationRequest'}
                              onChange={(e) => handleUpdateAction(rec.uid, si, ai, {
                                resource: { ...act.resource, resourceType: e.target.value },
                              })}
                            >
                              <MenuItem value="MedicationRequest">MedicationRequest</MenuItem>
                              <MenuItem value="ServiceRequest">ServiceRequest</MenuItem>
                            </Select>
                          </FormControl>
                          <TextField
                            size="small"
                            label="Description"
                            value={act.description || ''}
                            onChange={(e) => handleUpdateAction(rec.uid, si, ai, { description: e.target.value })}
                            sx={{ flex: 1 }}
                          />
                          <IconButton size="small" color="error" onClick={() => handleRemoveAction(rec.uid, si, ai)} aria-label="Remove action">
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </Stack>
                      ))}
                    </CardContent>
                  </Card>
                ))}
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}

      <Dialog open={!!pendingDeleteId} onClose={() => setPendingDeleteId(null)}>
        <DialogTitle>Delete Recommendation</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to delete Recommendation {pendingDeleteIndex + 1}? This action cannot be undone.
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
