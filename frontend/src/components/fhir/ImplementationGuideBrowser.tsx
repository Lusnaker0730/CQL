import { useState, useEffect, useMemo } from 'react'
import { SEARCH_DEBOUNCE_GENERAL_MS } from '../../constants/timing'
import {
  Box,
  Paper,
  Typography,
  Stack,
  TextField,
  Tabs,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  CircularProgress,
  Alert,
  InputAdornment,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Card,
  CardContent,
} from '@mui/material'
import {
  Search as SearchIcon,
  AccountTree as ProfileIcon,
  ListAlt as ValueSetIcon,
  Code as CodeSystemIcon,
  Inventory as PackageIcon,
  Close as CloseIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useIgPackages, useIgProfiles, useIgValueSets, useIgCodeSystems } from '../../hooks/useImplementationGuide'
import { fhirApi } from '../../api'
import type { ProfileSummary, ValueSetSummary, CodeSystemSummary } from '../../types'
import StatusChip from '../common/StatusChip'
import CardListSkeleton from '../common/CardListSkeleton'
import { extractApiError } from '../../utils/errorUtils'

interface DetailDialogState {
  open: boolean
  title: string
  json: string
}

export default function ImplementationGuideBrowser() {
  const { t } = useTranslation('fhir')
  const [tabIndex, setTabIndex] = useState(0)

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Tabs
        value={tabIndex}
        onChange={(_, v) => setTabIndex(v)}
        sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}
      >
        <Tab icon={<PackageIcon />} iconPosition="start" label={t('ig.tabPackages')} />
        <Tab icon={<ProfileIcon />} iconPosition="start" label={t('ig.tabProfiles')} />
        <Tab icon={<ValueSetIcon />} iconPosition="start" label={t('ig.tabValueSets')} />
        <Tab icon={<CodeSystemIcon />} iconPosition="start" label={t('ig.tabCodeSystems')} />
      </Tabs>

      <Box sx={{ flex: 1, overflow: 'auto' }}>
        {tabIndex === 0 && <PackagesTab />}
        {tabIndex === 1 && <ProfilesTab />}
        {tabIndex === 2 && <ValueSetsTab />}
        {tabIndex === 3 && <CodeSystemsTab />}
      </Box>
    </Box>
  )
}

function PackagesTab() {
  const { t } = useTranslation('fhir')
  const { data: packages, isLoading, error } = useIgPackages()

  if (isLoading) return <CardListSkeleton />
  if (error) return <Alert severity="error">{t('ig.packageLoadError', { error: extractApiError(error) })}</Alert>
  if (!packages || packages.length === 0) {
    return <Alert severity="info">{t('ig.noPackages')}</Alert>
  }

  return (
    <Stack spacing={2}>
      {packages.map((pkg) => (
        <Card key={pkg.name} variant="outlined">
          <CardContent>
            <Typography variant="h6" gutterBottom>{pkg.title}</Typography>
            <Stack spacing={1}>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={t('ig.nameLabel', { name: pkg.name })} size="small" variant="outlined" />
                <Chip label={t('ig.versionLabel', { version: pkg.version })} size="small" variant="outlined" />
                <Chip label={t('ig.fhirLabel', { fhirVersion: pkg.fhirVersion })} size="small" variant="outlined" />
              </Stack>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                {t('ig.canonicalLabel', { canonical: pkg.canonical })}
              </Typography>
              <Stack direction="row" spacing={2} sx={{ mt: 1 }}>
                <Paper variant="outlined" sx={{ p: 1.5, textAlign: 'center', flex: 1 }}>
                  <Typography variant="h5" color="primary.main">{pkg.profileCount}</Typography>
                  <Typography variant="caption" color="text.secondary">{t('ig.profiles')}</Typography>
                </Paper>
                <Paper variant="outlined" sx={{ p: 1.5, textAlign: 'center', flex: 1 }}>
                  <Typography variant="h5" color="primary.main">{pkg.valueSetCount}</Typography>
                  <Typography variant="caption" color="text.secondary">{t('ig.valueSets')}</Typography>
                </Paper>
                <Paper variant="outlined" sx={{ p: 1.5, textAlign: 'center', flex: 1 }}>
                  <Typography variant="h5" color="primary.main">{pkg.codeSystemCount}</Typography>
                  <Typography variant="caption" color="text.secondary">{t('ig.codeSystems')}</Typography>
                </Paper>
              </Stack>
            </Stack>
          </CardContent>
        </Card>
      ))}
    </Stack>
  )
}

