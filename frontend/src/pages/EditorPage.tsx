import { useState, useRef, useCallback, useMemo, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
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
  NoteAdd as NewIcon,
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
  Undo as UndoIcon,
  Redo as RedoIcon,
} from '@mui/icons-material'
import { useSelector, useDispatch } from 'react-redux'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { cqlApi } from '../api'
import { settingsApi } from '../api/settingsApi'
import CqlEditor from '../components/editor/CqlEditor'
import type { CqlEditorHandle } from '../components/editor/CqlEditor'
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
import { setCqlContent, setCqlContentWithHistory, setGoToLine } from '../store/editorSlice'
import { findElementLineRange } from '../utils/cqlElementLocator'
import type { CqlElementType } from '../utils/cqlElementLocator'
import { useTranslate, useCreateLibrary, useExportLibrary, useImportLibrary, useLibrariesMetadata, useLibrary } from '../hooks/useCql'
import { useTerminologyValidation } from '../hooks/useTerminologyValidation'
import { useLibraryHistory } from '../hooks/useLibraryHistory'
import { helpContent } from '../constants/helpContent'
import { PAGE_CONTENT_HEIGHT } from '../constants/layout'
import { STALE_5M } from '../constants/queryConstants'
import { extractApiError } from '../utils/errorUtils'
import { useNotification } from '../hooks/useNotification'
import TabPanel, { a11yProps } from '../components/common/TabPanel'

