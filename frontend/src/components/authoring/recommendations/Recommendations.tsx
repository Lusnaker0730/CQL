import { useCallback } from 'react'
import {
  Box, Stack, Typography, IconButton, Tooltip, TextField, Card, CardContent,
  MenuItem, Select, FormControl, InputLabel, Chip, Divider,
} from '@mui/material'
import {
  Add as AddIcon, Delete as DeleteIcon, ArrowUpward, ArrowDownward,
  LinkOutlined,
} from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import type { Recommendation, Subpopulation } from '../../../types/authoring'

function generateId(): string {
  return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
}

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

  const nonSpecialSubpops = subpopulations.filter((sp) => !sp.special)

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
                      <IconButton size="small" disabled={index === 0} onClick={() => handleMoveUp(index)}>
                        <ArrowUpward fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                  <Tooltip title="Move down">
                    <span>
                      <IconButton size="small" disabled={index >= recommendations.length - 1} onClick={() => handleMoveDown(index)}>
                        <ArrowDownward fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                  <Tooltip title="Remove recommendation">
                    <IconButton size="small" color="error" onClick={() => handleRemove(rec.uid)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>

                <TextField
                  label="Recommendation Text"
                  value={rec.text}
                  onChange={(e) => handleUpdate(rec.uid, { text: e.target.value })}
                  fullWidth
                  multiline
                  minRows={2}
                  size="small"
                  sx={{ mb: 2 }}
                />

                <Stack direction="row" spacing={2} mb={2}>
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
                {nonSpecialSubpops.length > 0 && (
                  <>
                    <Divider sx={{ my: 1.5 }} />
                    <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: 'block' }}>
                      Apply to Subpopulations
                    </Typography>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" sx={{ mb: 2 }}>
                      {nonSpecialSubpops.map((sp) => {
                        const isSelected = (rec.subpopulations || []).some((s) => s.uniqueId === sp.uniqueId)
                        return (
                          <Chip
                            key={sp.uniqueId}
                            label={sp.subpopulationName}
                            size="small"
                            color={isSelected ? 'primary' : 'default'}
                            variant={isSelected ? 'filled' : 'outlined'}
                            onClick={() => handleToggleSubpop(rec.uid, sp, !isSelected)}
                            sx={{ mb: 0.5 }}
                          />
                        )
                      })}
                    </Stack>
                  </>
                )}

                {/* Links */}
                <Divider sx={{ my: 1.5 }} />
                <Stack direction="row" alignItems="center" spacing={1} mb={1}>
                  <Typography variant="caption" color="text.secondary">Links</Typography>
                  <IconButton size="small" onClick={() => handleAddLink(rec.uid)}>
                    <LinkOutlined fontSize="small" />
                  </IconButton>
                </Stack>
                {(rec.links || []).map((link, li) => (
                  <Stack key={li} direction="row" spacing={1} mb={1} alignItems="center">
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
                    <IconButton size="small" color="error" onClick={() => handleRemoveLink(rec.uid, li)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                ))}
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}
    </Box>
  )
}
