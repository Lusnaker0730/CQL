import { useEffect, useRef, useCallback } from 'react'
import Editor, { OnMount, OnChange } from '@monaco-editor/react'
import type { editor } from 'monaco-editor'
import { Box, CircularProgress } from '@mui/material'
import { useDispatch, useSelector } from 'react-redux'
import { registerCqlLanguage } from '../../utils/cqlSyntax'
import type { LibraryInfo } from '../../utils/cqlSyntax'
import { setCqlContent, setCursorPosition } from '../../store/editorSlice'
import type { RootState } from '../../store'
import type { TerminologyValidationItem, LibraryMetadata } from '../../types'
import { usePreferences } from '../../hooks/usePreferences'

interface CqlEditorProps {
  height?: string | number
  readOnly?: boolean
  onTranslate?: () => void
  onExecute?: () => void
  terminologyIssues?: TerminologyValidationItem[]
  libraryMetadata?: LibraryMetadata[]
}

export default function CqlEditor({
  height = '500px',
  readOnly = false,
  onTranslate,
  onExecute,
  terminologyIssues,
  libraryMetadata,
}: CqlEditorProps) {
  const dispatch = useDispatch()
  const { cqlContent, errors, warnings } = useSelector((state: RootState) => state.editor)
  const { preferences } = usePreferences()
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null)
  const monacoRef = useRef<typeof import('monaco-editor') | null>(null)
  const lastExternalContent = useRef(cqlContent)
  const librariesRef = useRef<LibraryInfo[]>([])

  // Keep libraries ref in sync
  useEffect(() => {
    if (libraryMetadata) {
      librariesRef.current = libraryMetadata.map((m) => ({
        name: m.name,
        version: m.version,
        expressions: m.expressions,
        functions: m.functions,
      }))
    }
  }, [libraryMetadata])

  const handleEditorDidMount: OnMount = (editor, monaco) => {
    editorRef.current = editor
    monacoRef.current = monaco

    registerCqlLanguage(monaco, librariesRef.current)

    editor.updateOptions({
      theme: 'cql-theme',
    })

    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
      onTranslate?.()
    })

    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      onExecute?.()
    })

    editor.onDidChangeCursorPosition((e) => {
      dispatch(setCursorPosition({
        line: e.position.lineNumber,
        column: e.position.column,
      }))
    })
  }

  const handleChange: OnChange = useCallback(
    (value) => {
      if (value !== undefined) {
        lastExternalContent.current = value
        dispatch(setCqlContent(value))
      }
    },
    [dispatch]
  )

  // Sync content from Redux only when it changes externally (e.g., loading a library)
  useEffect(() => {
    if (!editorRef.current) return
    if (cqlContent !== lastExternalContent.current) {
      lastExternalContent.current = cqlContent
      const currentValue = editorRef.current.getValue()
      if (currentValue !== cqlContent) {
        editorRef.current.setValue(cqlContent)
      }
    }
  }, [cqlContent])

  useEffect(() => {
    if (!editorRef.current || !monacoRef.current) return

    const monaco = monacoRef.current
    const model = editorRef.current.getModel()
    if (!model) return

    const markers: editor.IMarkerData[] = [
      ...errors.map((error) => ({
        severity: monaco.MarkerSeverity.Error,
        message: error.message,
        startLineNumber: error.startLine || 1,
        startColumn: error.startColumn || 1,
        endLineNumber: error.endLine || error.startLine || 1,
        endColumn: error.endColumn || error.startColumn || 1,
      })),
      ...warnings.map((warning) => ({
        severity: monaco.MarkerSeverity.Warning,
        message: warning.message,
        startLineNumber: warning.startLine || 1,
        startColumn: warning.startColumn || 1,
        endLineNumber: warning.endLine || warning.startLine || 1,
        endColumn: warning.endColumn || warning.startColumn || 1,
      })),
    ]

    // Add terminology issue markers
    if (terminologyIssues && terminologyIssues.length > 0) {
      const content = model.getValue()
      const lines = content.split('\n')

      for (const issue of terminologyIssues) {
        // Search for the terminology reference name in the editor content
        const searchName = `"${issue.name}"`
        for (let i = 0; i < lines.length; i++) {
          const col = lines[i].indexOf(searchName)
          if (col !== -1) {
            markers.push({
              severity: monaco.MarkerSeverity.Info,
              message: `Terminology: ${issue.detail || `${issue.type} "${issue.name}" could not be validated`}`,
              startLineNumber: i + 1,
              startColumn: col + 1,
              endLineNumber: i + 1,
              endColumn: col + 1 + searchName.length,
            })
            break
          }
        }
      }
    }

    monaco.editor.setModelMarkers(model, 'cql', markers)
  }, [errors, warnings, terminologyIssues])

  return (
    <Box
      sx={{
        height,
        width: '100%',
        border: '1px solid',
        borderColor: 'rgba(13,115,119,0.15)',
        borderRadius: '10px',
        overflow: 'hidden',
        transition: 'all 0.2s ease',
        boxShadow: 'inset 0 1px 3px rgba(13,115,119,0.06)',
        '&:focus-within': {
          borderColor: 'primary.main',
          boxShadow: '0 0 0 3px rgba(13,115,119,0.12), inset 0 1px 3px rgba(13,115,119,0.06)',
        },
      }}
    >
      <Editor
        height="100%"
        defaultLanguage="cql"
        defaultValue={cqlContent}
        onChange={handleChange}
        onMount={handleEditorDidMount}
        loading={<CircularProgress />}
        options={{
          readOnly,
          minimap: { enabled: preferences.editorMinimap },
          fontSize: preferences.editorFontSize,
          lineNumbers: 'on',
          scrollBeyondLastLine: false,
          automaticLayout: true,
          tabSize: preferences.editorTabSize,
          wordWrap: preferences.editorWordWrap,
          folding: true,
          bracketPairColorization: { enabled: true },
          formatOnPaste: false,
          formatOnType: true,
        }}
      />
    </Box>
  )
}
