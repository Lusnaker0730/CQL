import { useState, useRef } from 'react'
import {
  Box,
  Grid,
  Paper,
  Button,
  Stack,
  Tabs,
  Tab,
  Typography,
  CircularProgress,
} from '@mui/material'
import {
  Translate as TranslateIcon,
  Save as SaveIcon,
  FileDownload as ExportIcon,
  FileUpload as ImportIcon,
} from '@mui/icons-material'
import { useSelector } from 'react-redux'
import CqlEditor from '../components/editor/CqlEditor'
import ElmViewer from '../components/editor/ElmViewer'
import ExecutionPanel from '../components/execution/ExecutionPanel'
import LibraryQuickAccess from '../components/editor/LibraryQuickAccess'
import HelpTooltip from '../components/common/HelpTooltip'
import type { RootState } from '../store'
import { useTranslate, useCreateLibrary, useExportLibrary, useImportLibrary, useLibrariesMetadata } from '../hooks/useCql'
import { useTerminologyValidation } from '../hooks/useTerminologyValidation'
import { useLibraryHistory } from '../hooks/useLibraryHistory'
import { helpContent } from '../constants/helpContent'

interface TabPanelProps {
  children?: React.ReactNode
  index: number
  value: number
}

function TabPanel({ children, value, index }: TabPanelProps) {
  return (
    <div role="tabpanel" hidden={value !== index} style={{ height: '100%' }}>
      {value === index && <Box sx={{ height: '100%' }}>{children}</Box>}
    </div>
  )
}

