import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useSelector } from 'react-redux'
import {
  Box,
  Typography,
  Stack,
  Chip,
} from '@mui/material'
import {
  AccountTree as TreeIcon,
  ArrowForward as ArrowIcon,
} from '@mui/icons-material'
import TypeChip from './TypeChip'
import type { CqlStructure } from '../../hooks/useCqlStructure'
import type { RootState } from '../../store'
import { parseCqlDefineBlocks, findQuotedReferences } from '../../utils/cqlNames'

interface ExpressionTreeViewProps {
  structure: CqlStructure
  onGoTo?: (name: string) => void
}

interface DepNode {
  name: string
  resultType?: string
  references: string[] // definitions this node references
  referencedBy: string[] // definitions that reference this node
}

/** Build a dependency graph from CQL content by scanning for quoted references */
function buildDependencyGraph(structure: CqlStructure, cqlContent: string): Map<string, DepNode> {
  const nodes = new Map<string, DepNode>()
  const allNames = new Set<string>()

  for (const expr of structure.expressions) {
    allNames.add(expr.name)
    nodes.set(expr.name, { name: expr.name, resultType: expr.resultType, references: [], referencedBy: [] })
  }
  for (const func of structure.functions) {
    allNames.add(func.name)
    nodes.set(func.name, { name: func.name, resultType: func.resultType, references: [], referencedBy: [] })
  }

  for (const block of parseCqlDefineBlocks(cqlContent)) {
    const node = nodes.get(block.name)
    if (!node) continue

    const refs = findQuotedReferences(block.body, allNames, block.name)
    node.references = refs
    for (const refName of refs) {
      const target = nodes.get(refName)
      if (target && !target.referencedBy.includes(block.name)) {
        target.referencedBy.push(block.name)
      }
    }
  }

  return nodes
}

/** Topological sort to find root nodes (no dependencies) */
function findRoots(nodes: Map<string, DepNode>): string[] {
  return Array.from(nodes.values())
    .filter((n) => n.referencedBy.length === 0)
    .map((n) => n.name)
}

export default function ExpressionTreeView({ structure, onGoTo }: ExpressionTreeViewProps) {
  const { t } = useTranslation('builder')
  const cqlContent = useSelector((state: RootState) => state.editor.cqlContent)

  const nodes = useMemo(
    () => buildDependencyGraph(structure, cqlContent),
    [structure, cqlContent],
  )
  const roots = useMemo(() => findRoots(nodes), [nodes])

  if (structure.expressions.length === 0 && structure.functions.length === 0) {
    return (
      <Box sx={{ p: 1.5, textAlign: 'center' }}>
        <TreeIcon sx={{ fontSize: 24, color: 'text.disabled', mb: 0.5 }} />
        <Typography
          variant="caption"
          sx={{
            display: "block",
            color: "text.secondary"
          }}>
          {t('expressionTree.noData')}
        </Typography>
      </Box>
    );
  }

  return (
    <Stack spacing={0.5}>
      {/* Render root nodes first, then others */}
      {roots.map((rootName) => (
        <TreeNodeView
          key={rootName}
          name={rootName}
          nodes={nodes}
          depth={0}
          visited={new Set()}
          onGoTo={onGoTo}
        />
      ))}

      {/* Show orphan nodes that are not roots but not referenced by roots */}
      {Array.from(nodes.values())
        .filter((n) => !roots.includes(n.name) && n.referencedBy.every((ref) => !roots.includes(ref)))
        .filter((n) => n.references.length === 0 && n.referencedBy.length === 0)
        .map((n) => (
          <TreeNodeView
            key={n.name}
            name={n.name}
            nodes={nodes}
            depth={0}
            visited={new Set()}
            onGoTo={onGoTo}
          />
        ))
      }
    </Stack>
  )
}

function TreeNodeView({
  name,
  nodes,
  depth,
  visited,
  onGoTo,
}: {
  name: string
  nodes: Map<string, DepNode>
  depth: number
  visited: Set<string>
  onGoTo?: (name: string) => void
}) {
  const node = nodes.get(name)
  if (!node || visited.has(name)) return null

  const newVisited = new Set(visited)
  newVisited.add(name)

  const hasRefs = node.references.length > 0
  const refCount = node.referencedBy.length

  return (
    <Box sx={{ pl: depth * 2 }}>
      <Stack
        direction="row"
        spacing={0.5}
        onClick={() => onGoTo?.(name)}
        sx={{
          alignItems: "center",
          py: 0.25,
          px: 0.5,
          borderRadius: 0.5,
          cursor: onGoTo ? 'pointer' : 'default',
          '&:hover': { bgcolor: 'rgba(13,115,119,0.06)' }
        }}>
        {depth > 0 && (
          <ArrowIcon sx={{ fontSize: 12, color: 'text.disabled', transform: 'rotate(0deg)' }} />
        )}
        <Typography
          variant="body2"
          sx={{
            fontFamily: 'monospace',
            fontSize: '0.75rem',
            fontWeight: refCount === 0 && depth === 0 ? 600 : 400,
            color: refCount === 0 && depth === 0 ? 'primary.main' : 'text.primary',
          }}
        >
          {name}
        </Typography>
        <TypeChip resultType={node.resultType} />
        {refCount > 0 && (
          <Chip
            label={`×${refCount}`}
            size="small"
            sx={{
              height: 16,
              fontSize: '0.55rem',
              bgcolor: 'rgba(13,115,119,0.08)',
              color: 'primary.dark',
            }}
          />
        )}
      </Stack>
      {/* Render children (definitions this node depends on) */}
      {hasRefs && depth < 4 && node.references.map((ref) => (
        <TreeNodeView
          key={ref}
          name={ref}
          nodes={nodes}
          depth={depth + 1}
          visited={newVisited}
          onGoTo={onGoTo}
        />
      ))}
    </Box>
  );
}
