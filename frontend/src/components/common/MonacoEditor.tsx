// PAT-165 follow-up: lazy-load wrapper for @monaco-editor/react.
//
// This file is the single place where monaco-editor is statically imported.
// All editor components in the app (CqlEditor, CqlLibraryEditorTab,
// SandboxPanel, ResourceEditorDialog, CqlPreviewPanel, CqlPreviewBox)
// re-export Editor from here instead of directly from '@monaco-editor/react'.
//
// Why: Vite's manualChunks puts monaco-editor in its own vendor-monaco chunk
// (~3.7 MB / 974 KB gzipped). When that chunk is referenced from main.tsx, it
// loads on every page including Landing / Login. Routing this single import
// through a file that is only imported by editor-bearing components means the
// chunk is reached only via lazy-loaded routes (EditorPage / CdsPage /
// AuthoringPage / FhirPage / CqlLibrariesPage), so non-editor pages don't pay
// the bundle cost.
//
// loader.config({ monaco }) is still required to prevent the runtime CDN
// fallback to jsdelivr (blocked by PAT-157 CSP). See PR #521 for context.

// eslint-disable react-refresh/only-export-components --
// This module deliberately re-exports both a component (Editor) and a hook
// (useMonaco) so all monaco-editor consumers go through the same entry
// point that runs loader.config({ monaco }) on import. Splitting would
// either run the side effect twice or risk a consumer importing the hook
// without triggering the loader config.

import * as monaco from 'monaco-editor'
import Editor, { loader, useMonaco, DiffEditor } from '@monaco-editor/react'
import type {
  BeforeMount,
  OnMount,
  OnChange,
  OnValidate,
  EditorProps,
  DiffEditorProps,
} from '@monaco-editor/react'

// PAT-165 follow-up: explicit Monaco worker configuration.
// When Monaco was loaded from jsdelivr CDN (pre-PAT-157), its loader.js
// configured MonacoEnvironment for us automatically. After self-hosting via
// PR #521 and lazy-loading via PR #526, no one tells Monaco where to find
// its workers — the editor then logs "Could not create web worker(s)" and
// falls back to running worker code on the main thread (UI freezes on
// heavy ops). Below uses Vite's `?worker` query so each worker is bundled
// as a separate chunk loaded lazily on demand.
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

// `self.MonacoEnvironment` is the contract Monaco looks for. Set at module
// scope so it's in place before any Editor mounts. Workers map by language
// label; the default catch-all is the core editor worker (handles syntax
// highlighting and tokenization for languages without a dedicated worker —
// our custom CQL syntax falls into this bucket).
;(globalThis as unknown as { MonacoEnvironment: monaco.Environment }).MonacoEnvironment = {
  getWorker(_workerId: string, label: string) {
    if (label === 'json') return new jsonWorker()
    if (label === 'css' || label === 'scss' || label === 'less') return new cssWorker()
    if (label === 'html' || label === 'handlebars' || label === 'razor') return new htmlWorker()
    if (label === 'typescript' || label === 'javascript') return new tsWorker()
    return new editorWorker()
  },
}

// Module-scope side effect — runs once when this module is first imported by
// any editor component, before Editor mounts.
loader.config({ monaco })

export default Editor
// eslint-disable-next-line react-refresh/only-export-components
export { DiffEditor, useMonaco }
export type {
  BeforeMount,
  OnMount,
  OnChange,
  OnValidate,
  EditorProps,
  DiffEditorProps,
}
