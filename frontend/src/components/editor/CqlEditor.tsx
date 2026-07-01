import { useEffect, useRef, useCallback, forwardRef, useImperativeHandle } from 'react'
import Editor, { type BeforeMount, type OnMount, type OnChange } from '../common/MonacoEditor'
import type { editor } from 'monaco-editor'
import { Box, CircularProgress } from '@mui/material'
import { alpha } from '@mui/material/styles'
import { EDITOR_HEIGHT } from '../../constants/layout'
import { useDispatch, useSelector } from 'react-redux'
import { registerCqlLanguage } from '../../utils/cqlSyntax'
import type { LibraryInfo } from '../../utils/cqlSyntax'
import { setCqlContent, setCursorPosition, setGoToLine } from '../../store/editorSlice'
import type { RootState } from '../../store'
import type { TerminologyValidationItem, LibraryMetadata } from '../../types'
import { usePreferences } from '../../hooks/usePreferences'

export interface CqlEditorHandle {
  getContent: () => string
  getEditor: () => editor.IStandaloneCodeEditor | null
  /** Flush current editor content to Redux store */
  flushContent: () => void
}

interface CqlEditorProps {
  height?: string | number
  readOnly?: boolean
  onTranslate?: () => void
  onExecute?: () => void
  terminologyIssues?: TerminologyValidationItem[]
  libraryMetadata?: LibraryMetadata[]
  onEditorRef?: (editor: editor.IStandaloneCodeEditor) => void
  /** Called on every content change (keystroke). Use for debounced consumers. */
  onContentChanged?: (content: string) => void
}