function ProfilesTab() {
  const { t } = useTranslation('fhir')
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [resourceType, setResourceType] = useState<string>('')
  const [detail, setDetail] = useState<DetailDialogState>({ open: false, title: '', json: '' })
  const [loadingDetail, setLoadingDetail] = useState(false)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), SEARCH_DEBOUNCE_GENERAL_MS)
    return () => clearTimeout(timer)
  }, [search])

  const { data: profiles, isLoading, error } = useIgProfiles(resourceType || undefined, debouncedSearch || undefined)

  const resourceTypes = useMemo(() => {
    if (!profiles) return []
    const types = new Set(profiles.map((p: ProfileSummary) => p.type).filter(Boolean))
    return Array.from(types).sort()
  }, [profiles])

  const handleRowClick = async (profile: ProfileSummary) => {
    setLoadingDetail(true)
    try {
      const json = await fhirApi.getProfile(profile.url)
      setDetail({ open: true, title: profile.title || profile.name, json: JSON.stringify(json, null, 2) })
    } catch {
      setDetail({ open: true, title: t('ig.loadError'), json: t('ig.loadProfileFailed') })
    }
    setLoadingDetail(false)
  }

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2}>
        <TextField
          label={t('ig.searchProfiles')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          size="small"
          sx={{ flex: 1 }}
          placeholder={t('ig.searchProfilesPlaceholder')}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon sx={{ color: 'text.secondary' }} />
              </InputAdornment>
            ),
          }}
        />
        <FormControl size="small" sx={{ minWidth: 180 }}>
          <InputLabel>{t('browser.resourceTypeLabel')}</InputLabel>
          <Select
            value={resourceType}
            label={t('browser.resourceTypeLabel')}
            onChange={(e) => setResourceType(e.target.value)}
          >
            <MenuItem value="">{t('ig.allTypes')}</MenuItem>
            {resourceTypes.map((rt) => (
              <MenuItem key={rt} value={rt}>{rt}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </Stack>

      {isLoading && <CircularProgress size={24} />}
      {error && <Alert severity="error">{t('ig.profileLoadError', { error: extractApiError(error) })}</Alert>}

      {profiles && (
        <>
          <Typography variant="subtitle2" color="text.secondary">
            {t('ig.profileCount', { count: profiles.length })}
          </Typography>
          <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 500 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colName')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colTitle')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colType')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colKind')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colStatus')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {profiles.map((p: ProfileSummary) => (
                  <TableRow
                    key={p.url}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => handleRowClick(p)}
                  >
                    <TableCell>
                      <Typography variant="body2" fontWeight={500}>{p.name}</Typography>
                    </TableCell>
                    <TableCell>{p.title}</TableCell>
                    <TableCell>
                      <Chip label={p.type} size="small" variant="outlined" />
                    </TableCell>
                    <TableCell>{p.kind}</TableCell>
                    <TableCell>
                      <StatusChip status={p.status || 'draft'} />
                    </TableCell>
                  </TableRow>
                ))}
                {profiles.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Typography variant="body2" color="text.secondary">{t('ig.noProfiles')}</Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}

      <DetailDialog state={detail} onClose={() => setDetail({ ...detail, open: false })} loading={loadingDetail} />
    </Stack>
  )
}

function ValueSetsTab() {
  const { t } = useTranslation('fhir')
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [detail, setDetail] = useState<DetailDialogState>({ open: false, title: '', json: '' })
  const [loadingDetail, setLoadingDetail] = useState(false)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), SEARCH_DEBOUNCE_GENERAL_MS)
    return () => clearTimeout(timer)
  }, [search])

  const { data: valueSets, isLoading, error } = useIgValueSets(debouncedSearch || undefined)

  const handleRowClick = async (vs: ValueSetSummary) => {
    setLoadingDetail(true)
    try {
      const json = await fhirApi.getIgValueSet(vs.url)
      setDetail({ open: true, title: vs.title || vs.name, json: JSON.stringify(json, null, 2) })
    } catch {
      setDetail({ open: true, title: t('ig.loadError'), json: t('ig.loadValueSetFailed') })
    }
    setLoadingDetail(false)
  }

  return (
    <Stack spacing={2}>
      <TextField
        label={t('ig.searchValueSets')}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        size="small"
        fullWidth
        placeholder={t('ig.searchValueSetsPlaceholder')}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon sx={{ color: 'text.secondary' }} />
            </InputAdornment>
          ),
        }}
      />

      {isLoading && <CircularProgress size={24} />}
      {error && <Alert severity="error">{t('ig.valueSetLoadError', { error: extractApiError(error) })}</Alert>}

      {valueSets && (
        <>
          <Typography variant="subtitle2" color="text.secondary">
            {t('ig.valueSetCount', { count: valueSets.length })}
          </Typography>
          <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 500 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colTitle')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colUrl')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colStatus')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colConcepts')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {valueSets.map((vs: ValueSetSummary) => (
                  <TableRow
                    key={vs.url}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => handleRowClick(vs)}
                  >
                    <TableCell>
                      <Typography variant="body2" fontWeight={500}>{vs.title || vs.name}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" noWrap sx={{ maxWidth: 300, display: 'block' }}>
                        {vs.url}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <StatusChip status={vs.status || 'draft'} />
                    </TableCell>
                    <TableCell>
                      <Chip label={vs.conceptCount} size="small" sx={{ bgcolor: 'rgba(13,115,119,0.1)', color: 'primary.dark', fontWeight: 600 }} />
                    </TableCell>
                  </TableRow>
                ))}
                {valueSets.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4}>
                      <Typography variant="body2" color="text.secondary">{t('ig.noValueSets')}</Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}

      <DetailDialog state={detail} onClose={() => setDetail({ ...detail, open: false })} loading={loadingDetail} />
    </Stack>
  )
}

