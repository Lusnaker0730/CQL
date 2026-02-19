import { useState, memo } from 'react'
import {
  Card, CardContent, Typography, Stack, IconButton, Tooltip, Collapse, Box, Chip,
} from '@mui/material'
import {
  ExpandMore as ExpandIcon, ExpandLess as CollapseIcon,
  Delete as DeleteIcon, ContentCopy as CopyIcon,
  FormatListBulleted as ListIcon, Visibility as ViewIcon,
  Build as ModIcon, Warning as WarningIcon,
  FormatIndentIncrease as IndentIcon, FormatIndentDecrease as OutdentIcon,
} from '@mui/icons-material'
import ArtifactElementBody from './ArtifactElementBody'
import ExpressionPhrase from './ExpressionPhrase'
import type { ElementInstance, ModifierDefinition } from '../../../types/authoring'
import { RETURN_TYPE_COLORS, RETURN_TYPE_COLOR_DEFAULT, ELEMENT_REF_BACKGROUNDS } from '../../../constants/authoringConstants'

const ELEMENT_ICONS: Record<string, typeof ListIcon> = {
  AgeRange: ViewIcon,
  Gender: ViewIcon,
}

interface ArtifactElementProps {
  element: ElementInstance
  modifiers: ModifierDefinition[]
  onUpdate: (updates: Partial<ElementInstance>) => void
  onRemove: () => void
  onIndent?: () => void
  onOutdent?: () => void
}

const ArtifactElement = memo(function ArtifactElement({
  element,
  modifiers,
  onUpdate,
  onRemove,
  onIndent,
  onOutdent,
}: ArtifactElementProps) {
  const [expanded, setExpanded] = useState(true)

  const elementName = element.fields?.find((f) => f.id === 'element_name')?.value as string
  const displayName = elementName || element.name
  const effectiveReturnType = getEffectiveReturnType(element)
  const chainError = getModifierChainError(element)
  const rtColor = RETURN_TYPE_COLORS[effectiveReturnType] || RETURN_TYPE_COLOR_DEFAULT
  const IconComp = ELEMENT_ICONS[element.type] || (element.type?.startsWith('Generic') ? ListIcon : ListIcon)

  const handleCopy = (e: React.MouseEvent) => {
    e.stopPropagation()
    const text = `${displayName} (${effectiveReturnType.replace(/_/g, ' ')})`
    navigator.clipboard.writeText(text)
  }

  return (
    <Card
      variant="outlined"
      sx={{
        borderLeft: '4px solid',
        borderLeftColor: rtColor,
        '&:hover': { boxShadow: 1 },
        transition: 'box-shadow 0.2s',
        ...(ELEMENT_REF_BACKGROUNDS[element.type] && { backgroundColor: ELEMENT_REF_BACKGROUNDS[element.type] }),
      }}
    >
      <Stack
        direction="row"
        alignItems="center"
        spacing={1}
        sx={{ px: 2, py: 1, cursor: 'pointer' }}
        onClick={() => setExpanded(!expanded)}
      >
        <IconButton size="small" aria-label={expanded ? 'Collapse element' : 'Expand element'}>
          {expanded ? <CollapseIcon fontSize="small" /> : <ExpandIcon fontSize="small" />}
        </IconButton>

        <IconComp fontSize="small" sx={{ color: rtColor, opacity: 0.7 }} />

        <Typography variant="body2" fontWeight={600} sx={{ flex: 1 }}>
          {displayName}
        </Typography>

        <Chip
          label={effectiveReturnType.replace(/_/g, ' ')}
          size="small"
          sx={{
            fontSize: '0.7rem',
            backgroundColor: rtColor + '18',
            color: rtColor,
            border: `1px solid ${rtColor}40`,
            fontWeight: 600,
          }}
        />

        {element.modifiers && element.modifiers.length > 0 && (
          <Chip
            icon={<ModIcon sx={{ fontSize: '0.85rem !important' }} />}
            label={`${element.modifiers.length}`}
            size="small"
            color="secondary"
            sx={{ fontSize: '0.7rem' }}
          />
        )}

        {chainError && (
          <Tooltip title={chainError}>
            <Chip
              icon={<WarningIcon sx={{ fontSize: '0.85rem !important' }} />}
              label="Chain Error"
              size="small"
              color="warning"
              sx={{ fontSize: '0.7rem' }}
            />
          </Tooltip>
        )}

        {onIndent && (
          <Tooltip title="Indent into new group">
            <IconButton size="small" onClick={(e) => { e.stopPropagation(); onIndent() }} sx={{ opacity: 0.5, '&:hover': { opacity: 1 } }} aria-label="Indent into new group">
              <IndentIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}

        {onOutdent && (
          <Tooltip title="Outdent to parent group">
            <IconButton size="small" onClick={(e) => { e.stopPropagation(); onOutdent() }} sx={{ opacity: 0.5, '&:hover': { opacity: 1 } }} aria-label="Outdent to parent group">
              <OutdentIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}

        <Tooltip title="Copy element info">
          <IconButton size="small" onClick={handleCopy} sx={{ opacity: 0.5, '&:hover': { opacity: 1 } }} aria-label="Copy element info">
            <CopyIcon fontSize="small" />
          </IconButton>
        </Tooltip>

        <Tooltip title="Remove element">
          <IconButton
            size="small"
            color="error"
            onClick={(e) => { e.stopPropagation(); onRemove() }}
            aria-label="Remove element"
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Stack>

      {!expanded && (
        <Box sx={{ px: 2, pb: 1 }}>
          <ExpressionPhrase element={element} />
        </Box>
      )}

      <Collapse in={expanded}>
        <CardContent sx={{ pt: 0 }}>
          <ArtifactElementBody
            element={element}
            modifiers={modifiers}
            onUpdate={onUpdate}
          />
        </CardContent>
      </Collapse>
    </Card>
  )
})

export default ArtifactElement

function getEffectiveReturnType(element: ElementInstance): string {
  if (element.modifiers && element.modifiers.length > 0) {
    return element.modifiers[element.modifiers.length - 1].returnType
  }
  return element.returnType
}

function getModifierChainError(element: ElementInstance): string | null {
  if (!element.modifiers || element.modifiers.length === 0) return null
  let currentType = element.returnType
  for (let i = 0; i < element.modifiers.length; i++) {
    const mod = element.modifiers[i]
    if (!mod.inputTypes.includes(currentType)) {
      return `Modifier "${mod.name}" expects ${mod.inputTypes.join('/')} but receives ${currentType.replace(/_/g, ' ')}`
    }
    currentType = mod.returnType
  }
  return null
}
