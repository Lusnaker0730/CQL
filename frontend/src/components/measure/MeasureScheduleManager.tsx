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
import { useTranslation } from 'react-i18next'
import GradientButton from '../common/GradientButton'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import type { MeasureSchedule, MeasureDefinition } from '../../types'

interface MeasureScheduleManagerProps {
  measure: MeasureDefinition
  onClose?: () => void
}

const PRESET_CRON_VALUES: Record<string, string> = {
  monthly: '0 0 1 * *',
  quarterly: '0 0 1 1,4,7,10 *',
  yearly: '0 0 1 1 *',
}

export default function MeasureScheduleManager({ measure, onClose }: MeasureScheduleManagerProps) {
  const { t } = useTranslation('measures')
  const queryClient = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [newSchedule, setNewSchedule] = useState({
    cronExpression: '0 0 1 * *',
    fhirServerUrl: '',
    periodType: 'monthly',
  })

  const presetCrons: Record<string, string> = {
    [t('schedules.presets.monthly')]: PRESET_CRON_VALUES.monthly,
    [t('schedules.presets.quarterly')]: PRESET_CRON_VALUES.quarterly,
    [t('schedules.presets.yearly')]: PRESET_CRON_VALUES.yearly,
  }

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
          <Typography variant="h6">{t('schedules.title')}</Typography>
          <Typography variant="body2" color="text.secondary">
            {measure.title || measure.name} v{measure.version}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <GradientButton startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
            {t('schedules.addSchedule')}
          </GradientButton>
          {onClose && <Button size="small" onClick={onClose}>{t('schedules.close')}</Button>}
        </Stack>
      </Stack>

      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell scope="col">{t('schedules.tableHeaders.enabled')}</TableCell>
              <TableCell scope="col">{t('schedules.tableHeaders.cron')}</TableCell>
              <TableCell scope="col">{t('schedules.tableHeaders.period')}</TableCell>
              <TableCell scope="col">{t('schedules.tableHeaders.lastRun')}</TableCell>
              <TableCell scope="col">{t('schedules.tableHeaders.nextRun')}</TableCell>
              <TableCell scope="col">{t('schedules.tableHeaders.status')}</TableCell>
              <TableCell scope="col" align="right">{t('schedules.tableHeaders.actions')}</TableCell>
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
                    {s.lastRunAt ? new Date(s.lastRunAt).toLocaleString() : t('schedules.never')}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="caption">
                    {s.nextRunAt ? new Date(s.nextRunAt).toLocaleString() : t('schedules.noNextRun')}
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
                    aria-label={t('schedules.triggerSchedule')}
                    disabled={triggerMutation.isPending}
                    onClick={() => triggerMutation.mutate(s.id!)}>
                    <PlayIcon fontSize="small" />
                  </IconButton>
                  <IconButton size="small" aria-label={t('schedules.deleteSchedule')} color="error" onClick={() => deleteMutation.mutate(s.id!)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
            {!isLoading && schedules.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                    {t('schedules.emptyState')}
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {triggerMutation.isSuccess && (
        <Alert severity="success" sx={{ mt: 2 }}>
          {t('schedules.manualComplete', { status: triggerMutation.data?.status })}
        </Alert>
      )}

      {/* Create Dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('schedules.createDialog.title')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label={t('schedules.createDialog.preset')} select size="small" fullWidth
              value="" onChange={(e) => {
                const cron = presetCrons[e.target.value]
                if (cron) setNewSchedule({ ...newSchedule, cronExpression: cron })
              }}>
              {Object.keys(presetCrons).map((label) => (
                <MenuItem key={label} value={label}>{label}</MenuItem>
              ))}
            </TextField>
            <TextField label={t('schedules.createDialog.cronExpression')} size="small" fullWidth
              value={newSchedule.cronExpression}
              onChange={(e) => setNewSchedule({ ...newSchedule, cronExpression: e.target.value })}
              helperText={t('schedules.createDialog.cronHelper')} />
            <TextField label={t('schedules.createDialog.periodType')} select size="small" fullWidth
              value={newSchedule.periodType}
              onChange={(e) => setNewSchedule({ ...newSchedule, periodType: e.target.value })}>
              <MenuItem value="monthly">{t('schedules.createDialog.monthly')}</MenuItem>
              <MenuItem value="quarterly">{t('schedules.createDialog.quarterly')}</MenuItem>
              <MenuItem value="yearly">{t('schedules.createDialog.yearly')}</MenuItem>
            </TextField>
            <TextField label={t('schedules.createDialog.fhirServerUrl')} size="small" fullWidth
              value={newSchedule.fhirServerUrl}
              onChange={(e) => setNewSchedule({ ...newSchedule, fhirServerUrl: e.target.value })} />
            {createMutation.isError && (
              <Alert severity="error">{(createMutation.error as Error).message}</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>{t('actions.cancel', { ns: 'common' })}</Button>
          <Button onClick={() => createMutation.mutate(newSchedule)} variant="contained"
            disabled={createMutation.isPending}>
            {createMutation.isPending ? t('schedules.createDialog.creating') : t('schedules.createDialog.create')}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  )
}
