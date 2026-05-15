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
