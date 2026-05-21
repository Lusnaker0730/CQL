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
import { useTranslation } from 'react-i18next'
import ArtifactElementBody from './ArtifactElementBody'
import ExpressionPhrase from './ExpressionPhrase'
import type { ElementInstance, ModifierDefinition } from '../../../types/authoring'
import { getEffectiveReturnType } from '../../../utils/modifierUtils'
import { RETURN_TYPE_COLORS, RETURN_TYPE_COLOR_DEFAULT, ELEMENT_REF_BACKGROUNDS } from '../../../constants/authoringConstants'

const ELEMENT_ICONS: Record<string, typeof ListIcon> = {
  AgeRange: ViewIcon,
  Gender: ViewIcon,
}

// Callbacks accept the element / uniqueId so the parent can pass a single
// stable callback (typically wrapped in useCallback) instead of a fresh
// per-render arrow per child. Without this, React.memo above is defeated
// and every keystroke in the tree re-renders every element.
interface ArtifactElementProps {
  element: ElementInstance
  modifiers: ModifierDefinition[]
  hideElementName?: boolean
  onUpdate: (uniqueId: string, updates: Partial<ElementInstance>) => void
  onRemove: (uniqueId: string) => void
  onIndent?: (uniqueId: string) => void
  onOutdent?: (element: ElementInstance) => void
}

const ArtifactElement = memo(function ArtifactElement({
  element,
  modifiers,
  hideElementName,
  onUpdate,
  onRemove,
  onIndent,
  onOutdent,
}: ArtifactElementProps) {
  const { t } = useTranslation('authoring')
  const [expanded, setExpanded] = useState(true)

  const elementName = element.fields?.find((f) => f.id === 'element_name')?.value as string
  const displayName = elementName || element.name
  const effectiveReturnType = getElementEffectiveReturnType(element)
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
        <IconButton size="small" aria-label={expanded ? t('element.collapse') : t('element.expand')}>
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
          <Tooltip title={t('element.modifierChainError', chainError)}>
            <Chip
              icon={<WarningIcon sx={{ fontSize: '0.85rem !important' }} />}
              label={t('element.chainError')}
              size="small"
              color="warning"
              sx={{ fontSize: '0.7rem' }}
            />
          </Tooltip>
        )}

        {onIndent && (
          <Tooltip title={t('element.indent')}>
            <IconButton size="small" onClick={(e) => { e.stopPropagation(); onIndent(element.uniqueId) }} sx={{ opacity: 0.7, '&:hover': { opacity: 1 } }} aria-label={t('element.indent')}>
              <IndentIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}

        {onOutdent && (
          <Tooltip title={t('element.outdent')}>
            <IconButton size="small" onClick={(e) => { e.stopPropagation(); onOutdent(element) }} sx={{ opacity: 0.7, '&:hover': { opacity: 1 } }} aria-label={t('element.outdent')}>
              <OutdentIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}

        <Tooltip title={t('element.copyInfo')}>
          <IconButton size="small" onClick={handleCopy} sx={{ opacity: 0.5, '&:hover': { opacity: 1 } }} aria-label={t('element.copyInfo')}>
            <CopyIcon fontSize="small" />
          </IconButton>
        </Tooltip>

        <Tooltip title={t('element.remove')}>
          <IconButton
            size="small"
            color="error"
            onClick={(e) => { e.stopPropagation(); onRemove(element.uniqueId) }}
            aria-label={t('element.remove')}
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
            hideElementName={hideElementName}
            onUpdate={(updates) => onUpdate(element.uniqueId, updates)}
          />
        </CardContent>
      </Collapse>
    </Card>
  )
})

export default ArtifactElement

function getElementEffectiveReturnType(element: ElementInstance): string {
  return getEffectiveReturnType(element.returnType, element.modifiers)
}

function getModifierChainError(element: ElementInstance): { name: string; expected: string; actual: string } | null {
  if (!element.modifiers || element.modifiers.length === 0) return null
  let currentType = element.returnType
  for (let i = 0; i < element.modifiers.length; i++) {
    const mod = element.modifiers[i]
    if (!mod.inputTypes.includes(currentType)) {
      return { name: mod.name, expected: mod.inputTypes.join('/'), actual: currentType.replace(/_/g, ' ') }
    }
    currentType = mod.returnType
  }
  return null
}
