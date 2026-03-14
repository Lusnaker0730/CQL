import { useMemo } from 'react'
import { useTwcoreFullCatalog } from './useTwcoreCatalog'

/**
 * Wraps useTwcoreFullCatalog with a client-side filter on name, resourceType,
 * category names, and code/display fields.
 */
export function useFilteredTwcoreCatalog(filter: string) {
  const { data: twcoreCatalog = [], isLoading } = useTwcoreFullCatalog()

  const filteredCatalog = useMemo(() => {
    if (!filter.trim()) return twcoreCatalog
    const lower = filter.toLowerCase()
    return twcoreCatalog
      .map((entry) => {
        const filteredCategories = entry.categories
          .map((cat) => ({
            ...cat,
            codes: cat.codes.filter(
              (c) =>
                c.code.toLowerCase().includes(lower) ||
                c.display.toLowerCase().includes(lower) ||
                c.displayZh.toLowerCase().includes(lower)
            ),
          }))
          .filter((cat) => cat.codes.length > 0 || cat.name.toLowerCase().includes(lower))
        if (
          filteredCategories.length > 0 ||
          entry.name.toLowerCase().includes(lower) ||
          entry.resourceType.toLowerCase().includes(lower)
        ) {
          return { ...entry, categories: filteredCategories.length > 0 ? filteredCategories : entry.categories }
        }
        return null
      })
      .filter(Boolean) as typeof twcoreCatalog
  }, [twcoreCatalog, filter])

  return { filteredCatalog, isLoading }
}
