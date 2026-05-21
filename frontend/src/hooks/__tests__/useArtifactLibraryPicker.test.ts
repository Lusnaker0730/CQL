import { describe, it, expect, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useArtifactLibraryPicker } from '../useArtifactLibraryPicker'
import type { LibraryDefinitionReference } from '../../components/cql-libraries/LibraryDefinitionPicker'

const sampleRef: LibraryDefinitionReference = {
  libraryName: 'SharedLogic',
  libraryVersion: '1.2.0',
  definitionName: 'HasDiabetes',
  alias: 'shared',
}

describe('useArtifactLibraryPicker', () => {
  it('starts with target=null (picker closed)', () => {
    const { result } = renderHook(() =>
      useArtifactLibraryPicker(vi.fn(), vi.fn()),
    )
    expect(result.current.target).toBeNull()
  })

  it('openForInclude sets target to include', () => {
    const { result } = renderHook(() =>
      useArtifactLibraryPicker(vi.fn(), vi.fn()),
    )
    act(() => result.current.openForInclude())
    expect(result.current.target).toBe('include')
  })

  it('openForExclude sets target to exclude', () => {
    const { result } = renderHook(() =>
      useArtifactLibraryPicker(vi.fn(), vi.fn()),
    )
    act(() => result.current.openForExclude())
    expect(result.current.target).toBe('exclude')
  })

  it('close resets target to null without dispatching', () => {
    const onAddInclude = vi.fn()
    const onAddExclude = vi.fn()
    const { result } = renderHook(() =>
      useArtifactLibraryPicker(onAddInclude, onAddExclude),
    )
    act(() => result.current.openForInclude())
    act(() => result.current.close())
    expect(result.current.target).toBeNull()
    expect(onAddInclude).not.toHaveBeenCalled()
    expect(onAddExclude).not.toHaveBeenCalled()
  })

  it('handleSelect with include target dispatches to onAddIncludeElement', () => {
    const onAddInclude = vi.fn()
    const onAddExclude = vi.fn()
    const { result } = renderHook(() =>
      useArtifactLibraryPicker(onAddInclude, onAddExclude),
    )
    act(() => result.current.openForInclude())
    act(() => result.current.handleSelect(sampleRef))

    // Element was converted and dispatched to the inclusion handler only.
    expect(onAddExclude).not.toHaveBeenCalled()
    expect(onAddInclude).toHaveBeenCalledTimes(1)
    const element = onAddInclude.mock.calls[0][0]
    expect(element.type).toBe('externalCqlElement')
    expect(element.name).toBe('shared."HasDiabetes"')
    // Picker closes after select.
    expect(result.current.target).toBeNull()
  })

  it('handleSelect with exclude target dispatches to onAddExcludeElement', () => {
    const onAddInclude = vi.fn()
    const onAddExclude = vi.fn()
    const { result } = renderHook(() =>
      useArtifactLibraryPicker(onAddInclude, onAddExclude),
    )
    act(() => result.current.openForExclude())
    act(() => result.current.handleSelect(sampleRef))

    expect(onAddInclude).not.toHaveBeenCalled()
    expect(onAddExclude).toHaveBeenCalledTimes(1)
    expect(result.current.target).toBeNull()
  })

  it('handleSelect without an open target does nothing (defensive)', () => {
    // Regression guard: if a stray select event fires while picker is closed,
    // we must not accidentally insert an element into either tree.
    const onAddInclude = vi.fn()
    const onAddExclude = vi.fn()
    const { result } = renderHook(() =>
      useArtifactLibraryPicker(onAddInclude, onAddExclude),
    )
    act(() => result.current.handleSelect(sampleRef))
    expect(onAddInclude).not.toHaveBeenCalled()
    expect(onAddExclude).not.toHaveBeenCalled()
    expect(result.current.target).toBeNull()
  })

  it('target state resets between two sequential picks to different trees', () => {
    // Uncommon flow but possible: author picks for include, then re-opens for
    // exclude and picks another. Both handlers should fire exactly once for
    // their respective tree, never cross-contaminating.
    const onAddInclude = vi.fn()
    const onAddExclude = vi.fn()
    const { result } = renderHook(() =>
      useArtifactLibraryPicker(onAddInclude, onAddExclude),
    )

    act(() => result.current.openForInclude())
    act(() => result.current.handleSelect(sampleRef))
    act(() => result.current.openForExclude())
    act(() => result.current.handleSelect({ ...sampleRef, alias: 'ex' }))

    expect(onAddInclude).toHaveBeenCalledTimes(1)
    expect(onAddExclude).toHaveBeenCalledTimes(1)
    expect(onAddExclude.mock.calls[0][0].fields.find((f: { id: string; value?: unknown }) => f.id === 'alias').value).toBe('ex')
  })
})