function CodeSystemsTab() {
  const { t } = useTranslation('fhir')
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [detail, setDetail] = useState<DetailDialogState>({ open: false, title: '', json: '' })
  const [loadingDetail, setLoadingDetail] = useState(false)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), SEARCH_DEBOUNCE_GENERAL_MS)
    return () => clearTimeout(timer)
  }, [search])

  const { data: codeSystems, isLoading, error } = useIgCodeSystems(debouncedSearch || undefined)

  const handleRowClick = async (cs: CodeSystemSummary) => {
    setLoadingDetail(true)
    try {
      const json = await fhirApi.getIgCodeSystem(cs.url)
      setDetail({ open: true, title: cs.title || cs.name, json: JSON.stringify(json, null, 2) })
    } catch {
      setDetail({ open: true, title: t('ig.loadError'), json: t('ig.loadCodeSystemFailed') })
    }
    setLoadingDetail(false)
  }

  return (
    <Stack spacing={2}>
      <TextField
        label={t('ig.searchCodeSystems')}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        size="small"
        fullWidth
        placeholder={t('ig.searchCodeSystemsPlaceholder')}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon sx={{ color: 'text.secondary' }} />
            </InputAdornment>
          ),
        }}
      />

      {isLoading && <CircularProgress size={24} />}
      {error && <Alert severity="error">{t('ig.codeSystemLoadError', { error: extractApiError(error) })}</Alert>}

      {codeSystems && (
        <>
          <Typography variant="subtitle2" color="text.secondary">
            {t('ig.codeSystemCount', { count: codeSystems.length })}
          </Typography>
          <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 500 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colTitle')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colUrl')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colStatus')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('ig.colConcepts')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {codeSystems.map((cs: CodeSystemSummary) => (
                  <TableRow
                    key={cs.url}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => handleRowClick(cs)}
                  >
                    <TableCell>
                      <Typography variant="body2" fontWeight={500}>{cs.title || cs.name}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" noWrap sx={{ maxWidth: 300, display: 'block' }}>
                        {cs.url}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <StatusChip status={cs.status || 'draft'} />
                    </TableCell>
                    <TableCell>
                      <Chip label={cs.conceptCount} size="small" sx={{ bgcolor: 'rgba(13,115,119,0.1)', color: 'primary.dark', fontWeight: 600 }} />
                    </TableCell>
                  </TableRow>
                ))}
                {codeSystems.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4}>
                      <Typography variant="body2" color="text.secondary">{t('ig.noCodeSystems')}</Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}

      <DetailDialog state={detail} onClose={() => setDetail({ ...detail, open: false })} loading={loadingDetail} />
    </Stack>
  )
}

function DetailDialog({ state, onClose, loading }: { state: DetailDialogState; onClose: () => void; loading: boolean }) {
  const { t } = useTranslation('fhir')
  const { t: tc } = useTranslation('common')

  return (
    <Dialog open={state.open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">{state.title}</Typography>
          <Button onClick={onClose} startIcon={<CloseIcon />} size="small">{tc('actions.close')}</Button>
        </Stack>
      </DialogTitle>
      <DialogContent>
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        ) : (
          <Box
            component="pre"
            sx={{
              p: 2,
              bgcolor: '#1e1e1e',
              color: '#d4d4d4',
              borderRadius: 1,
              overflow: 'auto',
              maxHeight: 500,
              fontSize: '0.8rem',
              fontFamily: '"Fira Code", "Cascadia Code", monospace',
            }}
          >
            {state.json}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button
          size="small"
          onClick={() => navigator.clipboard.writeText(state.json)}
        >
          {t('ig.copyJson')}
        </Button>
        <Button onClick={onClose}>{tc('actions.close')}</Button>
      </DialogActions>
    </Dialog>
  )
}
