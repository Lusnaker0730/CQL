import { useCallback, useState } from 'react'
import type { ElementInstance } from '../types/authoring'
import type { LibraryDefinitionReference } from '../components/cql-libraries/LibraryDefinitionPicker'
import { libraryReferenceToElement } from '../utils/libraryReference'

export type LibraryPickerTarget = 'include' | 'exclude' | null

export interface ArtifactLibraryPickerState {
  /** Current picker target. null means the picker dialog is closed. */
  target: LibraryPickerTarget
  /** Opens the picker to insert into the inclusion tree when a reference is selected. */
  openForInclude: () => void
  /** Opens the picker to insert into the exclusion tree when a reference is selected. */
  openForExclude: () => void
  /** Closes the picker without inserting anything. */
  close: () => void
  /** Called by the picker dialog when the author confirms a library definition.
   *  Converts the reference to an {@code externalCqlElement} tree node and
   *  dispatches to the correct add-handler based on the current target. */
  handleSelect: (reference: LibraryDefinitionReference) => void
}

/**
 * Bundles the CDS artifact library picker state + dispatch logic so the
 * integration can be tested without mounting the entire ArtifactWorkspace.
 *
 * <p>ArtifactWorkspace calls both of these {@code onAdd*} callbacks anyway
 * (via the existing inclusion / exclusion tree handlers); this hook just
 * routes the picker's selection to the right one.
 */
export function useArtifactLibraryPicker(
  onAddIncludeElement: (element: ElementInstance) => void,
  onAddExcludeElement: (element: ElementInstance) => void,
): ArtifactLibraryPickerState {
  const [target, setTarget] = useState<LibraryPickerTarget>(null)

  const openForInclude = useCallback(() => setTarget('include'), [])
  const openForExclude = useCallback(() => setTarget('exclude'), [])
  const close = useCallback(() => setTarget(null), [])

  const handleSelect = useCallback(
    (reference: LibraryDefinitionReference) => {
      const element = libraryReferenceToElement(reference)
      if (target === 'include') {
        onAddIncludeElement(element)
      } else if (target === 'exclude') {
        onAddExcludeElement(element)
      }
      setTarget(null)
    },
    [target, onAddIncludeElement, onAddExcludeElement],
  )

  return { target, openForInclude, openForExclude, close, handleSelect }
}
