import { useState, useCallback, useRef } from 'react'
import {
  Box, Stack, Typography, Alert, CircularProgress, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, IconButton, Tooltip, Chip, Dialog,
  DialogTitle, DialogContent, DialogActions, Button, Paper,
} from '@mui/material'
import {
  Delete as DeleteIcon, Visibility as ViewIcon, CloudUpload as UploadIcon,
} from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import { useExternalCqlList, useUploadExternalCql, useDeleteExternalCql } from '../../../hooks/useExternalCql'
import type { ExternalCqlLibrary } from '../../../types/authoring'

interface ExternalCqlProps {
  artifactId: number
}

export default function ExternalCql({ artifactId }: ExternalCqlProps) {
  const { data: libraries = [], isLoading, error } = useExternalCqlList(artifactId)
  const uploadMutation = useUploadExternalCql(artifactId)
  const deleteMutation = useDeleteExternalCql(artifactId)
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
        <Typography variant="h6">External CQL Libraries</Typography>
        <GradientButton
          startIcon={<UploadIcon />}
          onClick={() => fileInputRef.current?.click()}
          disabled={uploadMutation.isPending}
        >
          Upload Library
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
          Drag and drop a .cql file here, or click &quot;Upload Library&quot; above
        </Typography>
      </Box>

      {uploadMutation.isPending && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
          <CircularProgress size={16} />
          <Typography variant="body2">Uploading and parsing...</Typography>
        </Box>
      )}

      {uploadMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Upload failed: {(uploadMutation.error as Error)?.message || 'Unknown error'}
        </Alert>
      )}

      {uploadMutation.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>
          Library uploaded successfully.
        </Alert>
      )}

      {isLoading && <CircularProgress size={24} />}

      {!!error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load libraries.
        </Alert>
      )}

      {!isLoading && libraries.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
          <Typography variant="body2">
            No external CQL libraries uploaded. Upload libraries to reference them in your artifact logic.
          </Typography>
        </Box>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Version</TableCell>
                <TableCell>FHIR Version</TableCell>
                <TableCell>Uploaded</TableCell>
                <TableCell align="right">Actions</TableCell>
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
                    <Tooltip title="View details">
                      <IconButton size="small" onClick={() => setDetailsLib(lib)}>
                        <ViewIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton size="small" color="error" onClick={() => setDeleteTarget(lib)}>
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
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>Definitions</Typography>
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
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>Parameters</Typography>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap">
                    {detailsLib.details.parameters.map((p) => (
                      <Chip key={p} label={p} size="small" variant="outlined" sx={{ mb: 0.5 }} />
                    ))}
                  </Stack>
                </Box>
              )}

              {detailsLib.details?.valueSets && detailsLib.details.valueSets.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>Value Sets</Typography>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap">
                    {detailsLib.details.valueSets.map((vs) => (
                      <Chip key={vs} label={vs} size="small" variant="outlined" sx={{ mb: 0.5 }} />
                    ))}
                  </Stack>
                </Box>
              )}

              {detailsLib.details?.errors && detailsLib.details.errors.length > 0 && (
                <Alert severity="warning">
                  <Typography variant="subtitle2">Translation Warnings</Typography>
                  {detailsLib.details.errors.map((err, i) => (
                    <Typography key={i} variant="caption" display="block" sx={{ fontFamily: 'monospace' }}>
                      {err.message}
                    </Typography>
                  ))}
                </Alert>
              )}

              <Box>
                <Typography variant="subtitle2" sx={{ mb: 0.5 }}>CQL Source</Typography>
                <Box
                  sx={{
                    p: 1.5,
                    borderRadius: 1,
                    backgroundColor: (theme) => theme.palette.mode === 'dark' ? '#1e1e1e' : '#f5f5f5',
                    fontFamily: '"Fira Code", "Consolas", monospace',
                    fontSize: '0.8rem',
                    whiteSpace: 'pre-wrap',
                    maxHeight: 300,
                    overflow: 'auto',
                    border: 1,
                    borderColor: 'divider',
                  }}
                >
                  {detailsLib.cqlContent}
                </Box>
              </Box>
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetailsLib(null)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation */}
      <Dialog open={!!deleteTarget} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>Delete Library</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete &quot;{deleteTarget?.name}&quot;? This cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>Cancel</Button>
          <Button onClick={handleDelete} color="error" variant="contained">Delete</Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
