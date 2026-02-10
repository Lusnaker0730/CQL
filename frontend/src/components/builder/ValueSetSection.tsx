import { useState } from 'react'
import {
  Stack,
  TextField,
  Button,
  Typography,
  List,
  ListItem,
  ListItemText,
  CircularProgress,
  Collapse,
  Table,
  TableBody,
  TableRow,
  TableCell,
  IconButton,
} from '@mui/material'
import { Add as AddIcon, ExpandMore, ExpandLess } from '@mui/icons-material'
import { useSearchValueSets, useExpandValueSet } from '../../hooks/useTerminology'

interface ValueSetSectionProps {
  valueSets: string[]
  onInsert: (cqlSnippet: string) => void
}

export default function ValueSetSection({ valueSets, onInsert }: ValueSetSectionProps) {
  const [showForm, setShowForm] = useState(false)
  const [searchTerm, setSearchTerm] = useState('')
  const [expandedUrl, setExpandedUrl] = useState<string | null>(null)

  const { data: searchResults = [], isLoading: isSearching } = useSearchValueSets(
    searchTerm.length >= 2 ? searchTerm : undefined
  )
  const expandMutation = useExpandValueSet()

  const handleExpand = (url: string) => {
    if (expandedUrl === url) {
      setExpandedUrl(null)
      return
    }
    setExpandedUrl(url)
    expandMutation.mutate({ url })
  }

  const handleInsertValueSet = (name: string, url: string) => {
    const snippet = `valueset "${name}": '${url}'`
    onInsert(snippet)
    setShowForm(false)
    setSearchTerm('')
  }

  return (
    <Stack spacing={1}>
      {valueSets.length > 0 ? (
        <List dense disablePadding>
          {valueSets.map((vs, idx) => (
            <ListItem key={idx} disablePadding sx={{ py: 0.25 }}>
              <ListItemText
                primary={
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                    {vs}
                  </Typography>
                }
              />
            </ListItem>
          ))}
        </List>
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          No value sets found
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          Add ValueSet
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: 'rgba(13,115,119,0.03)', borderRadius: 1 }}>
          <TextField
            size="small"
            label="Search VSAC ValueSets"
            placeholder="e.g. Diabetes, HbA1c..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            InputProps={{
              endAdornment: isSearching ? <CircularProgress size={16} /> : null,
            }}
          />

          {searchResults.length > 0 && (
            <Stack spacing={0.5} sx={{ maxHeight: 250, overflow: 'auto' }}>
              {searchResults.slice(0, 20).map((vs) => (
                <Stack key={vs.url} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 0.75 }}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="body2" fontWeight={500} sx={{ fontSize: '0.8rem', flex: 1 }}>
                      {vs.title || vs.name}
                    </Typography>
                    <Stack direction="row" spacing={0.5}>
                      <IconButton size="small" onClick={() => handleExpand(vs.url)} aria-label="Toggle value set codes">
                        {expandedUrl === vs.url ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
                      </IconButton>
                      <Button
                        size="small"
                        variant="outlined"
                        onClick={() => handleInsertValueSet(vs.title || vs.name, vs.url)}
                        sx={{ minWidth: 0, px: 1, fontSize: '0.7rem' }}
                      >
                        Insert
                      </Button>
                    </Stack>
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    URL: {vs.url}
                  </Typography>

                  <Collapse in={expandedUrl === vs.url}>
                    {expandMutation.isPending && expandedUrl === vs.url ? (
                      <CircularProgress size={16} sx={{ m: 1 }} />
                    ) : expandMutation.data?.expansion?.contains ? (
                      <Table size="small" sx={{ mt: 0.5 }}>
                        <TableBody>
                          {expandMutation.data.expansion.contains.slice(0, 10).map((code, i) => (
                            <TableRow key={i}>
                              <TableCell sx={{ py: 0.25, fontSize: '0.75rem' }}>{code.code}</TableCell>
                              <TableCell sx={{ py: 0.25, fontSize: '0.75rem' }}>{code.display}</TableCell>
                            </TableRow>
                          ))}
                          {expandMutation.data.expansion.contains.length > 10 && (
                            <TableRow>
                              <TableCell colSpan={2} sx={{ py: 0.25, fontSize: '0.75rem', fontStyle: 'italic' }}>
                                ...and {expandMutation.data.expansion.contains.length - 10} more
                              </TableCell>
                            </TableRow>
                          )}
                        </TableBody>
                      </Table>
                    ) : null}
                  </Collapse>
                </Stack>
              ))}
            </Stack>
          )}

          <Button size="small" onClick={() => { setShowForm(false); setSearchTerm('') }}>
            Cancel
          </Button>
        </Stack>
      )}
    </Stack>
  )
}
