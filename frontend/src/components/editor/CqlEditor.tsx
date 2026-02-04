import { useEffect, useRef, useCallback } from 'react'
import Editor, { OnMount, OnChange } from '@monaco-editor/react'
import type { editor } from 'monaco-editor'
import { Box, CircularProgress } from '@mui/material'
import { useDispatch, useSelector } from 'react-redux'
import { registerCqlLanguage } from '../../utils/cqlSyntax'
import { setCqlContent, setCursorPosition } from '../../store/editorSlice'
import type { RootState } from '../../store'

interface CqlEditorProps {
  height?: string | number
  readOnly?: boolean
  onTranslate?: () => void
  onExecute?: () => void
}

export default function CqlEditor({
  height = '500px',
  readOnly = false,
  onTranslate,
  onExecute,
}: CqlEditorProps) {
  const dispatch = useDispatch()
  const { cqlContent, errors, warnings } = useSelector((state: RootState) => state.editor)
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null)
  const monacoRef = useRef<typeof import('monaco-editor') | null>(null)

  const handleEditorDidMount: OnMount = (editor, monaco) => {
    editorRef.current = editor
    monacoRef.current = monaco

    registerCqlLanguage(monaco)

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
        dispatch(setCqlContent(value))
      }
    },
    [dispatch]
  )

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

    monaco.editor.setModelMarkers(model, 'cql', markers)
  }, [errors, warnings])

  return (
    <Box sx={{ height, width: '100%', border: '1px solid #e0e0e0', borderRadius: 1 }}>
      <Editor
        height="100%"
        defaultLanguage="cql"
        value={cqlContent}
        onChange={handleChange}
        onMount={handleEditorDidMount}
        loading={<CircularProgress />}
        options={{
          readOnly,
          minimap: { enabled: false },
          fontSize: 14,
          lineNumbers: 'on',
          scrollBeyondLastLine: false,
          automaticLayout: true,
          tabSize: 2,
          wordWrap: 'on',
          folding: true,
          bracketPairColorization: { enabled: true },
          formatOnPaste: true,
          formatOnType: true,
        }}
      />
    </Box>
  )
}
