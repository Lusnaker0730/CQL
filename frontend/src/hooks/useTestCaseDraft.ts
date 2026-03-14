import { useEffect, useRef, useCallback, useState } from 'react'
import { AUTOSAVE_DEBOUNCE_MS } from '../constants/timing'

const DRAFT_KEY_PREFIX = 'testcase-draft'
const STALENESS_MS = 7 * 24 * 60 * 60 * 1000 // 7 days

export interface TestCaseDraft {
  title: string
  description: string
  bundleJson: string
  expectedPops: Record<string, boolean>
  series: string
  savedAt: number
}

function getDraftKey(measureId: number, testCaseId: number | null): string {
  return `${DRAFT_KEY_PREFIX}-${measureId}-${testCaseId ?? 'new'}`
}

function loadDraft(key: string): TestCaseDraft | null {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const draft: TestCaseDraft = JSON.parse(raw)
    if (Date.now() - draft.savedAt > STALENESS_MS) {
      localStorage.removeItem(key)
      return null
    }
    return draft
  } catch {
    return null
  }
}

interface UseTestCaseDraftOptions {
  measureId: number
  testCaseId: number | null
  title: string
  description: string
  bundleJson: string
  expectedPops: Record<string, boolean>
  series: string
}

interface UseTestCaseDraftReturn {
  restoredDraft: TestCaseDraft | null
  dismissDraft: () => void
}

export function useTestCaseDraft({
  measureId,
  testCaseId,
  title,
  description,
  bundleJson,
  expectedPops,
  series,
}: UseTestCaseDraftOptions): UseTestCaseDraftReturn {
  const draftKey = getDraftKey(measureId, testCaseId)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [restoredDraft, setRestoredDraft] = useState<TestCaseDraft | null>(() => loadDraft(draftKey))

  // Auto-save on change (debounced)
  useEffect(() => {
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => {
      const draft: TestCaseDraft = {
        title,
        description,
        bundleJson,
        expectedPops,
        series,
        savedAt: Date.now(),
      }
      localStorage.setItem(draftKey, JSON.stringify(draft))
    }, AUTOSAVE_DEBOUNCE_MS)
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [title, description, bundleJson, expectedPops, series, draftKey])

  const dismissDraft = useCallback(() => {
    localStorage.removeItem(draftKey)
    setRestoredDraft(null)
  }, [draftKey])

  return { restoredDraft, dismissDraft }
}

/** Remove the draft from localStorage (call on successful save). */
export function clearTestCaseDraft(measureId: number, testCaseId: number | null): void {
  localStorage.removeItem(getDraftKey(measureId, testCaseId))
}

/** sessionStorage helpers for preserving which test case is being edited */
const EDITING_KEY_PREFIX = 'testcase-editing'

export function saveEditingState(measureId: number, testCaseId: number | 'new'): void {
  sessionStorage.setItem(`${EDITING_KEY_PREFIX}-${measureId}`, String(testCaseId))
}

export function loadEditingState(measureId: number): number | 'new' | null {
  const raw = sessionStorage.getItem(`${EDITING_KEY_PREFIX}-${measureId}`)
  if (!raw) return null
  if (raw === 'new') return 'new'
  const id = parseInt(raw, 10)
  return isNaN(id) ? null : id
}

export function clearEditingState(measureId: number): void {
  sessionStorage.removeItem(`${EDITING_KEY_PREFIX}-${measureId}`)
}
