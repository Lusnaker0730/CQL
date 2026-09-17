import { useState, useMemo, useCallback } from 'react'
import { parseCqlDefineBlocks } from '../../utils/cqlNames'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  Radio,
  Stack,
  Step,
  StepLabel,
  Stepper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { ArrowBack as BackIcon, Close as CloseIcon } from '@mui/icons-material'
import { useQuery } from '@tanstack/react-query'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { useCqlLibrary } from '../../hooks/useCqlLibraries'
import { cqlApi } from '../../api'
import { SEARCH_DEBOUNCE_GENERAL_MS } from '../../constants/timing'
import StatusChip from '../common/StatusChip'
import { generateAlias } from '../../utils/libraryAlias'
import type { CqlLibrary } from '../../types'

export interface LibraryDefinitionReference {
  libraryName: string
  libraryVersion: string
  definitionName: string
  alias: string
}

interface LibraryDefinitionPickerProps {
  open: boolean
  onClose: () => void
  onSelect: (reference: LibraryDefinitionReference) => void
}

export default function LibraryDefinitionPicker({
  open,
  onClose,
  onSelect,
}: LibraryDefinitionPickerProps) {
  const { t } = useTranslation('cqlLibraries')

  // Step management
  const [activeStep, setActiveStep] = useState(0)
  const [selectedLibrary, setSelectedLibrary] = useState<CqlLibrary | null>(null)
  const [selectedDefinition, setSelectedDefinition] = useState<string>('')
  const [alias, setAlias] = useState('')

  // Search for step 1
  const [searchInput, setSearchInput] = useState('')
  const debouncedSearch = useDebouncedValue(searchInput, SEARCH_DEBOUNCE_GENERAL_MS)

  // Queries — only fetch libraries when the dialog is open
  const { data: libraries, isLoading: librariesLoading } = useQuery({
    queryKey: ['cql-libraries', debouncedSearch || undefined] as const,
    queryFn: () => cqlApi.getLibraries(debouncedSearch || undefined),
    enabled: open,
  })
  const { data: libraryDetail, isLoading: detailLoading } = useCqlLibrary(
    activeStep === 1 ? selectedLibrary?.id : null
  )

  // Filter to only active/draft libraries
  const filteredLibraries = useMemo(
    () => (libraries || []).filter((lib) => lib.status === 'active' || lib.status === 'draft'),
    [libraries]
  )

  // Extract definitions from library CQL content
  const definitions = useMemo(() => {
    if (!libraryDetail?.cqlContent) return []
    return parseCqlDefineBlocks(libraryDetail.cqlContent).map(b => b.name)
  }, [libraryDetail?.cqlContent])

  const steps = [t('picker.step1Title'), t('picker.step2Title')]

  const resetState = useCallback(() => {
    setActiveStep(0)
    setSelectedLibrary(null)
    setSelectedDefinition('')
    setAlias('')
    setSearchInput('')
  }, [])

  const handleSelectLibrary = useCallback((lib: CqlLibrary) => {
    setSelectedLibrary(lib)
    setAlias(generateAlias(lib.name))
    setSelectedDefinition('')
    setActiveStep(1)
  }, [])

  const handleBack = useCallback(() => {
    resetState()
  }, [resetState])

  const handleConfirm = useCallback(() => {
    if (!selectedLibrary || !selectedDefinition) return
    onSelect({
      libraryName: selectedLibrary.name,
      libraryVersion: selectedLibrary.version,
      definitionName: selectedDefinition,
      alias: alias.trim() || generateAlias(selectedLibrary.name),
    })
    resetState()
  }, [selectedLibrary, selectedDefinition, alias, onSelect, resetState])

  const handleClose = useCallback(() => {
    resetState()
    onClose()
  }, [resetState, onClose])

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle>
        <Stack
          direction="row"
          sx={{
            alignItems: "center",
            justifyContent: "space-between"
          }}>
          <Typography variant="h6">{t('picker.title')}</Typography>
          <IconButton onClick={handleClose} size="small">
            <CloseIcon />
          </IconButton>
        </Stack>
      </DialogTitle>
      <DialogContent dividers>
        <Stepper activeStep={activeStep} sx={{ mb: 3 }}>
          {steps.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>

        {activeStep === 0 && (
          <Box>
            <TextField
              fullWidth
              size="small"
              placeholder={t('picker.searchLibraries')}
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              sx={{ mb: 2 }}
            />

            {librariesLoading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                <CircularProgress size={32} />
              </Box>
            ) : filteredLibraries.length === 0 ? (
              <Typography
                sx={{
                  color: "text.secondary",
                  textAlign: 'center',
                  py: 4
                }}>
                {t('list.noResults')}
              </Typography>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('list.colName')}</TableCell>
                    <TableCell>{t('list.colVersion')}</TableCell>
                    <TableCell>{t('list.colOwner')}</TableCell>
                    <TableCell>{t('list.colStatus')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredLibraries.map((lib) => (
                    <TableRow
                      key={lib.id}
                      hover
                      sx={{ cursor: 'pointer' }}
                      onClick={() => handleSelectLibrary(lib)}
                    >
                      <TableCell>
                        <Typography sx={{
                          fontWeight: 500
                        }}>{lib.name}</Typography>
                      </TableCell>
                      <TableCell>{lib.version}</TableCell>
                      <TableCell>{lib.ownerUsername || '-'}</TableCell>
                      <TableCell>
                        <StatusChip status={lib.status} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Box>
        )}

        {activeStep === 1 && selectedLibrary && (
          <Box>
            <Stack
              direction="row"
              spacing={1}
              sx={{
                alignItems: "center",
                mb: 2
              }}>
              <IconButton onClick={handleBack} size="small">
                <BackIcon />
              </IconButton>
              <Typography variant="subtitle1" sx={{
                fontWeight: 600
              }}>
                {selectedLibrary.name}
              </Typography>
              <Typography variant="body2" sx={{
                color: "text.secondary"
              }}>
                v{selectedLibrary.version}
              </Typography>
            </Stack>

            {detailLoading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                <CircularProgress size={32} />
              </Box>
            ) : definitions.length === 0 ? (
              <Typography
                sx={{
                  color: "text.secondary",
                  textAlign: 'center',
                  py: 4
                }}>
                {t('picker.noDefinitions')}
              </Typography>
            ) : (
              <>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  {t('picker.selectDefinition')}
                </Typography>
                <List sx={{ maxHeight: 300, overflow: 'auto', mb: 2 }}>
                  {definitions.map((defName) => (
                    <ListItemButton
                      key={defName}
                      selected={selectedDefinition === defName}
                      onClick={() => setSelectedDefinition(defName)}
                      sx={{ borderRadius: 1 }}
                    >
                      <Radio
                        checked={selectedDefinition === defName}
                        size="small"
                        sx={{ mr: 1 }}
                      />
                      <ListItemText
                        primary={defName}
                        slotProps={{
                          primary: { sx: { fontFamily: 'monospace', fontSize: '0.9rem' } }
                        }}
                      />
                    </ListItemButton>
                  ))}
                </List>

                <TextField
                  label={t('picker.alias')}
                  helperText={t('picker.aliasHint')}
                  size="small"
                  value={alias}
                  onChange={(e) => setAlias(e.target.value)}
                  sx={{ width: 200 }}
                />

                {selectedDefinition && alias.trim() && (
                  <Typography
                    variant="body2"
                    sx={{ mt: 2, fontFamily: 'monospace', color: 'text.secondary' }}
                  >
                    {t('picker.generateInclude')}:{' '}
                    <Box component="span" sx={{ fontWeight: 600 }}>
                      {alias.trim()}.&quot;{selectedDefinition}&quot;
                    </Box>
                  </Typography>
                )}
              </>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        {activeStep === 1 && (
          <Button onClick={handleBack}>{t('picker.back')}</Button>
        )}
        <Button onClick={handleClose}>{t('common:actions.cancel')}</Button>
        {activeStep === 1 && (
          <Button
            variant="contained"
            onClick={handleConfirm}
            disabled={!selectedDefinition || !alias.trim()}
          >
            {t('picker.select')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