export default forwardRef<CqlEditorHandle, CqlEditorProps>(function CqlEditor({
  height = EDITOR_HEIGHT,
  readOnly = false,
  onTranslate,
  onExecute,
  terminologyIssues,
  libraryMetadata,
  onEditorRef,
  onContentChanged,
}, ref) {
  const dispatch = useDispatch()
  const { cqlContent, errors, warnings, goToLine } = useSelector((state: RootState) => state.editor)
  const { preferences } = usePreferences()
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null)
  const monacoRef = useRef<typeof import('monaco-editor') | null>(null)
  const lastExternalContent = useRef(cqlContent)
  const librariesRef = useRef<LibraryInfo[]>([])
  const disposablesRef = useRef<Array<{ dispose: () => void }>>([])
  const cursorRafRef = useRef(0)

  // Expose imperative handle for parent components
  useImperativeHandle(ref, () => ({
    getContent: () => editorRef.current?.getModel()?.getValue() ?? '',
    getEditor: () => editorRef.current,
    flushContent: () => {
      const value = editorRef.current?.getModel()?.getValue() ?? ''
      lastExternalContent.current = value
      dispatch(setCqlContent(value))
    },
  }), [dispatch])

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
      .replace(/\u2026/g, '...');                        // ellipsis → ...
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

    // Override Ctrl+V to use Clipboard API directly (Monaco's built-in paste
    // relies on the hidden textarea receiving the event, which can fail in
    // certain browser / CSP configurations).
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyV, async () => {
      try {
        const text = await navigator.clipboard.readText()
        const cleaned = sanitizePastedText(text)
        const selections = editor.getSelections()
        if (selections && selections.length > 0) {
          editor.executeEdits('clipboard-paste', selections.map(sel => ({
            range: sel,
            text: cleaned,
          })))
        }
      } catch {
        // Clipboard API unavailable — no-op, user can try native context menu
      }
    })

    // Ctrl+X: cut selected text (or current line if no selection) to clipboard
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyX, async () => {
      try {
        const selection = editor.getSelection()
        if (!selection) return
        const model = editor.getModel()
        if (!model) return

        let textToCut: string
        let range: import('monaco-editor').IRange

        if (selection.isEmpty()) {
          // No selection: cut entire line (standard editor behavior)
          const line = selection.startLineNumber
          textToCut = model.getLineContent(line) + model.getEOL()
          range = {
            startLineNumber: line,
            startColumn: 1,
            endLineNumber: line + 1,
            endColumn: 1,
          }
          // If last line, adjust range
          if (line === model.getLineCount()) {
            const prevLineEnd = line > 1 ? model.getLineMaxColumn(line - 1) : 1
            const prevLine = line > 1 ? line - 1 : line
            textToCut = model.getLineContent(line)
            range = {
              startLineNumber: prevLine,
              startColumn: line > 1 ? prevLineEnd : 1,
              endLineNumber: line,
              endColumn: model.getLineMaxColumn(line),
            }
          }
        } else {
          textToCut = model.getValueInRange(selection)
          range = selection
        }

        await navigator.clipboard.writeText(textToCut)
        editor.executeEdits('clipboard-cut', [{ range, text: '' }])
      } catch {
        // Clipboard API unavailable
      }
    })

    // Ctrl+C: copy selected text to clipboard
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyC, async () => {
      try {
        const selection = editor.getSelection()
        if (!selection) return
        const model = editor.getModel()
        if (!model) return

        let textToCopy: string
        if (selection.isEmpty()) {
          // No selection: copy entire line
          textToCopy = model.getLineContent(selection.startLineNumber) + model.getEOL()
        } else {
          textToCopy = model.getValueInRange(selection)
        }
        await navigator.clipboard.writeText(textToCopy)
      } catch {
        // Clipboard API unavailable
      }
    })

    // Ctrl+S: sync content to Redux, then translate
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
      const value = editor.getModel()?.getValue() ?? ''
      lastExternalContent.current = value
      dispatch(setCqlContent(value))
      onTranslate?.()
    })

    // Ctrl+Enter: sync content to Redux, then execute
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      const value = editor.getModel()?.getValue() ?? ''
      lastExternalContent.current = value
      dispatch(setCqlContent(value))
      onExecute?.()
    })

    // Sync to Redux on blur (covers button-click scenarios)
    const blurDisposable = editor.onDidBlurEditorText(() => {
      const value = editor.getModel()?.getValue() ?? ''
      if (value !== lastExternalContent.current) {
        lastExternalContent.current = value
        dispatch(setCqlContent(value))
      }
    })
    disposablesRef.current.push(blurDisposable)

    // Throttle cursor position dispatch via requestAnimationFrame
    const cursorDisposable = editor.onDidChangeCursorPosition((e) => {
      cancelAnimationFrame(cursorRafRef.current)
      cursorRafRef.current = requestAnimationFrame(() => {
        dispatch(setCursorPosition({
          line: e.position.lineNumber,
          column: e.position.column,
        }))
      })
    })
    disposablesRef.current.push(cursorDisposable)
  }

  // Cleanup Monaco subscriptions and DOM listeners on unmount
  useEffect(() => {
    return () => {
      cancelAnimationFrame(cursorRafRef.current)
      disposablesRef.current.forEach(d => d.dispose())
      disposablesRef.current = []
      editorRef.current = null
      monacoRef.current = null
    }
  }, [])

  // onChange: notify parent for debounced consumers (e.g. useCqlStructure),
  // but do NOT dispatch to Redux on every keystroke.
  const handleChange: OnChange = useCallback(
    (value) => {
      if (value !== undefined) {
        lastExternalContent.current = value
        onContentChanged?.(value)
      }
    },
    [onContentChanged]
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
      sx={(theme) => ({
        height,
        width: '100%',
        border: '1px solid',
        borderColor: alpha(theme.palette.primary.main, 0.15),
        borderRadius: '10px',
        overflow: 'hidden',
        transition: 'all 0.2s ease',
        boxShadow: `inset 0 1px 3px ${alpha(theme.palette.primary.main, 0.06)}`,
        '&:focus-within': {
          borderColor: 'primary.main',
          boxShadow: `0 0 0 3px ${alpha(theme.palette.primary.main, 0.12)}, inset 0 1px 3px ${alpha(theme.palette.primary.main, 0.06)}`,
        },
      })}
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
          contextmenu: true,
        }}
      />
    </Box>
  )
})
