import { useState, useRef, useCallback } from 'react'
import {
  Box,
  Grid,
  Paper,
  Button,
  IconButton,
  Stack,
  Tabs,
  Tab,
  Typography,
  CircularProgress,
  Tooltip,
  ToggleButton,
} from '@mui/material'
import {
  Translate as TranslateIcon,
  Save as SaveIcon,
  FileDownload as ExportIcon,
  FileUpload as ImportIcon,
  Star as StarIcon,
  StarBorder as StarBorderIcon,
  Construction as BuilderIcon,
  History as HistoryIcon,
  CompareArrows as CompareIcon,
  NewReleases as VersionIcon,
  Share as ShareIcon,
} from '@mui/icons-material'
import { useSelector, useDispatch } from 'react-redux'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { cqlApi } from '../api'
import CqlEditor from '../components/editor/CqlEditor'
import ElmViewer from '../components/editor/ElmViewer'
import ExecutionPanel from '../components/execution/ExecutionPanel'
import LibraryQuickAccess from '../components/editor/LibraryQuickAccess'
import CqlBuilderPanel from '../components/builder/CqlBuilderPanel'
import LibraryDependencyPanel from '../components/editor/LibraryDependencyPanel'
import LibraryShareDialog from '../components/editor/LibraryShareDialog'
import HelpTooltip from '../components/common/HelpTooltip'
import CreateVersionDialog from '../components/editor/CreateVersionDialog'
import VersionHistoryDialog from '../components/editor/VersionHistoryDialog'
import VersionDiffDialog from '../components/editor/VersionDiffDialog'
import type { RootState } from '../store'
import { setCqlContent, setGoToLine } from '../store/editorSlice'
import { findElementLineRange } from '../utils/cqlElementLocator'
import type { CqlElementType } from '../utils/cqlElementLocator'
import { useTranslate, useCreateLibrary, useExportLibrary, useImportLibrary, useLibrariesMetadata, useLibrary } from '../hooks/useCql'
import { useTerminologyValidation } from '../hooks/useTerminologyValidation'
import { useLibraryHistory } from '../hooks/useLibraryHistory'
import { helpContent } from '../constants/helpContent'
import TabPanel, { a11yProps } from '../components/common/TabPanel'

