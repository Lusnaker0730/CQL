import '@testing-library/jest-dom'
import { vi } from 'vitest'
import React from 'react'

// Stub monaco-editor at the module level so any test that transitively
// imports a Monaco-using component (CqlEditor / SandboxPanel etc) doesn't
// trigger the real Monaco initialization. jsdom is missing many browser
// APIs Monaco's internals call at import time (document.queryCommandSupported,
// MutationObserver edge cases) — without this stub, every Monaco-loading
// test fails on module load.
vi.mock('monaco-editor', () => ({}))

// Stub the @monaco-editor/react Editor / DiffEditor / hooks at the import
// level so individual tests don't need to repeat the mock. Replaces the
// per-file vi.mock that CqlEditor.test.tsx used historically.
vi.mock('@monaco-editor/react', () => {
  const Stub = ({
    value, defaultValue, onChange,
  }: { value?: string; defaultValue?: string; onChange?: (v: string) => void }) =>
    React.createElement('textarea', {
      'data-testid': 'monaco-editor',
      value: value ?? defaultValue ?? '',
      onChange: (e: React.ChangeEvent<HTMLTextAreaElement>) => onChange?.(e.target.value),
    })
  return {
    default: Stub,
    DiffEditor: Stub,
    Editor: Stub,
    useMonaco: () => null,
    loader: { config: () => undefined, init: () => Promise.resolve({}) },
  }
})

// The wrapper at components/common/MonacoEditor passes through to
// @monaco-editor/react (mocked above) and monaco-editor (mocked above), so
// no separate mock is needed for it.

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { store = {} },
    get length() { return Object.keys(store).length },
    key: (index: number) => Object.keys(store)[index] ?? null,
  }
})()

Object.defineProperty(window, 'localStorage', { value: localStorageMock })

// Mock sessionStorage
const sessionStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { store = {} },
    get length() { return Object.keys(store).length },
    key: (index: number) => Object.keys(store)[index] ?? null,
  }
})()

Object.defineProperty(window, 'sessionStorage', { value: sessionStorageMock })

// Reset storage before each test
beforeEach(() => {
  localStorageMock.clear()
  sessionStorageMock.clear()
})
