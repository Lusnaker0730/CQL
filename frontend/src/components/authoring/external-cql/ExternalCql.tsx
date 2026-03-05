import { useState, useCallback, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Stack, Typography, Alert, CircularProgress, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, IconButton, Tooltip, Chip, Dialog,
  DialogTitle, DialogContent, DialogActions, Button, Paper,
} from '@mui/material'
import {
  Delete as DeleteIcon, Visibility as ViewIcon, CloudUpload as UploadIcon,
  AutoFixHigh as ApplyIcon,
} from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import { useExternalCqlList, useUploadExternalCql, useDeleteExternalCql } from '../../../hooks/useExternalCql'
import type { ExternalCqlLibrary } from '../../../types/authoring'
import { codeBlockSx } from '../../../constants/authoringConstants'
import type { UseMutationResult } from '@tanstack/react-query'

/** Props for the presentational view (no hooks — parent provides data) */
export interface ExternalCqlViewProps {
  libraries: ExternalCqlLibrary[]
  isLoading: boolean
  error: unknown
  uploadMutation: UseMutationResult<ExternalCqlLibrary, Error, File>
  deleteMutation: UseMutationResult<unknown, Error, number>
  onApplyToArtifact?: (lib: ExternalCqlLibrary) => void
}

/** Reusable presentational component — works for both CDS and eCQM */
export function ExternalCqlView({
  libraries, isLoading, error, uploadMutation, deleteMutation, onApplyToArtifact,
}: ExternalCqlViewProps) {
  const { t } = useTranslation('authoring')
  const [detailsLib, setDetailsLib] = useState<ExternalCqlLibrary | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<ExternalCqlLibrary | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFileSelect = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (file) {
        uploadMutation.mutate(file)
      }
      // Reset so same file can be re-uploaded
      if (fileInputRef.current) fileInputRef.current.value = ''
    },
    [uploadMutation]
  )

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      const file = e.dataTransfer.files[0]
      if (file && (file.name.endsWith('.cql') || file.type === 'text/plain')) {
        uploadMutation.mutate(file)
      }
    },
    [uploadMutation]
  )

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
  }

  const handleDelete = () => {
    if (deleteTarget) {
      deleteMutation.mutate(deleteTarget.id)
      setDeleteTarget(null)
    }
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">{t('externalCql.title')}</Typography>
        <GradientButton
          startIcon={<UploadIcon />}
          onClick={() => fileInputRef.current?.click()}
          disabled={uploadMutation.isPending}
        >
          {t('externalCql.upload')}
        </GradientButton>
        <input
          ref={fileInputRef}
          type="file"
          accept=".cql"
          style={{ display: 'none' }}
          onChange={handleFileSelect}
        />
      </Stack>

      {/* Drop zone */}
      <Box
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        sx={{
          border: '2px dashed',
          borderColor: 'divider',
          borderRadius: 2,
          p: 3,
          mb: 2,
          textAlign: 'center',
          color: 'text.secondary',
          transition: 'border-color 0.2s',
          '&:hover': { borderColor: 'primary.main' },
        }}
      >
        <UploadIcon sx={{ fontSize: 40, mb: 1, opacity: 0.5 }} />
        <Typography variant="body2">
          {t('externalCql.dropHint')}
        </Typography>
      </Box>

      {uploadMutation.isPending && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
          <CircularProgress size={16} />
          <Typography variant="body2">{t('externalCql.uploading')}</Typography>
        </Box>
      )}

      {uploadMutation.isError && (
        <Alert severity="error" onClose={() => uploadMutation.reset()} sx={{ mb: 2 }}>
          <Typography variant="subtitle2">{t('externalCql.uploadFailed')}</Typography>
          <Typography variant="body2" sx={{ mb: 0.5 }}>
            {(uploadMutation.error as Error)?.message || 'Unknown error'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {t('externalCql.uploadFileHint')}
          </Typography>
        </Alert>
      )}

      {uploadMutation.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>
          {t('externalCql.uploadSuccess')}
        </Alert>
      )}

      {isLoading && <CircularProgress size={24} />}

      {!!error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('externalCql.loadError')}
        </Alert>
      )}

      {!isLoading && libraries.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
          <Typography variant="body2">
            {t('externalCql.emptyState')}
          </Typography>
        </Box>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('externalCql.colName')}</TableCell>
                <TableCell>{t('externalCql.colVersion')}</TableCell>
                <TableCell>{t('externalCql.colFhirVersion')}</TableCell>
                <TableCell>{t('externalCql.colUploaded')}</TableCell>
                <TableCell align="right">{t('externalCql.colActions')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {libraries.map((lib) => (
                <TableRow key={lib.id}>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {lib.name}
                    </Typography>
                  </TableCell>
                  <TableCell>{lib.version || '-'}</TableCell>
                  <TableCell>
                    {lib.fhirVersion ? (
                      <Chip label={lib.fhirVersion} size="small" variant="outlined" />
                    ) : '-'}
                  </TableCell>
                  <TableCell>
                    {lib.createdAt ? new Date(lib.createdAt).toLocaleDateString() : '-'}
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title={t('externalCql.viewDetails')}>
                      <IconButton size="small" onClick={() => setDetailsLib(lib)} aria-label={t('externalCql.viewDetails')}>
                        <ViewIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title={t('actions.delete', { ns: 'common' })}>
                      <IconButton size="small" color="error" onClick={() => setDeleteTarget(lib)} aria-label={t('actions.delete', { ns: 'common' })}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Details Dialog */}
      <Dialog open={!!detailsLib} onClose={() => setDetailsLib(null)} maxWidth="md" fullWidth>
        <DialogTitle>
          {detailsLib?.name} {detailsLib?.version && `v${detailsLib.version}`}
        </DialogTitle>
        <DialogContent>
          {detailsLib && (
            <Stack spacing={2} sx={{ mt: 1 }}>
              {detailsLib.details?.definitions && detailsLib.details.definitions.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{t('externalCql.definitions')}</Typography>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap">
                    {detailsLib.details.definitions.map((d) => (
                      <Chip
                        key={d.name}
                        label={`${d.name}${d.resultType ? `: ${d.resultType}` : ''}`}
                        size="small"
                        variant="outlined"
                        sx={{ mb: 0.5 }}
                      />
                    ))}
                  </Stack>
                </Box>
              )}

              {detailsLib.details?.parameters && detailsLib.details.parameters.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{t('externalCql.parametersLabel')}</Typography>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap">
                    {detailsLib.details.parameters.map((p) => (
                      <Chip key={p} label={p} size="small" variant="outlined" sx={{ mb: 0.5 }} />
                    ))}
                  </Stack>
                </Box>
              )}

              {detailsLib.details?.valueSets && detailsLib.details.valueSets.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{t('externalCql.valueSetsLabel')}</Typography>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap">
                    {detailsLib.details.valueSets.map((vs) => (
                      <Chip key={vs} label={vs} size="small" variant="outlined" sx={{ mb: 0.5 }} />
                    ))}
                  </Stack>
                </Box>
              )}

              {detailsLib.details?.errors && detailsLib.details.errors.length > 0 && (
                <Alert severity="warning">
                  <Typography variant="subtitle2">{t('externalCql.translationWarnings')}</Typography>
                  {detailsLib.details.errors.map((err, i) => (
                    <Typography key={i} variant="caption" display="block" sx={{ fontFamily: 'monospace' }}>
                      {err.message}
                    </Typography>
                  ))}
                </Alert>
              )}

              <Box>
                <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{t('externalCql.cqlSource')}</Typography>
                <Box
                  sx={{
                    ...codeBlockSx,
                    fontSize: '0.8rem',
                    maxHeight: 300,
                    overflow: 'auto',
                  }}
                >
                  {detailsLib.cqlContent}
                </Box>
              </Box>
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          {onApplyToArtifact && detailsLib?.details?.definitions && detailsLib.details.definitions.length > 0 && (
            <Button
              variant="contained"
              color="primary"
              startIcon={<ApplyIcon />}
              onClick={() => {
                if (detailsLib) {
                  onApplyToArtifact(detailsLib)
                  setDetailsLib(null)
                }
              }}
              sx={{ mr: 'auto' }}
            >
              {t('externalCql.applyToArtifact')}
            </Button>
          )}
          <Button onClick={() => setDetailsLib(null)}>{t('actions.close', { ns: 'common' })}</Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation */}
      <Dialog open={!!deleteTarget} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>{t('externalCql.deleteTitle')}</DialogTitle>
        <DialogContent>
          <Typography>
            {t('externalCql.deleteConfirm', { name: deleteTarget?.name })}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>{t('actions.close', { ns: 'common' })}</Button>
          <Button onClick={handleDelete} color="error" variant="contained">{t('actions.delete', { ns: 'common' })}</Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

/** CDS authoring wrapper — default export for backward compatibility */
interface ExternalCqlProps {
  artifactId: number
  onApplyToArtifact?: (lib: ExternalCqlLibrary) => void
}

export default function ExternalCql({ artifactId, onApplyToArtifact }: ExternalCqlProps) {
  const { data: libraries = [], isLoading, error } = useExternalCqlList(artifactId)
  const uploadMutation = useUploadExternalCql(artifactId)
  const deleteMutation = useDeleteExternalCql(artifactId)

  return (
    <ExternalCqlView
      libraries={libraries}
      isLoading={isLoading}
      error={error}
      uploadMutation={uploadMutation as UseMutationResult<ExternalCqlLibrary, Error, File>}
      deleteMutation={deleteMutation as UseMutationResult<unknown, Error, number>}
      onApplyToArtifact={onApplyToArtifact}
    />
  )
}
