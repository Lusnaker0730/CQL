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
import { EHR_CONNECTION } from '../../constants/fieldConstraints'

type AuthType = 'none' | 'basic' | 'bearer' | 'smart_backend'

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
  const [authType, setAuthType] = useState<AuthType>('none')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [token, setToken] = useState('')
  const [department, setDepartment] = useState('')
  // SMART Backend Services fields
  const [clientId, setClientId] = useState('')
  const [tokenEndpoint, setTokenEndpoint] = useState('')
  const [scopes, setScopes] = useState('')
  const [privateKey, setPrivateKey] = useState('')

  useEffect(() => {
    if (connection) {
      setName(connection.name || '')
      setFhirServerUrl(connection.fhirServerUrl || '')
      setAuthType((connection.authType as AuthType) || 'none')
      setDepartment(connection.department || '')
      setTokenEndpoint(connection.tokenEndpoint || '')
      // Parse credentials
      if (connection.credentials) {
        try {
          const creds = JSON.parse(connection.credentials)
          if (connection.authType === 'basic') {
            setUsername(creds.username || '')
            setPassword(creds.password || '')
          } else if (connection.authType === 'bearer') {
            setToken(creds.token || '')
          } else if (connection.authType === 'smart_backend') {
            setClientId(creds.clientId || '')
            setTokenEndpoint(creds.tokenEndpoint || connection.tokenEndpoint || '')
            setScopes(creds.scopes || '')
            setPrivateKey(creds.privateKey || '')
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
      setClientId('')
      setTokenEndpoint('')
      setScopes('')
      setPrivateKey('')
    }
  }, [connection])

  const buildCredentials = (): string | undefined => {
    if (authType === 'basic') return JSON.stringify({ username, password })
    if (authType === 'bearer') return JSON.stringify({ token })
    if (authType === 'smart_backend') return JSON.stringify({ clientId, scopes, privateKey })
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
        tokenEndpoint: authType === 'smart_backend' ? tokenEndpoint : undefined,
      }
      if (isEdit && connection?.id) {
        return ehrApi.updateConnection(connection.id, payload)
      }
      return ehrApi.createConnection(payload)
    },
    onSuccess: () => onClose(true),
  })

  const smartBackendValid = authType !== 'smart_backend' || (clientId.trim() && tokenEndpoint.trim() && privateKey.trim())
  const valid = name.trim() && fhirServerUrl.trim() && smartBackendValid

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
            inputProps={{ maxLength: EHR_CONNECTION.name.maxLength }}
          />

          <TextField
            label={t('ehr.serverUrl')}
            value={fhirServerUrl}
            onChange={(e) => setFhirServerUrl(e.target.value)}
            required
            fullWidth
            size="small"
            placeholder={t('ehr.fhirUrlPlaceholder')}
            inputProps={{ maxLength: EHR_CONNECTION.fhirServerUrl.maxLength }}
          />

          <TextField
            select
            label={t('ehr.authType')}
            value={authType}
            onChange={(e) => setAuthType(e.target.value as AuthType)}
            fullWidth
            size="small"
          >
            <MenuItem value="none">{t('ehr.authNone')}</MenuItem>
            <MenuItem value="basic">{t('ehr.authBasic')}</MenuItem>
            <MenuItem value="bearer">{t('ehr.authBearer')}</MenuItem>
            <MenuItem value="smart_backend">{t('ehr.authSmartBackend')}</MenuItem>
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

          {authType === 'smart_backend' && (
            <>
              <TextField
                label={t('ehr.clientId')}
                value={clientId}
                onChange={(e) => setClientId(e.target.value)}
                required
                fullWidth
                size="small"
              />
              <TextField
                label={t('ehr.tokenEndpoint')}
                value={tokenEndpoint}
                onChange={(e) => setTokenEndpoint(e.target.value)}
                required
                fullWidth
                size="small"
                placeholder="https://auth.example.com/token"
                inputProps={{ maxLength: EHR_CONNECTION.tokenEndpoint.maxLength }}
              />
              <TextField
                label={t('ehr.scopes')}
                value={scopes}
                onChange={(e) => setScopes(e.target.value)}
                fullWidth
                size="small"
                placeholder={t('ehr.scopesPlaceholder')}
              />
              <TextField
                label={t('ehr.privateKey')}
                value={privateKey}
                onChange={(e) => setPrivateKey(e.target.value)}
                required
                fullWidth
                size="small"
                multiline
                rows={6}
                helperText={t('ehr.privateKeyHelperText')}
                inputProps={{ style: { fontFamily: 'monospace', fontSize: '0.8rem' } }}
                placeholder={'-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----'}
              />
            </>
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
