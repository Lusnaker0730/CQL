import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  Typography,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Box,
  IconButton,
  Tooltip,
  InputAdornment,
} from '@mui/material'
import {
  ExpandMore as ExpandMoreIcon,
  ContentCopy as CopyIcon,
  Search as SearchIcon,
} from '@mui/icons-material'
import { useNotification } from '../../hooks/useNotification'

interface OperatorEntry {
  name: string
  category: string
  syntax: string
  description: string
  snippet: string
  example?: string
}

const OPERATORS: OperatorEntry[] = [
  // Comparison
  { name: '=', category: 'Comparison', syntax: 'expr1 = expr2', description: 'Equality comparison', snippet: ' = ', example: 'A = B' },
  { name: '!=', category: 'Comparison', syntax: 'expr1 != expr2', description: 'Inequality comparison', snippet: ' != ', example: 'A != B' },
  { name: '~', category: 'Comparison', syntax: 'expr1 ~ expr2', description: 'Equivalent (null-safe equality)', snippet: ' ~ ', example: 'C.clinicalStatus ~ "active"' },
  { name: '!~', category: 'Comparison', syntax: 'expr1 !~ expr2', description: 'Not equivalent', snippet: ' !~ ' },
  { name: '>', category: 'Comparison', syntax: 'expr1 > expr2', description: 'Greater than', snippet: ' > ' },
  { name: '<', category: 'Comparison', syntax: 'expr1 < expr2', description: 'Less than', snippet: ' < ' },
  { name: '>=', category: 'Comparison', syntax: 'expr1 >= expr2', description: 'Greater than or equal', snippet: ' >= ' },
  { name: '<=', category: 'Comparison', syntax: 'expr1 <= expr2', description: 'Less than or equal', snippet: ' <= ' },

  // Logical
  { name: 'and', category: 'Logical', syntax: 'expr1 and expr2', description: 'Logical conjunction', snippet: ' and ', example: '"Has Diabetes" and "Is Adult"' },
  { name: 'or', category: 'Logical', syntax: 'expr1 or expr2', description: 'Logical disjunction', snippet: ' or ', example: '"Has Diabetes" or "Has Prediabetes"' },
  { name: 'not', category: 'Logical', syntax: 'not expr', description: 'Logical negation', snippet: 'not ', example: 'not "Has Exclusion"' },
  { name: 'xor', category: 'Logical', syntax: 'expr1 xor expr2', description: 'Exclusive or', snippet: ' xor ' },
  { name: 'implies', category: 'Logical', syntax: 'expr1 implies expr2', description: 'Logical implication', snippet: ' implies ' },

  // Temporal
  { name: 'before', category: 'Temporal', syntax: 'A before B', description: 'A occurs before B', snippet: ' before ', example: 'E.period before "Measurement Period"' },
  { name: 'after', category: 'Temporal', syntax: 'A after B', description: 'A occurs after B', snippet: ' after ' },
  { name: 'during', category: 'Temporal', syntax: 'A during B', description: 'A occurs entirely within B', snippet: ' during ', example: 'E.period during "Measurement Period"' },
  { name: 'overlaps', category: 'Temporal', syntax: 'A overlaps B', description: 'A and B share some time', snippet: ' overlaps ' },
  { name: 'meets', category: 'Temporal', syntax: 'A meets B', description: 'End of A equals start of B', snippet: ' meets ' },
  { name: 'starts', category: 'Temporal', syntax: 'A starts B', description: 'A starts at the same time as B', snippet: ' starts ' },
  { name: 'ends', category: 'Temporal', syntax: 'A ends B', description: 'A ends at the same time as B', snippet: ' ends ' },
  { name: 'within', category: 'Temporal', syntax: 'A within N days of B', description: 'A occurs within N time units of B', snippet: ' within ', example: 'E.period within 30 days of "Measurement Period"' },
  { name: 'same as', category: 'Temporal', syntax: 'A same day as B', description: 'Same point in time at given precision', snippet: ' same day as ' },

  // Arithmetic
  { name: '+', category: 'Arithmetic', syntax: 'a + b', description: 'Addition', snippet: ' + ' },
  { name: '-', category: 'Arithmetic', syntax: 'a - b', description: 'Subtraction', snippet: ' - ' },
  { name: '*', category: 'Arithmetic', syntax: 'a * b', description: 'Multiplication', snippet: ' * ' },
  { name: '/', category: 'Arithmetic', syntax: 'a / b', description: 'Division', snippet: ' / ' },
  { name: 'mod', category: 'Arithmetic', syntax: 'a mod b', description: 'Modulo (remainder)', snippet: ' mod ' },
  { name: 'div', category: 'Arithmetic', syntax: 'a div b', description: 'Integer division', snippet: ' div ' },

  // Type
  { name: 'is', category: 'Type', syntax: 'expr is Type', description: 'Type check', snippet: ' is ', example: 'O.value is Quantity' },
  { name: 'as', category: 'Type', syntax: 'expr as Type', description: 'Type cast', snippet: ' as ', example: 'O.value as Quantity' },
  { name: 'cast', category: 'Type', syntax: 'cast expr as Type', description: 'Explicit type cast', snippet: 'cast  as ' },
  { name: 'convert', category: 'Type', syntax: "convert expr to Type", description: 'Convert between types', snippet: 'convert  to ' },
  { name: 'is null', category: 'Type', syntax: 'expr is null', description: 'Null check', snippet: ' is null' },
  { name: 'is not null', category: 'Type', syntax: 'expr is not null', description: 'Not-null check', snippet: ' is not null' },

  // Aggregate
  { name: 'Count', category: 'Aggregate', syntax: 'Count(list)', description: 'Count elements in a list', snippet: 'Count()', example: 'Count("Qualifying Encounters")' },
  { name: 'Sum', category: 'Aggregate', syntax: 'Sum(list)', description: 'Sum of numeric values', snippet: 'Sum()' },
  { name: 'Avg', category: 'Aggregate', syntax: 'Avg(list)', description: 'Average of numeric values', snippet: 'Avg()' },
  { name: 'Min', category: 'Aggregate', syntax: 'Min(list)', description: 'Minimum value', snippet: 'Min()' },
  { name: 'Max', category: 'Aggregate', syntax: 'Max(list)', description: 'Maximum value', snippet: 'Max()' },
  { name: 'First', category: 'Aggregate', syntax: 'First(list)', description: 'First element of an ordered list', snippet: 'First()' },
  { name: 'Last', category: 'Aggregate', syntax: 'Last(list)', description: 'Last element of an ordered list', snippet: 'Last()' },
  { name: 'Flatten', category: 'Aggregate', syntax: 'Flatten(list)', description: 'Flatten nested lists', snippet: 'Flatten()' },

  // Date/Age
  { name: 'AgeInYears', category: 'Date/Age', syntax: 'AgeInYears()', description: 'Patient age in years at today', snippet: 'AgeInYears()' },
  { name: 'AgeInYearsAt', category: 'Date/Age', syntax: 'AgeInYearsAt(date)', description: 'Patient age in years at a given date', snippet: 'AgeInYearsAt()', example: 'AgeInYearsAt(start of "Measurement Period")' },
  { name: 'AgeInMonths', category: 'Date/Age', syntax: 'AgeInMonths()', description: 'Patient age in months', snippet: 'AgeInMonths()' },
  { name: 'Today', category: 'Date/Age', syntax: 'Today()', description: 'Current date', snippet: 'Today()' },
  { name: 'Now', category: 'Date/Age', syntax: 'Now()', description: 'Current date and time', snippet: 'Now()' },
  { name: 'start of', category: 'Date/Age', syntax: 'start of interval', description: 'Start point of an interval', snippet: 'start of ', example: 'start of "Measurement Period"' },
  { name: 'end of', category: 'Date/Age', syntax: 'end of interval', description: 'End point of an interval', snippet: 'end of ' },
  { name: 'duration in', category: 'Date/Age', syntax: 'duration in days of interval', description: 'Duration of an interval in given units', snippet: 'duration in days of ' },

  // String
  { name: 'Length', category: 'String', syntax: 'Length(string)', description: 'Length of a string', snippet: 'Length()' },
  { name: 'Upper', category: 'String', syntax: 'Upper(string)', description: 'Convert to uppercase', snippet: 'Upper()' },
  { name: 'Lower', category: 'String', syntax: 'Lower(string)', description: 'Convert to lowercase', snippet: 'Lower()' },
  { name: '+ (concat)', category: 'String', syntax: "str1 + str2", description: 'String concatenation', snippet: " + ''" },

  // List
  { name: 'exists', category: 'List', syntax: 'exists(list)', description: 'True if list has any elements', snippet: 'exists ()', example: 'exists [Condition: "Diabetes"]' },
  { name: 'in', category: 'List', syntax: 'element in list', description: 'Membership test', snippet: ' in ', example: "O.status in { 'final', 'amended' }" },
  { name: 'contains', category: 'List', syntax: 'list contains element', description: 'List contains element', snippet: ' contains ' },
  { name: 'union', category: 'List', syntax: 'list1 union list2', description: 'Union of two lists', snippet: ' union ' },
  { name: 'intersect', category: 'List', syntax: 'list1 intersect list2', description: 'Intersection of two lists', snippet: ' intersect ' },
  { name: 'except', category: 'List', syntax: 'list1 except list2', description: 'Elements in list1 not in list2', snippet: ' except ' },
  { name: 'distinct', category: 'List', syntax: 'distinct list', description: 'Remove duplicates', snippet: 'distinct ' },
  { name: 'flatten', category: 'List', syntax: 'flatten list', description: 'Flatten nested lists', snippet: 'flatten ' },
  { name: 'singleton from', category: 'List', syntax: 'singleton from list', description: 'Extract single element from a list', snippet: 'singleton from ' },
]

