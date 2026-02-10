import { useState } from 'react'
import {
  Card,
  CardContent,
  Typography,
  Stack,
  IconButton,
  Tooltip,
  Collapse,
  Box,
  Chip,
} from '@mui/material'
import {
  ExpandMore as ExpandIcon,
  ExpandLess as CollapseIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material'
import ArtifactElementBody from './ArtifactElementBody'
import ExpressionPhrase from './ExpressionPhrase'
import type { ElementInstance, ModifierDefinition } from '../../../types/authoring'

interface ArtifactElementProps {
  element: ElementInstance
  modifiers: ModifierDefinition[]
  onUpdate: (updates: Partial<ElementInstance>) => void
  onRemove: () => void
}

export default function ArtifactElement({
  element,
  modifiers,
  onUpdate,
  onRemove,
}: ArtifactElementProps) {
  const [expanded, setExpanded] = useState(true)

  const elementName = element.fields?.find((f) => f.id === 'element_name')?.value as string
  const displayName = elementName || element.name

  return (
    <Card
      variant="outlined"
      sx={{
        borderLeft: '4px solid',
        borderLeftColor: 'primary.main',
        '&:hover': { boxShadow: 1 },
      }}
    >
      <Stack
        direction="row"
        alignItems="center"
        spacing={1}
        sx={{ px: 2, py: 1, cursor: 'pointer' }}
        onClick={() => setExpanded(!expanded)}
      >
        <IconButton size="small">
          {expanded ? <CollapseIcon fontSize="small" /> : <ExpandIcon fontSize="small" />}
        </IconButton>

        <Typography variant="body2" fontWeight={600} sx={{ flex: 1 }}>
          {displayName}
        </Typography>

        <Chip
          label={element.returnType.replace(/_/g, ' ')}
          size="small"
          variant="outlined"
          sx={{ fontSize: '0.7rem' }}
        />

        {element.modifiers && element.modifiers.length > 0 && (
          <Chip
            label={`${element.modifiers.length} modifier${element.modifiers.length > 1 ? 's' : ''}`}
            size="small"
            color="secondary"
            sx={{ fontSize: '0.7rem' }}
          />
        )}

        <Tooltip title="Remove element">
          <IconButton
            size="small"
            color="error"
            onClick={(e) => {
              e.stopPropagation()
              onRemove()
            }}
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
}
