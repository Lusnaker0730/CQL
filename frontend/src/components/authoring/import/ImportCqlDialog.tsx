import { useState, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField,
  Stack, Typography, Alert, CircularProgress, Box, Chip, Table, TableBody,
  TableCell, TableHead, TableRow, Divider,
} from '@mui/material'
import { useImportCql } from '../../../hooks/useCqlImport'
import { useCreateArtifact } from '../../../hooks/useAuthoring'
import { useNotification } from '../../../hooks/useNotification'
import { authoringApi } from '../../../api'
import type { CqlImportResult } from '../../../types/authoring'
import { SYSTEM_DEFINITIONS, DEF_MEETS_INCLUSION, DEF_MEETS_EXCLUSION } from '../../../constants/authoringConstants'
import { generateId } from '../../../utils/validation'

interface ImportCqlDialogProps {
  open: boolean
  onClose: () => void
  onImported: (artifactId: number) => void
}

export default function ImportCqlDialog({ open, onClose, onImported }: ImportCqlDialogProps) {
  const { t } = useTranslation('authoring')
  const [cqlInput, setCqlInput] = useState('')
  const [importResult, setImportResult] = useState<CqlImportResult | null>(null)
  const [isUploading, setIsUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const importMutation = useImportCql()
  const createMutation = useCreateArtifact()
  const { showNotification } = useNotification()

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

  // System definitions imported from authoringConstants

  /** Create an externalCqlRef element pointing to a definition in an external library */
  const makeExternalCqlRef = (
    defName: string,
    libName: string,
    libId: number,
    libVersion?: string,
    returnType = 'System.Any'
  ) => ({
    uniqueId: generateId(),
    type: 'externalCqlRef',
    name: defName,
    returnType,
    fields: [
      { id: 'element_name', type: 'string', name: 'Element Name', value: defName },
      { id: 'reference_id', type: 'string', name: 'Reference ID', value: `${libId}:${defName}`, static: true },
      { id: 'library_name', type: 'string', name: 'Library', value: libName, static: true },
      ...(libVersion ? [{ id: 'library_version', type: 'string', name: 'Library Version', value: libVersion, static: true }] : []),
    ],
    modifiers: [],
  })

  /** Wrap elements in a conjunction group for the expression tree */
  const wrapInConjunction = (name: string, elements: ReturnType<typeof makeExternalCqlRef>[]) => ({
    id: 'And',
    name,
    conjunction: true,
    returnType: 'boolean',
    childInstances: elements,
  })

  const handleCreateArtifact = () => {
    if (!importResult) return

    // Map parsed parameters to artifact format
    const parameters = importResult.parameters.map((p) => ({
      uniqueId: p.uniqueId,
      name: p.name,
      type: p.type,
      value: p.value,
    }))

    createMutation.mutate(
      {
        name: importResult.name,
        version: importResult.version,
        fhirVersion: importResult.fhirVersion,
        description: `Imported from CQL: ${importResult.name}`,
        parameters: parameters.length > 0 ? parameters : undefined,
      },
      {
        onSuccess: async (artifact) => {
          if (!importResult.cqlContent) {
            onImported(artifact.id)
            handleClose()
            return
          }

          setIsUploading(true)
          try {
            // Upload the original CQL content as an External CQL library
            const extLib = await authoringApi.uploadExternalCqlContent(artifact.id, importResult.cqlContent)

            // Auto-populate expression trees from the uploaded library's definitions
            const defs = extLib.details?.definitions ?? []
            const libName = extLib.name
            const libId = extLib.id
            const libVersion = extLib.version

            // Find key definitions
            const inclusionDef = defs.find((d) => d.name === DEF_MEETS_INCLUSION)
            const exclusionDef = defs.find((d) => d.name === DEF_MEETS_EXCLUSION)

            // Build expression tree elements
            const inclusionElements = inclusionDef
              ? [makeExternalCqlRef(inclusionDef.name, libName, libId, libVersion, inclusionDef.resultType || 'System.Boolean')]
              : []
            const exclusionElements = exclusionDef
              ? [makeExternalCqlRef(exclusionDef.name, libName, libId, libVersion, exclusionDef.resultType || 'System.Boolean')]
              : []

            // Remaining definitions → baseElements
            const baseElements = defs
              .filter((d) => !SYSTEM_DEFINITIONS.has(d.name))
              .map((d) => ({
                uniqueId: generateId(),
                name: d.name,
                type: 'externalCqlRef',
                returnType: d.resultType || 'System.Any',
                fields: [
                  { id: 'element_name', type: 'string', name: 'Element Name', value: d.name },
                  { id: 'reference_id', type: 'string', name: 'Reference ID', value: `${libId}:${d.name}`, static: true },
                  { id: 'library_name', type: 'string', name: 'Library', value: libName, static: true },
                  ...(libVersion ? [{ id: 'library_version', type: 'string', name: 'Library Version', value: libVersion, static: true }] : []),
                ],
                modifiers: [],
              }))

            // Update the artifact with populated expression trees
            const hasChanges = inclusionElements.length > 0 || exclusionElements.length > 0 || baseElements.length > 0
            if (hasChanges) {
              try {
                await authoringApi.updateArtifact(artifact.id, {
                  name: artifact.name,
                  ...(inclusionElements.length > 0 && {
                    expTreeInclude: wrapInConjunction(DEF_MEETS_INCLUSION, inclusionElements),
                  }),
                  ...(exclusionElements.length > 0 && {
                    expTreeExclude: wrapInConjunction(DEF_MEETS_EXCLUSION, exclusionElements),
                  }),
                  ...(baseElements.length > 0 && { baseElements }),
                })
              } catch (err) {
                showNotification(t('importCql.populateError', { error: (err as Error).message }), 'warning')
              }
            }
          } catch (err) {
            showNotification(t('importCql.uploadError', { error: (err as Error).message }), 'warning')
          } finally {
            setIsUploading(false)
          }

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
      <DialogTitle>{t('importCql.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {!importResult ? (
            <>
              <Typography variant="body2" color="text.secondary">
                {t('importCql.description')}
              </Typography>

              <Button
                variant="outlined"
                size="small"
                onClick={() => fileInputRef.current?.click()}
                sx={{ alignSelf: 'flex-start' }}
              >
                {t('importCql.uploadFile')}
              </Button>
              <input
                ref={fileInputRef}
                type="file"
                accept=".cql"
                style={{ display: 'none' }}
                onChange={handleFileUpload}
              />

              <TextField
                label={t('importCql.cqlSourceLabel')}
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
                  <Typography variant="subtitle2">{t('importCql.parseFailed')}</Typography>
                  <Typography variant="body2" sx={{ mb: 0.5 }}>
                    {(importMutation.error as Error)?.message || 'Unknown error'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {t('importCql.parseFailedHint')}
                  </Typography>
                </Alert>
              )}
            </>
          ) : (
            <>
              {/* Import Preview */}
              <Alert severity={importResult.valid ? 'success' : 'warning'}>
                {importResult.valid
                  ? t('importCql.parseSuccess')
                  : t('importCql.parseWarning')}
              </Alert>

              <Stack direction="row" spacing={2} flexWrap="wrap">
                <Chip label={`Library: ${importResult.name}`} />
                <Chip label={`Version: ${importResult.version}`} variant="outlined" />
                <Chip label={`FHIR: ${importResult.fhirVersion}`} variant="outlined" />
              </Stack>

              {importResult.errors && importResult.errors.length > 0 && (
                <Alert severity="error">
                  <Typography variant="subtitle2">{t('importCql.errorsFound', { count: importResult.errors.length })}</Typography>
                  <Box sx={{ maxHeight: 150, overflow: 'auto', mt: 0.5 }}>
                    {importResult.errors.map((err, i) => (
                      <Typography key={i} variant="caption" display="block" sx={{ fontFamily: 'monospace', mb: 0.25 }}>
                        {i + 1}. {err.message}
                      </Typography>
                    ))}
                  </Box>
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                    {t('importCql.errorsHint')}
                  </Typography>
                </Alert>
              )}

              {importResult.valueSets.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                    {t('importCql.valueSets', { count: importResult.valueSets.length })}
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
                    {t('importCql.parameters', { count: importResult.parameters.length })}
                  </Typography>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>{t('importCql.colName')}</TableCell>
                        <TableCell>{t('importCql.colType')}</TableCell>
                        <TableCell>{t('importCql.colDefault')}</TableCell>
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
                    {t('importCql.defineStatements', { count: importResult.definitions.length })}
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
                {t('importCql.backToEditor')}
              </Button>
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t('actions.cancel', { ns: 'common' })}</Button>
        {!importResult ? (
          <Button
            onClick={handleParse}
            variant="contained"
            disabled={!cqlInput.trim() || importMutation.isPending}
          >
            {importMutation.isPending ? <CircularProgress size={16} sx={{ mr: 1 }} /> : null}
            {t('importCql.parseCql')}
          </Button>
        ) : (
          <Button
            onClick={handleCreateArtifact}
            variant="contained"
            disabled={createMutation.isPending || isUploading}
          >
            {(createMutation.isPending || isUploading) ? <CircularProgress size={16} sx={{ mr: 1 }} /> : null}
            {isUploading ? t('importCql.uploadingCql') : t('importCql.createArtifact')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}