const CATEGORIES = [
  'Comparison', 'Logical', 'Temporal', 'Arithmetic', 'Type',
  'Aggregate', 'Date/Age', 'String', 'List',
]

interface OperatorReferenceProps {
  onInsert: (snippet: string) => void
}

export default function OperatorReference({ onInsert }: OperatorReferenceProps) {
  const { t } = useTranslation('builder')
  const [search, setSearch] = useState('')
  const [expanded, setExpanded] = useState<string | false>(false)
  const { showNotification } = useNotification()

  const filteredByCategory = useMemo(() => {
    const term = search.toLowerCase()
    const map: Record<string, OperatorEntry[]> = {}
    for (const cat of CATEGORIES) map[cat] = []
    for (const op of OPERATORS) {
      if (term && !op.name.toLowerCase().includes(term) && !op.description.toLowerCase().includes(term)) continue
      map[op.category].push(op)
    }
    return map
  }, [search])

  const handleCopy = async (snippet: string) => {
    try {
      await navigator.clipboard.writeText(snippet.trim())
      showNotification('Copied to clipboard', 'success', 2000)
    } catch {
      showNotification('Failed to copy', 'error', 2000)
    }
  }

  return (
    <Stack spacing={0.5}>
      <TextField
        size="small"
        placeholder={t('operatorReference.searchPlaceholder')}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon sx={{ fontSize: 18, color: 'text.disabled' }} />
            </InputAdornment>
          ),
        }}
        sx={{ '& input': { fontSize: '0.85rem' } }}
      />

      {CATEGORIES.map((cat) => {
        const ops = filteredByCategory[cat]
        if (!ops || ops.length === 0) return null
        return (
          <Accordion
            key={cat}
            expanded={expanded === cat}
            onChange={(_, isExpanded) => setExpanded(isExpanded ? cat : false)}
            disableGutters
            elevation={0}
            sx={{
              '&:before': { display: 'none' },
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: '4px !important',
            }}
          >
            <AccordionSummary
              expandIcon={<ExpandMoreIcon sx={{ fontSize: 18 }} />}
              sx={{ minHeight: 32, '& .MuiAccordionSummary-content': { my: 0.25 } }}
            >
              <Typography variant="body2" fontWeight={500} fontSize="0.8rem">
                {cat}
                <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 0.5 }}>
                  ({ops.length})
                </Typography>
              </Typography>
            </AccordionSummary>
            <AccordionDetails sx={{ pt: 0, pb: 0.5, px: 0.5 }}>
              <Stack spacing={0}>
                {ops.map((op) => (
                  <Box
                    key={op.name}
                    sx={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      py: 0.5,
                      px: 0.5,
                      borderRadius: 0.5,
                      cursor: 'pointer',
                      '&:hover': { bgcolor: 'rgba(13,115,119,0.06)' },
                      '&:hover .op-copy': { opacity: 1 },
                    }}
                    onClick={() => onInsert(op.snippet)}
                  >
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Typography variant="body2" fontWeight={600} sx={{ fontFamily: 'monospace', fontSize: '0.78rem' }}>
                        {op.name}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.7rem', display: 'block' }}>
                        {op.syntax}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.68rem' }}>
                        {op.description}
                      </Typography>
                      {op.example && (
                        <Typography
                          variant="caption"
                          sx={{
                            display: 'block',
                            fontFamily: 'monospace',
                            fontSize: '0.65rem',
                            color: 'primary.dark',
                            mt: 0.25,
                          }}
                        >
                          {op.example}
                        </Typography>
                      )}
                    </Box>
                    <Tooltip title="Copy">
                      <IconButton
                        className="op-copy"
                        size="small"
                        onClick={(e) => { e.stopPropagation(); handleCopy(op.snippet) }}
                        sx={{ opacity: 0, transition: 'opacity 0.15s', p: 0.25, mt: 0.25 }}
                      >
                        <CopyIcon sx={{ fontSize: 14 }} />
                      </IconButton>
                    </Tooltip>
                  </Box>
                ))}
              </Stack>
            </AccordionDetails>
          </Accordion>
        )
      })}
    </Stack>
  )
}