export default function EditorPage() {
  const { t } = useTranslation('editor')
  const dispatch = useDispatch()
  const isTranslating = useSelector((state: RootState) => state.editor.isTranslating)
  const errors = useSelector((state: RootState) => state.editor.errors)
  const elmJson = useSelector((state: RootState) => state.editor.elmJson)
  const cursorPosition = useSelector((state: RootState) => state.editor.cursorPosition)
  const cqlEditorRef = useRef<CqlEditorHandle>(null)
  const monacoEditorRef = useRef<import('monaco-editor').editor.IStandaloneCodeEditor | null>(null)
  // Local mirror of editor content for UI that needs it (e.g. libraryMatch, disabled checks)
  const [localContent, setLocalContent] = useState('')
  const [rightPanelTab, setRightPanelTab] = useState(0)
  const [showBuilder, setShowBuilder] = useState(false)
  const [lastSavedLibraryId, setLastSavedLibraryId] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const { addToRecent, toggleFavorite, isFavorite } = useLibraryHistory()
  const { showNotification } = useNotification()

  const queryClient = useQueryClient()
  const [versionDialogOpen, setVersionDialogOpen] = useState(false)
  const [historyDialogOpen, setHistoryDialogOpen] = useState(false)
  const [diffDialogOpen, setDiffDialogOpen] = useState(false)
  const [shareDialogOpen, setShareDialogOpen] = useState(false)

  const libraryMatch = localContent.match(/library\s+(\S+)(?:\s+version\s+'([^']+)')?/)
  const libraryName = libraryMatch?.[1] || ''
  const libraryVersion = libraryMatch?.[2] || '0.0.0'

  // Sync localContent from Redux on external loads (e.g. loading a library)
  const cqlContentFromRedux = useSelector((state: RootState) => state.editor.cqlContent)
  useEffect(() => {
    setLocalContent(cqlContentFromRedux)
  }, [cqlContentFromRedux])

  // Called on every keystroke from Monaco — updates local mirror only
  const handleContentChanged = useCallback((content: string) => {
    setLocalContent(content)
  }, [])

  // Flush editor content to Redux and return it — call before any action that needs current content
  const syncAndGetContent = useCallback(() => {
    if (cqlEditorRef.current) {
      cqlEditorRef.current.flushContent()
      return cqlEditorRef.current.getContent()
    }
    return localContent
  }, [localContent])

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
    }).catch((err) => {
      showNotification('Failed to load library version: ' + extractApiError(err), 'error')
    })
  }

  const handleCompare = async (oldId: string | number, newId: string | number) => {
    return cqlApi.compareLibraryVersions(String(oldId), String(newId))
  }

  const historyVersions = useMemo(
    () =>
      historyData.map((m) => ({
        id: m.id,
        version: m.version,
        status: m.status || 'draft',
        createdAt: m.createdAt,
        updatedAt: m.updatedAt,
      })),
    [historyData]
  )

  const diffVersions = useMemo(
    () =>
      historyData.map((m) => ({
        id: m.id,
        version: m.version,
      })),
    [historyData]
  )

  const translateMutation = useTranslate()
  const saveLibraryMutation = useCreateLibrary()
  const exportMutation = useExportLibrary()
  const importMutation = useImportLibrary()
  const { data: libraryMetadata } = useLibrariesMetadata()
  const { results: terminologyResults, isValidating: isTermValidating } = useTerminologyValidation(elmJson)
  const { data: currentLibrary } = useLibrary(lastSavedLibraryId)
  const { data: aiStatus } = useQuery({
    queryKey: ['ai-status'],
    queryFn: () => settingsApi.getAiStatus(),
    staleTime: STALE_5M,
  })

  const handleNewLibrary = useCallback(() => {
    const template = `library NewLibrary version '1.0.0'\n\nusing FHIR version '4.0.1'\ninclude FHIRHelpers version '4.0.1' called FHIRHelpers\n\ncontext Patient\n\n`
    dispatch(setCqlContent(template))
    setLastSavedLibraryId(null)
  }, [dispatch])

  const handleTranslate = useCallback(() => {
    const content = syncAndGetContent()
    translateMutation.mutate({ cql: content })
  }, [syncAndGetContent, translateMutation])

  const handleSaveLibrary = useCallback(() => {
    const content = syncAndGetContent()
    saveLibraryMutation.mutate(
      { cql: content },
      {
        onSuccess: (library) => {
          setLastSavedLibraryId(library.id)
          addToRecent({ id: library.id, name: library.name, version: library.version })
        },
      }
    )
  }, [syncAndGetContent, saveLibraryMutation, addToRecent])

  const handleExport = useCallback(() => {
    const content = syncAndGetContent()
    saveLibraryMutation.mutate(
      { cql: content },
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
  }, [syncAndGetContent, saveLibraryMutation, addToRecent, exportMutation])

  const handleImport = useCallback(() => {
    fileInputRef.current?.click()
  }, [])

  const handleFileSelected = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    const reader = new FileReader()
    reader.onload = (event) => {
      const content = event.target?.result as string
      if (file.name.endsWith('.cql')) {
        // Load CQL content directly into editor
        dispatch(setCqlContent(content))
      } else {
        // Existing FHIR Library JSON import flow
        try {
          const fhirLibrary = JSON.parse(content)
          importMutation.mutate(fhirLibrary)
        } catch {
          showNotification('Invalid JSON file', 'error')
        }
      }
    }
    reader.readAsText(file)
    e.target.value = ''
  }

  const handleInsertSnippet = useCallback(
    (snippet: string) => {
      const content = cqlEditorRef.current?.getContent() ?? localContent
      const lines = content.split('\n')
      const lineIdx = Math.min(cursorPosition.line - 1, lines.length)
      // Insert the snippet at the end of the current line, with blank line separation
      lines.splice(lineIdx + 1, 0, '', snippet, '')
      dispatch(setCqlContentWithHistory(lines.join('\n')))
    },
    [localContent, cursorPosition, dispatch]
  )

  const handleGoToElement = useCallback(
    (type: CqlElementType, identifier: string) => {
      const content = cqlEditorRef.current?.getContent() ?? localContent
      const range = findElementLineRange(content, type, identifier)
      if (range) {
        dispatch(setGoToLine(range.startLine))
      }
    },
    [localContent, dispatch]
  )

  const handleDeleteElement = useCallback(
    (type: CqlElementType, identifier: string) => {
      const content = cqlEditorRef.current?.getContent() ?? localContent
      const range = findElementLineRange(content, type, identifier)
      if (!range) return
      const lines = content.split('\n')
      // Remove the element lines, plus trailing blank line if present
      let endIdx = range.endLine - 1
      if (endIdx + 1 < lines.length && lines[endIdx + 1].trim() === '') {
        endIdx++
      }
      lines.splice(range.startLine - 1, endIdx - range.startLine + 2)
      dispatch(setCqlContentWithHistory(lines.join('\n')))
    },
    [localContent, dispatch]
  )

  const handleEditElement = useCallback(
    (type: CqlElementType, identifier: string, newSnippet: string) => {
      const content = cqlEditorRef.current?.getContent() ?? localContent
      const range = findElementLineRange(content, type, identifier)
      if (!range) return
      const lines = content.split('\n')
      lines.splice(range.startLine - 1, range.endLine - range.startLine + 1, newSnippet)
      dispatch(setCqlContentWithHistory(lines.join('\n')))
    },
    [localContent, dispatch]
  )

  return (
    <Box sx={{ height: PAGE_CONTENT_HEIGHT, p: 2 }}>
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileSelected}
        accept=".json,.cql"
        style={{ display: 'none' }}
      />
      <Grid spacing={2} sx={{ height: '100%' }}>
        <Grid size={{ xs: 12, md: 2 }} sx={{ height: '100%', display: { xs: 'none', md: 'block' } }}>
          <LibraryQuickAccess />
        </Grid>

        <Grid size={{ xs: 12, md: 5.5 }} sx={{ height: '100%' }}>
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
                  {t('title')}
                </Typography>
                <Stack direction="row" spacing={0.5} alignItems="center" sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
                  <Tooltip title={t('toolbar.newLibrary')}>
                    <IconButton size="small" onClick={handleNewLibrary}>
                      <NewIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title={t('toolbar.undo')}>
                    <span>
                      <IconButton size="small" onClick={() => { monacoEditorRef.current?.trigger('toolbar', 'undo', null); monacoEditorRef.current?.focus() }}>
                        <UndoIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                  <Tooltip title={t('toolbar.redo')}>
                    <span>
                      <IconButton size="small" onClick={() => { monacoEditorRef.current?.trigger('toolbar', 'redo', null); monacoEditorRef.current?.focus() }}>
                        <RedoIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                  <Button
                    size="small"
                    variant="contained"
                    startIcon={
                      isTranslating ? <CircularProgress size={16} color="inherit" /> : <TranslateIcon />
                    }
                    onClick={handleTranslate}
                    disabled={isTranslating || !localContent}
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
                    {isTranslating ? t('toolbar.translating') : t('toolbar.translate')}
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
                    {t('toolbar.saveLibrary')}
                  </Button>
                  {lastSavedLibraryId && (
                    <Tooltip title={isFavorite(lastSavedLibraryId) ? t('toolbar.removeFavorite') : t('toolbar.addFavorite')}>
                      <IconButton
                        size="small"
                        onClick={() => toggleFavorite(lastSavedLibraryId)}
                        aria-label={t('toolbar.toggleFavorite')}
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
                    disabled={!localContent || errors.length > 0}
                    sx={{
                      borderColor: 'rgba(27,58,92,0.3)',
                      color: 'secondary.main',
                      '&:hover': {
                        borderColor: 'secondary.main',
                        bgcolor: 'rgba(27,58,92,0.04)',
                      },
                    }}
                  >
                    {t('toolbar.export')}
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
                    {t('toolbar.import')}
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
                    {t('toolbar.share')}
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
                    {t('toolbar.version')}
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
                    {t('toolbar.history')}
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
                    {t('toolbar.compare')}
                  </Button>
                  <Tooltip title={showBuilder ? t('toolbar.hideBuilder') : t('toolbar.showBuilder')}>
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
                        {t('toolbar.builder')}
                      </Typography>
                    </ToggleButton>
                  </Tooltip>
                </Stack>
              </Stack>
            </Box>
            <Box sx={{ flexGrow: 1, overflow: 'hidden' }}>
              <CqlEditor
                ref={cqlEditorRef}
                height="100%"
                onTranslate={handleTranslate}
                terminologyIssues={terminologyResults.filter((r) => r.status !== 'valid')}
                libraryMetadata={libraryMetadata}
                onEditorRef={(editor) => { monacoEditorRef.current = editor }}
                onContentChanged={handleContentChanged}
              />
            </Box>
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, md: 4.5 }} sx={{ height: '100%' }}>
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
                editorContent={localContent}
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
                  <Tab label={t('tabs.elmErrors')} {...a11yProps(0, 'editor')} />
                  <Tab label={t('tabs.execute')} {...a11yProps(1, 'editor')} />
                  <Tab label={t('tabs.dependencies')} {...a11yProps(2, 'editor')} />
                </Tabs>
                <Box sx={{ flexGrow: 1, overflow: 'auto' }}>
                  <TabPanel value={rightPanelTab} index={0} prefix="editor" sx={{ height: '100%' }}>
                    <ElmViewer
                      terminologyResults={terminologyResults}
                      isTermValidating={isTermValidating}
                      aiEnabled={aiStatus?.enabled ?? false}
                      onApplyFix={(suggestedCql) => {
                        dispatch(setCqlContentWithHistory(suggestedCql))
                      }}
                    />
                  </TabPanel>
                  <TabPanel value={rightPanelTab} index={1} prefix="editor" sx={{ height: '100%' }}>
                    <ExecutionPanel getLatestCql={syncAndGetContent} />
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
