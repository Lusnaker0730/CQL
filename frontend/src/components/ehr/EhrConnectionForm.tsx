import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  MenuItem,
  Stack,
  Alert,
} from '@mui/material'
import { ehrApi } from '../../api'
import type { EhrConnection } from '../../types'
import DepartmentSelector from '../common/DepartmentSelector'

interface EhrConnectionFormProps {
  open: boolean
  connection: EhrConnection | null
  onClose: (saved?: boolean) => void
}

export default function EhrConnectionForm({ open, connection, onClose }: EhrConnectionFormProps) {
  const { t } = useTranslation('fhir')
  const isEdit = !!connection?.id

  const [name, setName] = useState('')
  const [fhirServerUrl, setFhirServerUrl] = useState('')
  const [authType, setAuthType] = useState<'none' | 'basic' | 'bearer'>('none')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [token, setToken] = useState('')
  const [department, setDepartment] = useState('')

  useEffect(() => {
    if (connection) {
      setName(connection.name || '')
      setFhirServerUrl(connection.fhirServerUrl || '')
      setAuthType((connection.authType as 'none' | 'basic' | 'bearer') || 'none')
      setDepartment(connection.department || '')
      // Parse credentials
      if (connection.credentials) {
        try {
          const creds = JSON.parse(connection.credentials)
          if (connection.authType === 'basic') {
            setUsername(creds.username || '')
            setPassword(creds.password || '')
          } else if (connection.authType === 'bearer') {
            setToken(creds.token || '')
          }
        } catch { /* ignore */ }
      }
    } else {
      setName('')
      setFhirServerUrl('')
      setAuthType('none')
      setUsername('')
      setPassword('')
      setToken('')
      setDepartment('')
    }
  }, [connection])

  const buildCredentials = (): string | undefined => {
    if (authType === 'basic') return JSON.stringify({ username, password })
    if (authType === 'bearer') return JSON.stringify({ token })
    return undefined
  }

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload: Partial<EhrConnection> = {
        name,
        fhirServerUrl,
        authType,
        credentials: buildCredentials(),
        department: department || undefined,
      }
      if (isEdit && connection?.id) {
        return ehrApi.updateConnection(connection.id, payload)
      }
      return ehrApi.createConnection(payload)
    },
    onSuccess: () => onClose(true),
  })

  const valid = name.trim() && fhirServerUrl.trim()

  return (
    <Dialog open={open} onClose={() => onClose()} maxWidth="sm" fullWidth>
      <DialogTitle>
        {isEdit ? t('ehr.editConnection') : t('ehr.addConnection')}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {saveMutation.isError && (
            <Alert severity="error">
              {(saveMutation.error as Error).message}
            </Alert>
          )}

          <TextField
            label={t('ehr.connectionName')}
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            fullWidth
            size="small"
          />

          <TextField
            label={t('ehr.serverUrl')}
            value={fhirServerUrl}
            onChange={(e) => setFhirServerUrl(e.target.value)}
            required
            fullWidth
            size="small"
            placeholder="https://fhir.example.com/fhir"
          />

          <TextField
            select
            label={t('ehr.authType')}
            value={authType}
            onChange={(e) => setAuthType(e.target.value as 'none' | 'basic' | 'bearer')}
            fullWidth
            size="small"
          >
            <MenuItem value="none">{t('ehr.authNone')}</MenuItem>
            <MenuItem value="basic">{t('ehr.authBasic')}</MenuItem>
            <MenuItem value="bearer">{t('ehr.authBearer')}</MenuItem>
          </TextField>

          {authType === 'basic' && (
            <>
              <TextField
                label={t('ehr.username')}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                fullWidth
                size="small"
              />
              <TextField
                label={t('ehr.password')}
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                fullWidth
                size="small"
              />
            </>
          )}

          {authType === 'bearer' && (
            <TextField
              label={t('ehr.token')}
              value={token}
              onChange={(e) => setToken(e.target.value)}
              fullWidth
              size="small"
              multiline
              rows={2}
            />
          )}

          <DepartmentSelector
            value={department}
            onChange={setDepartment}
            size="small"
            fullWidth
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => onClose()}>{t('ehr.cancel')}</Button>
        <Button
          variant="contained"
          onClick={() => saveMutation.mutate()}
          disabled={!valid || saveMutation.isPending}
        >
          {t('ehr.saveConnection')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
