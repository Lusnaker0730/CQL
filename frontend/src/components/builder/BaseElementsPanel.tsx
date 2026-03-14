import { useMemo, useState, useCallback, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useSelector } from 'react-redux'
import {
  Typography,
  Stack,
  IconButton,
  Tooltip,
  Chip,
} from '@mui/material'
import {
  PushPin as PinIcon,
  PushPinOutlined as PinOutlinedIcon,
} from '@mui/icons-material'
import TypeChip from './TypeChip'
import type { CqlStructure } from '../../hooks/useCqlStructure'
import type { RootState } from '../../store'
import { parseCqlDefineBlocks, findQuotedReferences } from '../../utils/cqlNames'

interface BaseElementsPanelProps {
  structure: CqlStructure
  onGoTo?: (name: string) => void
}

/** Scan CQL content to count how many times each definition is referenced */
function countReferences(structure: CqlStructure, cqlContent: string): Map<string, number> {
  const counts = new Map<string, number>()
  const knownNames = new Set(structure.expressions.map((e) => e.name))

  for (const name of knownNames) {
    counts.set(name, 0)
  }

  for (const block of parseCqlDefineBlocks(cqlContent)) {
    const refs = findQuotedReferences(block.body, knownNames, block.name)
    for (const refName of refs) {
      counts.set(refName, (counts.get(refName) || 0) + 1)
    }
  }

  return counts
}

const STORAGE_KEY = 'cql-builder-base-elements'

export default function BaseElementsPanel({ structure, onGoTo }: BaseElementsPanelProps) {
  const { t } = useTranslation('builder')
  const cqlContent = useSelector((state: RootState) => state.editor.cqlContent)
  const [pinnedNames, setPinnedNames] = useState<Set<string>>(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      return stored ? new Set(JSON.parse(stored)) : new Set()
    } catch {
      return new Set()
    }
  })

  // Persist pinned names
  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([...pinnedNames]))
  }, [pinnedNames])

  const refCounts = useMemo(
    () => countReferences(structure, cqlContent),
    [structure, cqlContent],
  )

  const togglePin = useCallback((name: string) => {
    setPinnedNames((prev) => {
      const next = new Set(prev)
      if (next.has(name)) {
        next.delete(name)
      } else {
        next.add(name)
      }
      return next
    })
  }, [])

  // Split into pinned (base elements) and others
  const pinned = useMemo(
    () => structure.expressions.filter((e) => pinnedNames.has(e.name)),
    [structure.expressions, pinnedNames],
  )

  const suggestions = useMemo(
    () => structure.expressions
      .filter((e) => !pinnedNames.has(e.name) && (refCounts.get(e.name) || 0) >= 2)
      .sort((a, b) => (refCounts.get(b.name) || 0) - (refCounts.get(a.name) || 0)),
    [structure.expressions, pinnedNames, refCounts],
  )

  return (
    <Stack spacing={0.5}>
      {pinned.length > 0 ? (
        pinned.map((expr) => {
          const count = refCounts.get(expr.name) || 0
          return (
            <Stack
              key={expr.name}
              direction="row"
              spacing={0.5}
              alignItems="center"
              sx={{
                py: 0.5,
                px: 0.5,
                borderRadius: 0.5,
                bgcolor: 'rgba(13,115,119,0.04)',
                '&:hover': { bgcolor: 'rgba(13,115,119,0.08)' },
              }}
            >
              <Tooltip title={t('baseElements.unmarkBase')}>
                <IconButton size="small" onClick={() => togglePin(expr.name)} sx={{ p: 0.25 }}>
                  <PinIcon sx={{ fontSize: 16, color: 'primary.main' }} />
                </IconButton>
              </Tooltip>
              <Typography
                variant="body2"
                sx={{
                  fontFamily: 'monospace',
                  fontSize: '0.75rem',
                  flex: 1,
                  cursor: 'pointer',
                  '&:hover': { color: 'primary.main', textDecoration: 'underline' },
                }}
                onClick={() => onGoTo?.(expr.name)}
              >
                {expr.name}
              </Typography>
              <TypeChip resultType={expr.resultType} />
              <Chip
                label={count > 0 ? t('baseElements.usedBy', { count }) : t('baseElements.notUsed')}
                size="small"
                sx={{
                  height: 18,
                  fontSize: '0.6rem',
                  bgcolor: count > 0 ? 'success.50' : 'warning.50',
                  color: count > 0 ? 'success.dark' : 'warning.dark',
                }}
              />
            </Stack>
          )
        })
      ) : (
        <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic', px: 0.5 }}>
          {t('baseElements.noBaseElements')}
        </Typography>
      )}

      {/* Suggestions: frequently referenced definitions */}
      {suggestions.length > 0 && (
        <>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ pt: 0.5 }}>
            {t('baseElements.title')} — {t('validation.hints')}
          </Typography>
          {suggestions.slice(0, 5).map((expr) => {
            const count = refCounts.get(expr.name) || 0
            return (
              <Stack
                key={expr.name}
                direction="row"
                spacing={0.5}
                alignItems="center"
                sx={{
                  py: 0.25,
                  px: 0.5,
                  borderRadius: 0.5,
                  '&:hover': { bgcolor: 'rgba(0,0,0,0.03)' },
                }}
              >
                <Tooltip title={t('baseElements.markAsBase')}>
                  <IconButton size="small" onClick={() => togglePin(expr.name)} sx={{ p: 0.25 }}>
                    <PinOutlinedIcon sx={{ fontSize: 16, color: 'text.disabled' }} />
                  </IconButton>
                </Tooltip>
                <Typography
                  variant="body2"
                  sx={{
                    fontFamily: 'monospace',
                    fontSize: '0.7rem',
                    flex: 1,
                    color: 'text.secondary',
                  }}
                >
                  {expr.name}
                </Typography>
                <Chip
                  label={`×${count}`}
                  size="small"
                  sx={{ height: 16, fontSize: '0.55rem', bgcolor: 'rgba(13,115,119,0.08)' }}
                />
              </Stack>
            )
          })}
        </>
      )}
    </Stack>
  )
}
