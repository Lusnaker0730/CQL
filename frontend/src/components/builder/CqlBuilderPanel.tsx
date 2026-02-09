import { useState } from 'react'
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
import IncludesSection from './IncludesSection'
import ValueSetSection from './ValueSetSection'
import CodesSection from './CodesSection'
import ParametersSection from './ParametersSection'
import DefinitionsSection from './DefinitionsSection'
import FunctionsSection from './FunctionsSection'

interface CqlBuilderPanelProps {
  onInsertSnippet: (snippet: string) => void
}

export default function CqlBuilderPanel({ onInsertSnippet }: CqlBuilderPanelProps) {
  const { structure, isParsing, parseError, parse } = useCqlStructure()
  const [expanded, setExpanded] = useState<string | false>('includes')

  const handleAccordion = (panel: string) => (_: unknown, isExpanded: boolean) => {
    setExpanded(isExpanded ? panel : false)
  }

  const sections = [
    {
      id: 'includes',
      label: 'Includes',
      count: structure.includes.length,
      content: <IncludesSection includes={structure.includes} onInsert={onInsertSnippet} />,
    },
    {
      id: 'valueSets',
      label: 'Value Sets',
      count: structure.valueSets.length,
      content: <ValueSetSection valueSets={structure.valueSets} onInsert={onInsertSnippet} />,
    },
    {
      id: 'codes',
      label: 'Codes',
      count: structure.codes.length,
      content: <CodesSection codes={structure.codes} onInsert={onInsertSnippet} />,
    },
    {
      id: 'parameters',
      label: 'Parameters',
      count: structure.parameters.length,
      content: <ParametersSection parameters={structure.parameters} onInsert={onInsertSnippet} />,
    },
    {
      id: 'definitions',
      label: 'Definitions',
      count: structure.expressions.length,
      content: <DefinitionsSection expressions={structure.expressions} onInsert={onInsertSnippet} />,
    },
    {
      id: 'functions',
      label: 'Functions',
      count: structure.functions.length,
      content: <FunctionsSection functions={structure.functions} onInsert={onInsertSnippet} />,
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