export default function EditorPage() {
  const dispatch = useDispatch()
  const { cqlContent, isTranslating, errors, elmJson, cursorPosition } = useSelector((state: RootState) => state.editor)
  const [rightPanelTab, setRightPanelTab] = useState(0)
  const [showBuilder, setShowBuilder] = useState(false)
  const [lastSavedLibraryId, setLastSavedLibraryId] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const { addToRecent, toggleFavorite, isFavorite } = useLibraryHistory()

  const queryClient = useQueryClient()
  const [versionDialogOpen, setVersionDialogOpen] = useState(false)
  const [historyDialogOpen, setHistoryDialogOpen] = useState(false)
  const [diffDialogOpen, setDiffDialogOpen] = useState(false)
  const [shareDialogOpen, setShareDialogOpen] = useState(false)

  const libraryMatch = cqlContent.match(/library\s+(\S+)(?:\s+version\s+'([^']+)')?/)
  const libraryName = libraryMatch?.[1] || ''
  const libraryVersion = libraryMatch?.[2] || '0.0.0'

  const versionMutation = useMutation({
    mutationFn: (type: string) => cqlApi.createLibraryVersion(libraryName, type),
    onSuccess: (newLib) => {
      queryClient.invalidateQueries({ queryKey: ['libraries'] })
      dispatch(setCqlContent(newLib.cqlContent))
      setVersionDialogOpen(false)
    },
  })

  const { data: historyData = [] } = useQuery({
    queryKey: ['library-history', libraryName],
    queryFn: () => cqlApi.getLibraryHistory(libraryName),
    enabled: historyDialogOpen && !!libraryName,
  })

  const handleSelectVersion = (id: string | number) => {
    cqlApi.getLibrary(String(id)).then((lib) => {
      dispatch(setCqlContent(lib.cqlContent))
      setHistoryDialogOpen(false)
    })
  }

  const handleCompare = async (oldId: string | number, newId: string | number) => {
    return cqlApi.compareLibraryVersions(String(oldId), String(newId))
  }

  const historyVersions = historyData.map((m) => ({
    id: m.id,
    version: m.version,
    status: m.status || 'draft',
    createdAt: m.createdAt,
    updatedAt: m.updatedAt,
  }))

  const diffVersions = historyData.map((m) => ({
    id: m.id,
    version: m.version,
  }))

  const translateMutation = useTranslate()
  const saveLibraryMutation = useCreateLibrary()
  const exportMutation = useExportLibrary()
  const importMutation = useImportLibrary()
  const { data: libraryMetadata } = useLibrariesMetadata()
  const { results: terminologyResults, isValidating: isTermValidating } = useTerminologyValidation(elmJson)
  const { data: currentLibrary } = useLibrary(lastSavedLibraryId)

  const handleTranslate = () => {
    translateMutation.mutate({ cql: cqlContent })
  }

  const handleSaveLibrary = () => {
    saveLibraryMutation.mutate(
      { cql: cqlContent },
      {
        onSuccess: (library) => {
          setLastSavedLibraryId(library.id)
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

  const handleInsertSnippet = useCallback(
    (snippet: string) => {
      const lines = cqlContent.split('\n')
      const lineIdx = Math.min(cursorPosition.line - 1, lines.length)
      // Insert the snippet at the end of the current line, with blank line separation
      lines.splice(lineIdx + 1, 0, '', snippet, '')
      dispatch(setCqlContent(lines.join('\n')))
    },
    [cqlContent, cursorPosition, dispatch]
  )

  const handleGoToElement = useCallback(
    (type: CqlElementType, identifier: string) => {
      const range = findElementLineRange(cqlContent, type, identifier)
      if (range) {
        dispatch(setGoToLine(range.startLine))
      }
    },
    [cqlContent, dispatch]
  )

  const handleDeleteElement = useCallback(
    (type: CqlElementType, identifier: string) => {
      const range = findElementLineRange(cqlContent, type, identifier)
      if (!range) return
      const lines = cqlContent.split('\n')
      // Remove the element lines, plus trailing blank line if present
      let endIdx = range.endLine - 1
      if (endIdx + 1 < lines.length && lines[endIdx + 1].trim() === '') {
        endIdx++
      }
      lines.splice(range.startLine - 1, endIdx - range.startLine + 2)
      dispatch(setCqlContent(lines.join('\n')))
    },
    [cqlContent, dispatch]
  )

  const handleEditElement = useCallback(
    (type: CqlElementType, identifier: string, newSnippet: string) => {
      const range = findElementLineRange(cqlContent, type, identifier)
      if (!range) return
      const lines = cqlContent.split('\n')
      lines.splice(range.startLine - 1, range.endLine - range.startLine + 1, newSnippet)
      dispatch(setCqlContent(lines.join('\n')))
    },
    [cqlContent, dispatch]
  )

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
              <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center" sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'secondary.main', whiteSpace: 'nowrap' }}>
                  CQL Editor
                </Typography>
                <Stack direction="row" spacing={0.5} alignItems="center" sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
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
                  {lastSavedLibraryId && (
                    <Tooltip title={isFavorite(lastSavedLibraryId) ? 'Remove from favorites' : 'Add to favorites'}>
                      <IconButton
                        size="small"
                        onClick={() => toggleFavorite(lastSavedLibraryId)}
                        aria-label="Toggle favorite"
                        sx={{ color: isFavorite(lastSavedLibraryId) ? 'warning.main' : 'text.secondary' }}
                      >
                        {isFavorite(lastSavedLibraryId) ? <StarIcon /> : <StarBorderIcon />}
                      </IconButton>
                    </Tooltip>
                  )}
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
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<ShareIcon />}
                    onClick={() => setShareDialogOpen(true)}
                    disabled={!lastSavedLibraryId}
                    sx={{
                      borderColor: 'rgba(27,58,92,0.3)',
                      color: 'secondary.main',
                      '&:hover': {
                        borderColor: 'secondary.main',
                        bgcolor: 'rgba(27,58,92,0.04)',
                      },
                    }}
                  >
                    Share
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<VersionIcon />}
                    onClick={() => setVersionDialogOpen(true)}
                    disabled={!libraryName}
                    sx={{
                      borderColor: 'rgba(27,58,92,0.3)',
                      color: 'secondary.main',
                      '&:hover': {
                        borderColor: 'secondary.main',
                        bgcolor: 'rgba(27,58,92,0.04)',
                      },
                    }}
                  >
                    Version
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<HistoryIcon />}
                    onClick={() => setHistoryDialogOpen(true)}
                    disabled={!libraryName}
                    sx={{
                      borderColor: 'rgba(27,58,92,0.3)',
                      color: 'secondary.main',
                      '&:hover': {
                        borderColor: 'secondary.main',
                        bgcolor: 'rgba(27,58,92,0.04)',
                      },
                    }}
                  >
                    History
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<CompareIcon />}
                    onClick={() => { setHistoryDialogOpen(false); setDiffDialogOpen(true) }}
                    disabled={!libraryName}
                    sx={{
                      borderColor: 'rgba(27,58,92,0.3)',
                      color: 'secondary.main',
                      '&:hover': {
                        borderColor: 'secondary.main',
                        bgcolor: 'rgba(27,58,92,0.04)',
                      },
                    }}
                  >
                    Compare
                  </Button>
                  <Tooltip title={showBuilder ? 'Hide Builder Panel' : 'Show Builder Panel'}>
                    <ToggleButton
                      size="small"
                      value="builder"
                      selected={showBuilder}
                      onChange={() => setShowBuilder((prev) => !prev)}
                      sx={{
                        border: '1px solid',
                        borderColor: showBuilder ? 'primary.main' : 'rgba(13,115,119,0.3)',
                        color: showBuilder ? 'primary.main' : 'text.secondary',
                        bgcolor: showBuilder ? 'rgba(13,115,119,0.08)' : 'transparent',
                        px: 1,
                        '&:hover': {
                          bgcolor: 'rgba(13,115,119,0.08)',
                        },
                      }}
                    >
                      <BuilderIcon sx={{ fontSize: 18, mr: 0.5 }} />
                      <Typography variant="caption" sx={{ textTransform: 'none' }}>
                        Builder
                      </Typography>
                    </ToggleButton>
                  </Tooltip>
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
              borderLeftColor: showBuilder ? 'primary.main' : 'secondary.main',
              overflow: 'hidden',
            }}
          >
            {showBuilder ? (
              <CqlBuilderPanel
                onInsertSnippet={handleInsertSnippet}
                onDeleteElement={handleDeleteElement}
                onGoToElement={handleGoToElement}
                onEditElement={handleEditElement}
              />
            ) : (
              <>
                <Tabs
                  value={rightPanelTab}
                  onChange={(_, v) => setRightPanelTab(v)}
                  sx={{
                    borderBottom: '1px solid',
                    borderColor: 'rgba(13,115,119,0.1)',
                    bgcolor: 'rgba(27,58,92,0.03)',
                  }}
                >
                  <Tab label="ELM / Errors" {...a11yProps(0, 'editor')} />
                  <Tab label="Execute" {...a11yProps(1, 'editor')} />
                  <Tab label="Dependencies" {...a11yProps(2, 'editor')} />
                </Tabs>
                <Box sx={{ flexGrow: 1, overflow: 'auto' }}>
                  <TabPanel value={rightPanelTab} index={0} prefix="editor" sx={{ height: '100%' }}>
                    <ElmViewer
                      terminologyResults={terminologyResults}
                      isTermValidating={isTermValidating}
                    />
                  </TabPanel>
                  <TabPanel value={rightPanelTab} index={1} prefix="editor" sx={{ height: '100%' }}>
                    <ExecutionPanel />
                  </TabPanel>
                  <TabPanel value={rightPanelTab} index={2} prefix="editor" sx={{ height: '100%' }}>
                    <LibraryDependencyPanel
                      libraryId={lastSavedLibraryId}
                      libraryName={libraryName || null}
                      onLoadLibrary={(lib) => dispatch(setCqlContent(lib.cqlContent))}
                    />
                  </TabPanel>
                </Box>
              </>
            )}
          </Paper>
        </Grid>
      </Grid>
      <CreateVersionDialog
        open={versionDialogOpen}
        onClose={() => setVersionDialogOpen(false)}
        onConfirm={(type) => versionMutation.mutate(type)}
        currentVersion={libraryVersion}
        isPending={versionMutation.isPending}
        entityType="library"
      />
      <VersionHistoryDialog
        open={historyDialogOpen}
        onClose={() => setHistoryDialogOpen(false)}
        versions={historyVersions}
        onSelectVersion={handleSelectVersion}
        entityType="library"
      />
      <VersionDiffDialog
        open={diffDialogOpen}
        onClose={() => setDiffDialogOpen(false)}
        versions={diffVersions}
        onCompare={handleCompare}
      />
      <LibraryShareDialog
        open={shareDialogOpen}
        onClose={() => setShareDialogOpen(false)}
        library={currentLibrary ?? null}
      />
    </Box>
  )
}