export default function EditorPage() {
  const { cqlContent, isTranslating, errors, elmJson } = useSelector((state: RootState) => state.editor)
  const [rightPanelTab, setRightPanelTab] = useState(0)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const { addToRecent } = useLibraryHistory()

  const translateMutation = useTranslate()
  const saveLibraryMutation = useCreateLibrary()
  const exportMutation = useExportLibrary()
  const importMutation = useImportLibrary()
  const { data: libraryMetadata } = useLibrariesMetadata()
  const { results: terminologyResults, isValidating: isTermValidating } = useTerminologyValidation(elmJson)

  const handleTranslate = () => {
    translateMutation.mutate({ cql: cqlContent })
  }

  const handleSaveLibrary = () => {
    saveLibraryMutation.mutate(
      { cql: cqlContent },
      {
        onSuccess: (library) => {
          addToRecent({ id: library.id, name: library.name, version: library.version })
        },
      }
    )
  }

  const handleExport = () => {
    saveLibraryMutation.mutate(
      { cql: cqlContent },
      {
        onSuccess: (library) => {
          addToRecent({ id: library.id, name: library.name, version: library.version })
          exportMutation.mutate(library.id, {
            onSuccess: (fhirLibrary) => {
              const blob = new Blob([JSON.stringify(fhirLibrary, null, 2)], { type: 'application/json' })
              const url = URL.createObjectURL(blob)
              const a = document.createElement('a')
              a.href = url
              a.download = `${library.name}-${library.version}.fhir.json`
              a.click()
              URL.revokeObjectURL(url)
            },
          })
        },
      }
    )
  }

  const handleImport = () => {
    fileInputRef.current?.click()
  }

  const handleFileSelected = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    const reader = new FileReader()
    reader.onload = (event) => {
      try {
        const fhirLibrary = JSON.parse(event.target?.result as string)
        importMutation.mutate(fhirLibrary)
      } catch {
        // Invalid JSON
      }
    }
    reader.readAsText(file)
    e.target.value = ''
  }

  return (
    <Box sx={{ height: 'calc(100vh - 120px)', p: 2 }}>
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileSelected}
        accept=".json"
        style={{ display: 'none' }}
      />
      <Grid container spacing={2} sx={{ height: '100%' }}>
        <Grid item xs={12} md={2} sx={{ height: '100%', display: { xs: 'none', md: 'block' } }}>
          <LibraryQuickAccess />
        </Grid>

        <Grid item xs={12} md={5.5} sx={{ height: '100%' }}>
          <Paper
            sx={{
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              borderLeft: '3px solid',
              borderLeftColor: 'primary.main',
              overflow: 'hidden',
            }}
          >
            <Box
              sx={{
                p: 1,
                px: 1.5,
                background: 'linear-gradient(135deg, rgba(13,115,119,0.06) 0%, rgba(20,163,168,0.03) 100%)',
                borderBottom: '1px solid',
                borderColor: 'rgba(13,115,119,0.1)',
              }}
            >
              <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center">
                <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'secondary.main' }}>
                  CQL Editor
                </Typography>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Button
                    size="small"
                    variant="contained"
                    startIcon={
                      isTranslating ? <CircularProgress size={16} color="inherit" /> : <TranslateIcon />
                    }
                    onClick={handleTranslate}
                    disabled={isTranslating || !cqlContent}
                    sx={{
                      background: 'linear-gradient(135deg, #1B3A5C 0%, #2D5F8A 100%)',
                      '&:hover': {
                        background: 'linear-gradient(135deg, #0F2440 0%, #1B3A5C 100%)',
                      },
                      '&.Mui-disabled': {
                        background: 'rgba(0,0,0,0.12)',
                      },
                    }}
                  >
                    {isTranslating ? 'Translating...' : 'Translate'}
                  </Button>
                  <HelpTooltip text={helpContent.editor.translate} />
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<SaveIcon />}
                    onClick={handleSaveLibrary}
                    disabled={saveLibraryMutation.isPending || errors.length > 0}
                    sx={{
                      borderColor: 'rgba(13,115,119,0.4)',
                      color: 'primary.dark',
                      '&:hover': {
                        borderColor: 'primary.main',
                        bgcolor: 'rgba(13,115,119,0.04)',
                      },
                    }}
                  >
                    Save Library
                  </Button>
                  <HelpTooltip text={helpContent.editor.save} />
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<ExportIcon />}
                    onClick={handleExport}
                    disabled={!cqlContent || errors.length > 0}
                    sx={{
                      borderColor: 'rgba(27,58,92,0.3)',
                      color: 'secondary.main',
                      '&:hover': {
                        borderColor: 'secondary.main',
                        bgcolor: 'rgba(27,58,92,0.04)',
                      },
                    }}
                  >
                    Export
                  </Button>
                  <HelpTooltip text={helpContent.editor.export} />
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<ImportIcon />}
                    onClick={handleImport}
                    disabled={importMutation.isPending}
                    sx={{
                      borderColor: 'rgba(27,58,92,0.3)',
                      color: 'secondary.main',
                      '&:hover': {
                        borderColor: 'secondary.main',
                        bgcolor: 'rgba(27,58,92,0.04)',
                      },
                    }}
                  >
                    Import
                  </Button>
                  <HelpTooltip text={helpContent.editor.import} />
                </Stack>
              </Stack>
            </Box>
            <Box sx={{ flexGrow: 1, overflow: 'hidden' }}>
              <CqlEditor
                height="100%"
                onTranslate={handleTranslate}
                terminologyIssues={terminologyResults.filter((r) => r.status !== 'valid')}
                libraryMetadata={libraryMetadata}
              />
            </Box>
          </Paper>
        </Grid>

        <Grid item xs={12} md={4.5} sx={{ height: '100%' }}>
          <Paper
            sx={{
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              borderLeft: '3px solid',
              borderLeftColor: 'secondary.main',
              overflow: 'hidden',
            }}
          >
            <Tabs
              value={rightPanelTab}
              onChange={(_, v) => setRightPanelTab(v)}
              sx={{
                borderBottom: '1px solid',
                borderColor: 'rgba(13,115,119,0.1)',
                bgcolor: 'rgba(27,58,92,0.03)',
              }}
            >
              <Tab label="ELM / Errors" />
              <Tab label="Execute" />
            </Tabs>
            <Box sx={{ flexGrow: 1, overflow: 'auto' }}>
              <TabPanel value={rightPanelTab} index={0}>
                <ElmViewer
                  terminologyResults={terminologyResults}
                  isTermValidating={isTermValidating}
                />
              </TabPanel>
              <TabPanel value={rightPanelTab} index={1}>
                <ExecutionPanel />
              </TabPanel>
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  )
}
