import { useState, useMemo, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Button, Checkbox, CircularProgress, Dialog, DialogTitle,
  DialogContent, DialogContentText, DialogActions, Stack, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material'
import {
  Compare as CompareIcon,
  NoteAdd as DraftIcon,
} from '@mui/icons-material'
import { useCqlLibraryHistory, useCreateCqlLibrary } from '../../hooks/useCqlLibraries'
import { useNotification } from '../../hooks/useNotification'
import { extractApiError } from '../../utils/errorUtils'
import StatusChip from '../common/StatusChip'
import SectionHeader from '../common/SectionHeader'
import VersionCompareDialog from './VersionCompareDialog'
import type { CqlLibrary } from '../../types'

interface CqlLibraryHistoryTabProps {
  libraryName: string
  currentVersion: string
  onLoadVersion?: (libraryId: string) => void
}

export default function CqlLibraryHistoryTab({
  libraryName,
  currentVersion,
  onLoadVersion,
}: CqlLibraryHistoryTabProps) {
  const { t } = useTranslation('cqlLibraries')
  const { showNotification } = useNotification()
  const { data: versions, isLoading } = useCqlLibraryHistory(libraryName)
  const createMutation = useCreateCqlLibrary()

  // Compare selection state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [compareOpen, setCompareOpen] = useState(false)

  // Create draft confirmation state
  const [draftSource, setDraftSource] = useState<CqlLibrary | null>(null)

  const sortedVersions = useMemo(() => {
    if (!versions) return []
    return [...versions].sort((a, b) => {
      return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
    })
  }, [versions])

  const handleToggleSelect = useCallback((id: string) => {
    setSelectedIds(prev => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        if (next.size >= 2) return prev
        next.add(id)
      }
      return next
    })
  }, [])

  const compareVersions = useMemo(() => {
    if (selectedIds.size !== 2 || !sortedVersions.length) return { old: null, new: null }
    const selected = sortedVersions.filter(v => selectedIds.has(v.id))
    if (selected.length !== 2) return { old: null, new: null }
    // Older version first (smaller updatedAt)
    const [a, b] = selected
    const aTime = new Date(a.updatedAt).getTime()
    const bTime = new Date(b.updatedAt).getTime()
    return aTime <= bTime
      ? { old: a, new: b }
      : { old: b, new: a }
  }, [selectedIds, sortedVersions])

  const handleCompare = useCallback(() => {
    if (compareVersions.old && compareVersions.new) {
      setCompareOpen(true)
    }
  }, [compareVersions])

  const handleCreateDraft = useCallback(() => {
    if (!draftSource) return
    createMutation.mutate(
      { cql: draftSource.cqlContent, description: draftSource.description },
      {
        onSuccess: () => {
          showNotification(t('import.importSuccess'), 'success')
          setDraftSource(null)
        },
        onError: (err) => {
          showNotification(
            t('errors.createFailed', { error: extractApiError(err) }),
            'error',
          )
          setDraftSource(null)
        },
      },
    )
  }, [draftSource, createMutation, showNotification, t])

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress />
      </Box>
    )
  }

  return (
    <Box>
      <SectionHeader
        title={t('history.title')}
        actions={
          sortedVersions.length >= 2 ? (
            <Button
              size="small"
              startIcon={<CompareIcon />}
              onClick={handleCompare}
              disabled={selectedIds.size !== 2}
            >
              {t('compare.compareSelected')}
            </Button>
          ) : undefined
        }
      />

      {selectedIds.size > 0 && selectedIds.size < 2 && (
        <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: 'block' }}>
          {t('compare.selectTwo')}
        </Typography>
      )}

      {sortedVersions.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {t('history.noHistory')}
        </Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                {sortedVersions.length >= 2 && (
                  <TableCell padding="checkbox" />
                )}
                <TableCell>{t('history.version')}</TableCell>
                <TableCell>{t('history.status')}</TableCell>
                <TableCell>{t('history.date')}</TableCell>
                <TableCell align="right">{t('history.actions')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedVersions.map((ver) => {
                const isCurrent = ver.version === currentVersion
                const isSelected = selectedIds.has(ver.id)
                return (
                  <TableRow
                    key={ver.id}
                    sx={{
                      bgcolor: isCurrent ? 'action.selected' : undefined,
                    }}
                  >
                    {sortedVersions.length >= 2 && (
                      <TableCell padding="checkbox">
                        <Checkbox
                          size="small"
                          checked={isSelected}
                          disabled={!isSelected && selectedIds.size >= 2}
                          onChange={() => handleToggleSelect(ver.id)}
                        />
                      </TableCell>
                    )}
                    <TableCell>
                      <Typography variant="body2" fontWeight={isCurrent ? 600 : 400}>
                        v{ver.version}
                        {isCurrent && (
                          <Typography
                            component="span"
                            variant="caption"
                            color="primary"
                            sx={{ ml: 1 }}
                          >
                            {t('history.current')}
                          </Typography>
                        )}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <StatusChip status={ver.status} />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {new Date(ver.updatedAt).toLocaleString()}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                        {!isCurrent && onLoadVersion && (
                          <Button
                            size="small"
                            onClick={() => onLoadVersion(ver.id)}
                          >
                            {t('history.loadVersion')}
                          </Button>
                        )}
                        {ver.status !== 'draft' && (
                          <Button
                            size="small"
                            startIcon={<DraftIcon />}
                            onClick={() => setDraftSource(ver)}
                          >
                            {t('history.createDraftFrom')}
                          </Button>
                        )}
                      </Stack>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Create Draft Confirmation */}
      <Dialog open={draftSource !== null} onClose={() => setDraftSource(null)} maxWidth="xs" fullWidth>
        <DialogTitle>{t('draft.createTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('draft.createMessage', { version: draftSource?.version || '' })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDraftSource(null)} disabled={createMutation.isPending}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            variant="contained"
            onClick={handleCreateDraft}
            disabled={createMutation.isPending}
          >
            {createMutation.isPending ? t('draft.creating') : t('draft.createTitle')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Version Compare Dialog */}
      <VersionCompareDialog
        open={compareOpen}
        onClose={() => setCompareOpen(false)}
        oldVersion={compareVersions.old}
        newVersion={compareVersions.new}
      />
    </Box>
  )
}
