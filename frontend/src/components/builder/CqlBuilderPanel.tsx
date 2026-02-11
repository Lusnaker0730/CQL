import { useState, useEffect } from 'react'
import {
  Box,
  Stack,
  Typography,
  Button,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Chip,
  CircularProgress,
  Alert,
} from '@mui/material'
import {
  ExpandMore as ExpandMoreIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material'
import { useCqlStructure } from '../../hooks/useCqlStructure'
import { updateCqlStructure } from '../../utils/cqlSyntax'
import IncludesSection from './IncludesSection'
import ValueSetSection from './ValueSetSection'
import CodesSection from './CodesSection'
import ConceptsSection from './ConceptsSection'
import ParametersSection from './ParametersSection'
import DefinitionsSection from './DefinitionsSection'
import FunctionsSection from './FunctionsSection'

import type { CqlElementType } from '../../utils/cqlElementLocator'

interface CqlBuilderPanelProps {
  onInsertSnippet: (snippet: string) => void
  onDeleteElement?: (type: CqlElementType, identifier: string) => void
  onGoToElement?: (type: CqlElementType, identifier: string) => void
  onEditElement?: (type: CqlElementType, identifier: string, newSnippet: string) => void
}

export default function CqlBuilderPanel({
  onInsertSnippet,
  onDeleteElement,
  onGoToElement,
  onEditElement,
}: CqlBuilderPanelProps) {
  const { structure, isParsing, parseError, parse } = useCqlStructure()
  const [expanded, setExpanded] = useState<string | false>('includes')

  // Keep Monaco IntelliSense in sync with CQL structure
  useEffect(() => { updateCqlStructure(structure) }, [structure])

  const handleAccordion = (panel: string) => (_: unknown, isExpanded: boolean) => {
    setExpanded(isExpanded ? panel : false)
  }

  const handleAutoIncludeC3F = (snippet: string) => {
    if (snippet.includes('C3F.') && !structure.includes.some((inc) => inc.includes('called C3F'))) {
      const c3fInclude = `include CDS_Connect_Commons_for_FHIRv401 version '1.1.1' called C3F`
      onInsertSnippet(c3fInclude)
      // Small delay to avoid race with content update
      setTimeout(() => onInsertSnippet(snippet), 50)
    } else {
      onInsertSnippet(snippet)
    }
  }

  const sections = [
    {
      id: 'includes',
      label: 'Includes',
      count: structure.includes.length,
      content: (
        <IncludesSection
          includes={structure.includes}
          onInsert={onInsertSnippet}
          onDelete={(id) => onDeleteElement?.('include', id)}
          onGoTo={(id) => onGoToElement?.('include', id)}
          onEdit={(id, snippet) => onEditElement?.('include', id, snippet)}
        />
      ),
    },
    {
      id: 'valueSets',
      label: 'Value Sets',
      count: structure.valueSets.length,
      content: (
        <ValueSetSection
          valueSets={structure.valueSets}
          onInsert={onInsertSnippet}
          onDelete={(id) => onDeleteElement?.('valueset', id)}
          onGoTo={(id) => onGoToElement?.('valueset', id)}
          onEdit={(id, snippet) => onEditElement?.('valueset', id, snippet)}
        />
      ),
    },
    {
      id: 'codes',
      label: 'Codes',
      count: structure.codes.length,
      content: (
        <CodesSection
          codes={structure.codes}
          onInsert={onInsertSnippet}
          onDelete={(id) => onDeleteElement?.('code', id)}
          onGoTo={(id) => onGoToElement?.('code', id)}
          onEdit={(id, snippet) => onEditElement?.('code', id, snippet)}
        />
      ),
    },
    {
      id: 'concepts',
      label: 'Concepts',
      count: structure.concepts.length,
      content: (
        <ConceptsSection
          concepts={structure.concepts}
          codes={structure.codes}
          onInsert={onInsertSnippet}
          onDelete={(id) => onDeleteElement?.('concept', id)}
          onGoTo={(id) => onGoToElement?.('concept', id)}
          onEdit={(id, snippet) => onEditElement?.('concept', id, snippet)}
        />
      ),
    },
    {
      id: 'parameters',
      label: 'Parameters',
      count: structure.parameters.length,
      content: (
        <ParametersSection
          parameters={structure.parameters}
          onInsert={onInsertSnippet}
          onDelete={(id) => onDeleteElement?.('parameter', id)}
          onGoTo={(id) => onGoToElement?.('parameter', id)}
          onEdit={(id, snippet) => onEditElement?.('parameter', id, snippet)}
        />
      ),
    },
    {
      id: 'definitions',
      label: 'Definitions',
      count: structure.expressions.length,
      content: (
        <DefinitionsSection
          expressions={structure.expressions}
          onInsert={handleAutoIncludeC3F}
          valueSets={structure.valueSets}
          codes={structure.codes}
          parameters={structure.parameters}
          onDelete={(id) => onDeleteElement?.('define', id)}
          onGoTo={(id) => onGoToElement?.('define', id)}
          onEdit={(id, snippet) => onEditElement?.('define', id, snippet)}
        />
      ),
    },
    {
      id: 'functions',
      label: 'Functions',
      count: structure.functions.length,
      content: (
        <FunctionsSection
          functions={structure.functions}
          onInsert={onInsertSnippet}
          onDelete={(id) => onDeleteElement?.('function', id)}
          onGoTo={(id) => onGoToElement?.('function', id)}
          onEdit={(id, snippet) => onEditElement?.('function', id, snippet)}
        />
      ),
    },
  ]

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box
        sx={{
          p: 1,
          px: 1.5,
          background: 'linear-gradient(135deg, rgba(13,115,119,0.06) 0%, rgba(20,163,168,0.03) 100%)',
          borderBottom: '1px solid',
          borderColor: 'rgba(13,115,119,0.1)',
        }}
      >
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="subtitle2" fontWeight={600} color="secondary.main">
            CQL Builder
          </Typography>
          <Button
            size="small"
            startIcon={isParsing ? <CircularProgress size={14} color="inherit" /> : <RefreshIcon />}
            onClick={parse}
            disabled={isParsing}
            sx={{ fontSize: '0.75rem' }}
          >
            {isParsing ? 'Parsing...' : 'Parse CQL'}
          </Button>
        </Stack>
        {structure.libraryId && (
          <Typography variant="caption" color="text.secondary">
            {structure.libraryId} v{structure.libraryVersion}
          </Typography>
        )}
      </Box>

      {parseError && (
        <Alert severity="warning" sx={{ mx: 1, mt: 1, py: 0 }}>
          {parseError}
        </Alert>
      )}

      <Box sx={{ flex: 1, overflow: 'auto', p: 0.5 }}>
        {sections.map((section) => (
          <Accordion
            key={section.id}
            expanded={expanded === section.id}
            onChange={handleAccordion(section.id)}
            disableGutters
            elevation={0}
            sx={{
              '&:before': { display: 'none' },
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: '4px !important',
              mb: 0.5,
              '&.Mui-expanded': { mb: 0.5 },
            }}
          >
            <AccordionSummary
              expandIcon={<ExpandMoreIcon />}
              sx={{
                minHeight: 36,
                '& .MuiAccordionSummary-content': { my: 0.5 },
              }}
            >
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="body2" fontWeight={500}>
                  {section.label}
                </Typography>
                <Chip
                  label={section.count}
                  size="small"
                  sx={{
                    height: 20,
                    fontSize: '0.7rem',
                    bgcolor: section.count > 0 ? 'rgba(13,115,119,0.1)' : 'transparent',
                    color: section.count > 0 ? 'primary.dark' : 'text.disabled',
                  }}
                />
              </Stack>
            </AccordionSummary>
            <AccordionDetails sx={{ pt: 0, pb: 1 }}>
              {section.content}
            </AccordionDetails>
          </Accordion>
        ))}
      </Box>
    </Box>
  )
}
