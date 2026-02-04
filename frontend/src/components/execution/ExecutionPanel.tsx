import React, { useState } from 'react'
import {
  Box,
  Paper,
  TextField,
  Button,
  Typography,
  Stack,
  Divider,
  CircularProgress,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Collapse,
  IconButton,
} from '@mui/material'
import {
  PlayArrow as PlayIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
} from '@mui/icons-material'
import { useDispatch, useSelector } from 'react-redux'
import type { RootState } from '../../store'
import { setPatientId, setFhirServerUrl } from '../../store/executionSlice'
import { useExecute } from '../../hooks/useCql'

export default function ExecutionPanel() {
  const dispatch = useDispatch()
  const { cqlContent } = useSelector((state: RootState) => state.editor)
  const { patientId, fhirServerUrl, isExecuting, results, errors, executionTimeMs } = useSelector(
    (state: RootState) => state.execution
  )
  const executeMutation = useExecute()
  const [expandedResults, setExpandedResults] = useState<Set<string>>(new Set())

  const handleExecute = () => {
    executeMutation.mutate({
      cql: cqlContent,
      patientId: patientId || undefined,
      fhirServerUrl: fhirServerUrl || undefined,
    })
  }

  const toggleExpanded = (name: string) => {
    const newExpanded = new Set(expandedResults)
    if (newExpanded.has(name)) {
      newExpanded.delete(name)
    } else {
      newExpanded.add(name)
    }
    setExpandedResults(newExpanded)
  }

  const renderValue = (value: unknown): React.ReactNode => {
    if (value === null || value === undefined) {
      return <Chip label="null" size="small" />
    }
    if (typeof value === 'boolean') {
      return (
        <Chip
          label={value ? 'true' : 'false'}
          color={value ? 'success' : 'default'}
          size="small"
        />
      )
    }
    if (typeof value === 'number') {
      return <Typography variant="body2">{value}</Typography>
    }
    if (typeof value === 'string') {
      return <Typography variant="body2">"{value}"</Typography>
    }
    if (Array.isArray(value)) {
      return (
        <Typography variant="body2" color="text.secondary">
          List [{value.length} items]
        </Typography>
      )
    }
    if (typeof value === 'object') {
      return (
        <Typography variant="body2" color="text.secondary">
          {JSON.stringify(value).substring(0, 50)}...
        </Typography>
      )
    }
    return <Typography variant="body2">{String(value)}</Typography>
  }

  return (
    <Paper sx={{ p: 2, height: '100%' }}>
      <Typography variant="h6" gutterBottom>
        CQL Execution
      </Typography>

      <Stack spacing={2}>
        <TextField
          label="FHIR Server URL"
          value={fhirServerUrl}
          onChange={(e) => dispatch(setFhirServerUrl(e.target.value))}
          size="small"
          fullWidth
        />

        <TextField
          label="Patient ID"
          value={patientId}
          onChange={(e) => dispatch(setPatientId(e.target.value))}
          size="small"
          fullWidth
          placeholder="e.g., example-patient-1"
        />

        <Button
          variant="contained"
          startIcon={isExecuting ? <CircularProgress size={20} /> : <PlayIcon />}
          onClick={handleExecute}
          disabled={isExecuting || !cqlContent}
          fullWidth
        >
          {isExecuting ? 'Executing...' : 'Execute CQL'}
        </Button>

        {executionTimeMs !== null && (
          <Typography variant="body2" color="text.secondary">
            Execution time: {executionTimeMs}ms
          </Typography>
        )}

        <Divider />

        {errors.length > 0 && (
          <Alert severity="error">
            {errors.map((error, i) => (
              <div key={i}>{error}</div>
            ))}
          </Alert>
        )}

        {Object.keys(results).length > 0 && (
          <Box>
            <Typography variant="subtitle1" gutterBottom>
              Results
            </Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell width={40}></TableCell>
                    <TableCell>Expression</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Value</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {Object.entries(results).map(([name, result]) => (
                    <React.Fragment key={name}>
                      <TableRow hover>
                        <TableCell>
                          {typeof result.value === 'object' && result.value !== null && (
                            <IconButton
                              size="small"
                              onClick={() => toggleExpanded(name)}
                            >
                              {expandedResults.has(name) ? (
                                <ExpandLessIcon />
                              ) : (
                                <ExpandMoreIcon />
                              )}
                            </IconButton>
                          )}
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" fontWeight="medium">
                            {name}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip label={result.valueType} size="small" variant="outlined" />
                        </TableCell>
                        <TableCell>{renderValue(result.value)}</TableCell>
                      </TableRow>
                      {typeof result.value === 'object' && result.value !== null && (
                        <TableRow>
                          <TableCell colSpan={4} sx={{ py: 0 }}>
                            <Collapse in={expandedResults.has(name)}>
                              <Box
                                component="pre"
                                sx={{
                                  p: 2,
                                  bgcolor: 'grey.100',
                                  borderRadius: 1,
                                  fontSize: '0.75rem',
                                  overflow: 'auto',
                                  maxHeight: 200,
                                }}
                              >
                                {JSON.stringify(result.value, null, 2)}
                              </Box>
                            </Collapse>
                          </TableCell>
                        </TableRow>
                      )}
                    </React.Fragment>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        )}
      </Stack>
    </Paper>
  )
}
