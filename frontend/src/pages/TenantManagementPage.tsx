import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Typography,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Chip,
  TextField,
  CircularProgress,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Switch,
  Tooltip,
} from '@mui/material'
import { Add as AddIcon, Group as GroupIcon } from '@mui/icons-material'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../api'
import { tenantApi, type TenantSummary } from '../api/tenantApi'
import { getStoredUsername } from '../utils/validation'

const CODE_PATTERN = /^[a-z0-9][a-z0-9-]{1,49}$/

/**
 * Clinic (tenant) provisioning — platform operator only (PAT-201 / #699).
 * The backend enforces the real boundary (ADMIN + default-tenant guard); a clinic
 * tenant's ADMIN reaching this page gets 403s from every call.
 */
export default function TenantManagementPage() {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const queryClient = useQueryClient()
  const currentUsername = getStoredUsername()

  const [error, setError] = useState<string | null>(null)

  // Create dialog
  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState({ code: '', name: '' })
  const [createLoading, setCreateLoading] = useState(false)

  // Members dialog
  const [membersTenant, setMembersTenant] = useState<TenantSummary | null>(null)
  const [assignUserId, setAssignUserId] = useState<number | ''>('')
  const [assignLoading, setAssignLoading] = useState(false)

  const { data: tenants, isLoading } = useQuery({
    queryKey: ['admin', 'tenants'],
    queryFn: tenantApi.listTenants,
  })

  const { data: members, isLoading: membersLoading } = useQuery({
    queryKey: ['admin', 'tenants', membersTenant?.id, 'users'],
    queryFn: () => tenantApi.listTenantUsers(membersTenant!.id),
    enabled: membersTenant != null,
  })

  const { data: allUsers } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: adminApi.listUsers,
    enabled: membersTenant != null,
  })

  const codeValid = createForm.code === '' || CODE_PATTERN.test(createForm.code)

  const handleCreate = async () => {
    setCreateLoading(true)
    setError(null)
    try {
      await tenantApi.createTenant(createForm)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'tenants'] })
      setCreateOpen(false)
      setCreateForm({ code: '', name: '' })
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('tenants.createFailed'))
    } finally {
      setCreateLoading(false)
    }
  }

  const handleToggleActive = async (tenant: TenantSummary) => {
    setError(null)
    try {
      await tenantApi.setTenantActive(tenant.id, !tenant.active)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'tenants'] })
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('tenants.updateFailed'))
    }
  }

  const handleAssign = async () => {
    if (membersTenant == null || assignUserId === '') return
    setAssignLoading(true)
    setError(null)
    try {
      await tenantApi.assignUser(membersTenant.id, assignUserId)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'tenants', membersTenant.id, 'users'] })
      setAssignUserId('')
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t('tenants.assignFailed'))
    } finally {
      setAssignLoading(false)
    }
  }

  // Users not already in the open tenant — the assign dropdown's option pool.
  // The caller is excluded: self-assignment is rejected server-side (it would
  // invalidate the operator's own session).
  const memberIds = new Set((members ?? []).map((m) => m.id))
  const assignableUsers = (allUsers ?? []).filter(
    (u) => !memberIds.has(u.id) && u.username !== currentUsername
  )

  return (
    <Box sx={{ p: 3, maxWidth: 1000, mx: 'auto' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5">{t('tenants.title')}</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
          {t('tenants.create')}
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('tenants.code')}</TableCell>
              <TableCell>{t('tenants.name')}</TableCell>
              <TableCell>{t('tenants.status')}</TableCell>
              <TableCell>{t('tenants.active')}</TableCell>
              <TableCell>{t('tenants.members')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  <CircularProgress size={24} />
                </TableCell>
              </TableRow>
            )}
            {(tenants ?? []).map((tenant) => (
              <TableRow key={tenant.id}>
                <TableCell>
                  <code>{tenant.code}</code>
                </TableCell>
                <TableCell>{tenant.name}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={tenant.active ? t('tenants.statusActive') : t('tenants.statusInactive')}
                    color={tenant.active ? 'success' : 'default'}
                  />
                </TableCell>
                <TableCell>
                  <Tooltip
                    title={tenant.code === 'default' ? t('tenants.defaultProtected') : ''}
                  >
                    <span>
                      <Switch
                        size="small"
                        checked={tenant.active}
                        disabled={tenant.code === 'default'}
                        onChange={() => handleToggleActive(tenant)}
                        slotProps={{ input: { 'aria-label': t('tenants.active') } }}
                      />
                    </span>
                  </Tooltip>
                </TableCell>
                <TableCell>
                  <Button
                    size="small"
                    startIcon={<GroupIcon />}
                    onClick={() => setMembersTenant(tenant)}
                  >
                    {t('tenants.viewMembers')}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Create tenant dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>{t('tenants.createTitle')}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            margin="dense"
            label={t('tenants.code')}
            value={createForm.code}
            error={!codeValid}
            helperText={t('tenants.codeHelp')}
            onChange={(e) => setCreateForm({ ...createForm, code: e.target.value })}
          />
          <TextField
            fullWidth
            margin="dense"
            label={t('tenants.name')}
            value={createForm.name}
            onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>{tc('actions.cancel')}</Button>
          <Button
            variant="contained"
            disabled={
              createLoading || createForm.code === '' || createForm.name === '' || !codeValid
            }
            onClick={handleCreate}
          >
            {createLoading ? <CircularProgress size={20} /> : t('tenants.create')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Members dialog */}
      <Dialog
        open={membersTenant != null}
        onClose={() => setMembersTenant(null)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>
          {t('tenants.membersTitle', { name: membersTenant?.name ?? '' })}
        </DialogTitle>
        <DialogContent>
          {membersLoading ? (
            <CircularProgress size={24} />
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{t('tenants.username')}</TableCell>
                  <TableCell>{t('tenants.role')}</TableCell>
                  <TableCell>{t('tenants.enabled')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(members ?? []).map((m) => (
                  <TableRow key={m.id}>
                    <TableCell>{m.username}</TableCell>
                    <TableCell>{m.role}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={m.enabled ? t('tenants.userEnabled') : t('tenants.userDisabled')}
                        color={m.enabled ? 'success' : 'default'}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}

          {membersTenant?.active && (
            <Box sx={{ display: 'flex', gap: 1, mt: 2, alignItems: 'center' }}>
              <FormControl size="small" sx={{ minWidth: 220 }}>
                <InputLabel id="assign-user-label">{t('tenants.assignUser')}</InputLabel>
                <Select
                  labelId="assign-user-label"
                  label={t('tenants.assignUser')}
                  value={assignUserId}
                  onChange={(e) => setAssignUserId(e.target.value as number | '')}
                >
                  {assignableUsers.map((u) => (
                    <MenuItem key={u.id} value={u.id}>
                      {u.username} ({u.role})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <Button
                variant="contained"
                size="small"
                disabled={assignUserId === '' || assignLoading}
                onClick={handleAssign}
              >
                {assignLoading ? <CircularProgress size={18} /> : t('tenants.assign')}
              </Button>
            </Box>
          )}
          {membersTenant?.active && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
              {t('tenants.assignNote')}
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setMembersTenant(null)}>{tc('actions.close')}</Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
