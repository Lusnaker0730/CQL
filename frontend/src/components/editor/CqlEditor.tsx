import { useEffect, useRef, useCallback } from 'react'
import Editor, { BeforeMount, OnMount, OnChange } from '@monaco-editor/react'
import type { editor } from 'monaco-editor'
import { Box, CircularProgress } from '@mui/material'
import { useDispatch, useSelector } from 'react-redux'
import { registerCqlLanguage } from '../../utils/cqlSyntax'
import type { LibraryInfo } from '../../utils/cqlSyntax'
import { setCqlContent, setCursorPosition, setGoToLine } from '../../store/editorSlice'
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
  onEditorRef?: (editor: editor.IStandaloneCodeEditor) => void
}

export default function CqlEditor({
  height = '500px',
  readOnly = false,
  onTranslate,
  onExecute,
  terminologyIssues,
  libraryMetadata,
  onEditorRef,
}: CqlEditorProps) {
  const dispatch = useDispatch()
  const { cqlContent, errors, warnings, goToLine } = useSelector((state: RootState) => state.editor)
  const { preferences } = usePreferences()
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null)
  const monacoRef = useRef<typeof import('monaco-editor') | null>(null)
  const lastExternalContent = useRef(cqlContent)
  const librariesRef = useRef<LibraryInfo[]>([])
  const disposablesRef = useRef<Array<{ dispose: () => void }>>([])
  const pasteListenerRef = useRef<{ node: HTMLElement; handler: EventListener } | null>(null)

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

  // Clean invisible chars, bidi controls, smart quotes from pasted text (Trojan Source prevention)
  const sanitizePastedText = (text: string): string => {
    return text
      // Strip zero-width, bidi controls, soft hyphen, word joiner, and other invisible chars
      .replace(/[\u200B-\u200F\u202A-\u202E\u2060-\u2064\u2066-\u2069\u061C\u00AD\u180E\uFEFF\uFFF9-\uFFFB]/g, '')
      .replace(/[\u2028\u2029]/g, '\n')                // line/paragraph separator → newline
      .replace(/\u00A0/g, ' ')                         // non-breaking space → space
      .replace(/[\u2018\u2019\u201A]/g, "'")            // smart single quotes → '
      .replace(/[\u201C\u201D\u201E]/g, '"')            // smart double quotes → "
      .replace(/\u2013/g, '-')                          // en-dash → -
      .replace(/\u2014/g, '--')                         // em-dash → --
      .replace(/\u2026/g, '...')                        // ellipsis → ...
  }

  const handleEditorWillMount: BeforeMount = (monaco) => {
    registerCqlLanguage(monaco, librariesRef.current)
  }

  const handleEditorDidMount: OnMount = (editor, monaco) => {
    editorRef.current = editor
    monacoRef.current = monaco
    onEditorRef?.(editor)

    // Sanitize only the pasted range, preserving undo stack
    const pasteDisposable = editor.onDidPaste((e) => {
      const model = editor.getModel()
      if (!model) return
      const pastedText = model.getValueInRange(e.range)
      const cleaned = sanitizePastedText(pastedText)
      if (cleaned !== pastedText) {
        editor.executeEdits('paste-sanitize', [{
          range: e.range,
          text: cleaned,
        }])
      }
    })
    disposablesRef.current.push(pasteDisposable)

    // Fallback paste handler for environments where Monaco's built-in paste fails
    const domNode = editor.getDomNode()
    if (domNode) {
      const pasteHandler: EventListener = (e) => {
        const clipText = (e as ClipboardEvent).clipboardData?.getData('text/plain')
        if (!clipText) return

        const modelBefore = editor.getModel()?.getValue()
        setTimeout(() => {
          const modelAfter = editor.getModel()?.getValue()
          if (modelBefore === modelAfter && editor.getModel()) {
            const cleaned = sanitizePastedText(clipText)
            const selections = editor.getSelections()
            if (selections && selections.length > 0) {
              editor.executeEdits('paste-fallback', selections.map(sel => ({
                range: sel,
                text: cleaned,
              })))
            }
          }
        }, 0)
      }
      domNode.addEventListener('paste', pasteHandler)
      pasteListenerRef.current = { node: domNode, handler: pasteHandler }
    }

    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
      onTranslate?.()
    })

    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      onExecute?.()
    })

    const cursorDisposable = editor.onDidChangeCursorPosition((e) => {
      dispatch(setCursorPosition({
        line: e.position.lineNumber,
        column: e.position.column,
      }))
    })
    disposablesRef.current.push(cursorDisposable)
  }

  // Cleanup Monaco subscriptions and DOM listeners on unmount
  useEffect(() => {
    return () => {
      disposablesRef.current.forEach(d => d.dispose())
      disposablesRef.current = []
      if (pasteListenerRef.current) {
        const { node, handler } = pasteListenerRef.current
        node.removeEventListener('paste', handler)
        pasteListenerRef.current = null
      }
      editorRef.current = null
      monacoRef.current = null
    }
  }, [])

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
  // Use executeEdits instead of setValue to preserve Monaco's undo stack
  useEffect(() => {
    if (!editorRef.current) return
    if (cqlContent !== lastExternalContent.current) {
      lastExternalContent.current = cqlContent
      const model = editorRef.current.getModel()
      if (model) {
        const currentValue = model.getValue()
        if (currentValue !== cqlContent) {
          const fullRange = model.getFullModelRange()
          editorRef.current.executeEdits('external', [{
            range: fullRange,
            text: cqlContent,
          }])
        }
      }
    }
  }, [cqlContent])

  // Switch Monaco theme when dark mode preference changes
  useEffect(() => {
    if (!monacoRef.current) return
    monacoRef.current.editor.setTheme(
      preferences.themeMode === 'dark' ? 'cql-theme-dark' : 'cql-theme'
    )
  }, [preferences.themeMode])

  // Go-to-line: reveal and focus requested line, then clear
  useEffect(() => {
    if (goToLine == null || !editorRef.current) return
    editorRef.current.revealLineInCenter(goToLine)
    editorRef.current.setPosition({ lineNumber: goToLine, column: 1 })
    editorRef.current.focus()
    dispatch(setGoToLine(null))
  }, [goToLine, dispatch])

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
        theme={preferences.themeMode === 'dark' ? 'cql-theme-dark' : 'cql-theme'}
        beforeMount={handleEditorWillMount}
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
