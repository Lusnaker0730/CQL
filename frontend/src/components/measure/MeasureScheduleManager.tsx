import { useState } from 'react'
import {
  Box,
  Paper,
  Typography,
  Stack,
  TextField,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  IconButton,
  Switch,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  MenuItem,
  Alert,
} from '@mui/material'
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  PlayArrow as PlayIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import type { MeasureSchedule, MeasureDefinition } from '../../types'

interface MeasureScheduleManagerProps {
  measure: MeasureDefinition
  onClose?: () => void
}

const PRESET_CRONS: Record<string, string> = {
  'Monthly (1st at midnight)': '0 0 1 * *',
  'Quarterly (Jan, Apr, Jul, Oct 1st)': '0 0 1 1,4,7,10 *',
  'Yearly (Jan 1st)': '0 0 1 1 *',
}

export default function MeasureScheduleManager({ measure, onClose }: MeasureScheduleManagerProps) {
  const queryClient = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [newSchedule, setNewSchedule] = useState({
    cronExpression: '0 0 1 * *',
    fhirServerUrl: '',
    periodType: 'monthly',
  })

  const { data: schedules = [], isLoading } = useQuery({
    queryKey: ['schedules', measure.id],
    queryFn: () => measureApi.getSchedules(measure.id!),
    enabled: !!measure.id,
  })

  const createMutation = useMutation({
    mutationFn: (schedule: Partial<MeasureSchedule>) => measureApi.createSchedule(measure.id!, schedule),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['schedules', measure.id] })
      setCreateOpen(false)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<MeasureSchedule> }) => measureApi.updateSchedule(id, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['schedules', measure.id] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => measureApi.deleteSchedule(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['schedules', measure.id] }),
  })

  const triggerMutation = useMutation({
    mutationFn: (id: number) => measureApi.triggerSchedule(id),
  })

  const handleToggle = (schedule: MeasureSchedule) => {
    updateMutation.mutate({ id: schedule.id!, data: { enabled: !schedule.enabled } })
  }

  return (
    <Paper sx={{ p: 2 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h6">Schedules</Typography>
          <Typography variant="body2" color="text.secondary">
            {measure.title || measure.name} v{measure.version}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <GradientButton startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
            Add Schedule
          </GradientButton>
          {onClose && <Button size="small" onClick={onClose}>Close</Button>}
        </Stack>
      </Stack>

      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Enabled</TableCell>
              <TableCell>Cron</TableCell>
              <TableCell>Period</TableCell>
              <TableCell>Last Run</TableCell>
              <TableCell>Next Run</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {schedules.map((s: MeasureSchedule) => (
              <TableRow key={s.id}>
                <TableCell>
                  <Switch size="small" checked={s.enabled} onChange={() => handleToggle(s)} />
                </TableCell>
                <TableCell>
                  <Typography variant="caption" fontFamily="monospace">{s.cronExpression}</Typography>
                </TableCell>
                <TableCell>{s.periodType}</TableCell>
                <TableCell>
                  <Typography variant="caption">
                    {s.lastRunAt ? new Date(s.lastRunAt).toLocaleString() : 'Never'}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="caption">
                    {s.nextRunAt ? new Date(s.nextRunAt).toLocaleString() : '-'}
                  </Typography>
                </TableCell>
                <TableCell>
                  {s.lastRunStatus && (
                    <Chip label={s.lastRunStatus} size="small"
                      color={s.lastRunStatus === 'complete' ? 'success' : 'error'} />
                  )}
                </TableCell>
                <TableCell align="right">
                  <IconButton size="small" color="primary"
                    disabled={triggerMutation.isPending}
                    onClick={() => triggerMutation.mutate(s.id!)}>
                    <PlayIcon fontSize="small" />
                  </IconButton>
                  <IconButton size="small" color="error" onClick={() => deleteMutation.mutate(s.id!)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
            {!isLoading && schedules.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                    No schedules configured.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {triggerMutation.isSuccess && (
        <Alert severity="success" sx={{ mt: 2 }}>
          Manual evaluation completed: {triggerMutation.data?.status}
        </Alert>
      )}

      {/* Create Dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create Schedule</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Preset" select size="small" fullWidth
              value="" onChange={(e) => {
                const cron = PRESET_CRONS[e.target.value]
                if (cron) setNewSchedule({ ...newSchedule, cronExpression: cron })
              }}>
              {Object.keys(PRESET_CRONS).map((label) => (
                <MenuItem key={label} value={label}>{label}</MenuItem>
              ))}
            </TextField>
            <TextField label="Cron Expression" size="small" fullWidth
              value={newSchedule.cronExpression}
              onChange={(e) => setNewSchedule({ ...newSchedule, cronExpression: e.target.value })}
              helperText="Spring cron format (sec min hour dom month dow)" />
            <TextField label="Period Type" select size="small" fullWidth
              value={newSchedule.periodType}
              onChange={(e) => setNewSchedule({ ...newSchedule, periodType: e.target.value })}>
              <MenuItem value="monthly">Monthly</MenuItem>
              <MenuItem value="quarterly">Quarterly</MenuItem>
              <MenuItem value="yearly">Yearly</MenuItem>
            </TextField>
            <TextField label="FHIR Server URL (optional)" size="small" fullWidth
              value={newSchedule.fhirServerUrl}
              onChange={(e) => setNewSchedule({ ...newSchedule, fhirServerUrl: e.target.value })} />
            {createMutation.isError && (
              <Alert severity="error">{(createMutation.error as Error).message}</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button onClick={() => createMutation.mutate(newSchedule)} variant="contained"
            disabled={createMutation.isPending}>
            {createMutation.isPending ? 'Creating...' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  )
}
