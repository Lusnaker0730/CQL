import { useState, useRef } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField,
  Stack, Typography, Alert, CircularProgress, Box, Chip, Table, TableBody,
  TableCell, TableHead, TableRow, Divider,
} from '@mui/material'
import { useImportCql } from '../../../hooks/useCqlImport'
import { useCreateArtifact } from '../../../hooks/useAuthoring'
import type { CqlImportResult } from '../../../types/authoring'

interface ImportCqlDialogProps {
  open: boolean
  onClose: () => void
  onImported: (artifactId: number) => void
}

export default function ImportCqlDialog({ open, onClose, onImported }: ImportCqlDialogProps) {
  const [cqlInput, setCqlInput] = useState('')
  const [importResult, setImportResult] = useState<CqlImportResult | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const importMutation = useImportCql()
  const createMutation = useCreateArtifact()

  const handleParse = () => {
    if (!cqlInput.trim()) return
    importMutation.mutate(cqlInput, {
      onSuccess: (data) => setImportResult(data),
    })
  }

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      const reader = new FileReader()
      reader.onload = (ev) => {
        const content = ev.target?.result as string
        setCqlInput(content)
      }
      reader.readAsText(file)
    }
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const handleCreateArtifact = () => {
    if (!importResult) return

    createMutation.mutate(
      {
        name: importResult.name,
        version: importResult.version,
        fhirVersion: importResult.fhirVersion,
        description: `Imported from CQL: ${importResult.name}`,
      },
      {
        onSuccess: (artifact) => {
          onImported(artifact.id)
          handleClose()
        },
      }
    )
  }

  const handleClose = () => {
    setCqlInput('')
    setImportResult(null)
    onClose()
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle>Import CQL</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {!importResult ? (
            <>
              <Typography variant="body2" color="text.secondary">
                Paste CQL code below or upload a .cql file. The parser will extract library metadata,
                value sets, parameters, and define statements.
              </Typography>

              <Button
                variant="outlined"
                size="small"
                onClick={() => fileInputRef.current?.click()}
                sx={{ alignSelf: 'flex-start' }}
              >
                Upload .cql File
              </Button>
              <input
                ref={fileInputRef}
                type="file"
                accept=".cql"
                style={{ display: 'none' }}
                onChange={handleFileUpload}
              />

              <TextField
                label="CQL Source Code"
                value={cqlInput}
                onChange={(e) => setCqlInput(e.target.value)}
                multiline
                minRows={10}
                maxRows={20}
                fullWidth
                placeholder="library MyArtifact version '1.0.0'&#10;using FHIR version '4.0.1'&#10;..."
                sx={{
                  '& .MuiInputBase-input': {
                    fontFamily: '"Fira Code", "Consolas", monospace',
                    fontSize: '0.85rem',
                  },
                }}
              />

              {importMutation.isError && (
                <Alert severity="error">
                  Import failed: {(importMutation.error as Error)?.message || 'Unknown error'}
                </Alert>
              )}
            </>
          ) : (
            <>
              {/* Import Preview */}
              <Alert severity={importResult.valid ? 'success' : 'warning'}>
                {importResult.valid
                  ? 'CQL parsed successfully. Review the extracted structure below.'
                  : 'CQL parsed with errors. The artifact will be created but may need corrections.'}
              </Alert>

              <Stack direction="row" spacing={2} flexWrap="wrap">
                <Chip label={`Library: ${importResult.name}`} />
                <Chip label={`Version: ${importResult.version}`} variant="outlined" />
                <Chip label={`FHIR: ${importResult.fhirVersion}`} variant="outlined" />
              </Stack>

              {importResult.errors && importResult.errors.length > 0 && (
                <Alert severity="error">
                  <Typography variant="subtitle2">{importResult.errors.length} error(s)</Typography>
                  {importResult.errors.slice(0, 5).map((err, i) => (
                    <Typography key={i} variant="caption" display="block" sx={{ fontFamily: 'monospace' }}>
                      {err.message}
                    </Typography>
                  ))}
                </Alert>
              )}

              {importResult.valueSets.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                    Value Sets ({importResult.valueSets.length})
                  </Typography>
                  <Stack direction="row" spacing={0.5} flexWrap="wrap">
                    {importResult.valueSets.map((vs) => (
                      <Chip key={vs.oid} label={vs.name} size="small" variant="outlined" sx={{ mb: 0.5 }} />
                    ))}
                  </Stack>
                </Box>
              )}

              {importResult.parameters.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                    Parameters ({importResult.parameters.length})
                  </Typography>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Name</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell>Default</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {importResult.parameters.map((p) => (
                        <TableRow key={p.uniqueId}>
                          <TableCell>{p.name}</TableCell>
                          <TableCell><Chip label={p.type} size="small" /></TableCell>
                          <TableCell>{p.value || '-'}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Box>
              )}

              {importResult.definitions.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                    Define Statements ({importResult.definitions.length})
                  </Typography>
                  {importResult.definitions.map((def, i) => (
                    <Box key={i} sx={{ mb: 1 }}>
                      <Typography variant="caption" sx={{ fontWeight: 600 }}>{def.name}</Typography>
                      <Box
                        sx={{
                          p: 0.5,
                          pl: 1.5,
                          borderLeft: 2,
                          borderColor: 'primary.main',
                          backgroundColor: 'action.hover',
                          fontFamily: '"Fira Code", monospace',
                          fontSize: '0.75rem',
                          whiteSpace: 'pre-wrap',
                          maxHeight: 60,
                          overflow: 'auto',
                        }}
                      >
                        {def.expression}
                      </Box>
                    </Box>
                  ))}
                </Box>
              )}

              <Divider />
              <Button variant="outlined" size="small" onClick={() => setImportResult(null)} sx={{ alignSelf: 'flex-start' }}>
                Back to Editor
              </Button>
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Cancel</Button>
        {!importResult ? (
          <Button
            onClick={handleParse}
            variant="contained"
            disabled={!cqlInput.trim() || importMutation.isPending}
          >
            {importMutation.isPending ? <CircularProgress size={16} sx={{ mr: 1 }} /> : null}
            Parse CQL
          </Button>
        ) : (
          <Button
            onClick={handleCreateArtifact}
            variant="contained"
            disabled={createMutation.isPending}
          >
            {createMutation.isPending ? <CircularProgress size={16} sx={{ mr: 1 }} /> : null}
            Create Artifact
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}
