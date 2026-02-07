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
  FormControlLabel,
  Switch,
} from '@mui/material'
import {
  PlayArrow as PlayIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  Timer as TimerIcon,
  BugReport as DebugIcon,
} from '@mui/icons-material'
import { useDispatch, useSelector } from 'react-redux'
import type { RootState } from '../../store'
import { setPatientId, setFhirServerUrl } from '../../store/executionSlice'
import { useExecute } from '../../hooks/useCql'
import DebugPanel from './DebugPanel'

export default function ExecutionPanel() {
  const dispatch = useDispatch()
  const { cqlContent } = useSelector((state: RootState) => state.editor)
  const { patientId, fhirServerUrl, isExecuting, results, errors, executionTimeMs, debugTrace } = useSelector(
    (state: RootState) => state.execution
  )
  const executeMutation = useExecute()
  const [expandedResults, setExpandedResults] = useState<Set<string>>(new Set())
  const [debugMode, setDebugMode] = useState(false)

  const handleExecute = () => {
    executeMutation.mutate({
      cql: cqlContent,
      patientId: patientId || undefined,
      fhirServerUrl: fhirServerUrl || undefined,
      debugMode,
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
      return <Chip label="null" size="small" sx={{ bgcolor: 'rgba(84,110,122,0.1)', color: 'text.secondary' }} />
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
      return <Typography variant="body2" sx={{ color: 'primary.dark', fontWeight: 600 }}>{value}</Typography>
    }
    if (typeof value === 'string') {
      return <Typography variant="body2" sx={{ color: 'text.primary' }}>"{value}"</Typography>
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

        <Stack direction="row" spacing={2} alignItems="center">
          <Button
            variant="contained"
            startIcon={isExecuting ? <CircularProgress size={20} color="inherit" /> : <PlayIcon />}
            onClick={handleExecute}
            disabled={isExecuting || !cqlContent}
            fullWidth
            sx={{
              py: 1.2,
              background: 'linear-gradient(135deg, #0D7377 0%, #14A3A8 100%)',
              fontSize: '0.95rem',
              '&:hover': {
                background: 'linear-gradient(135deg, #095052 0%, #0D7377 100%)',
              },
              '&.Mui-disabled': {
                background: 'rgba(0,0,0,0.12)',
              },
            }}
          >
            {isExecuting ? 'Executing...' : 'Execute CQL'}
          </Button>
        </Stack>

        <FormControlLabel
          control={
            <Switch
              size="small"
              checked={debugMode}
              onChange={(e) => setDebugMode(e.target.checked)}
              sx={{
                '& .MuiSwitch-switchBase.Mui-checked': {
                  color: 'secondary.main',
                },
                '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': {
                  backgroundColor: 'secondary.main',
                },
              }}
            />
          }
          label={
            <Stack direction="row" spacing={0.5} alignItems="center">
              <DebugIcon sx={{ fontSize: 16, color: debugMode ? 'secondary.main' : 'text.secondary' }} />
              <Typography variant="body2" color={debugMode ? 'secondary.main' : 'text.secondary'}>
                Debug Mode
              </Typography>
            </Stack>
          }
        />

        {executionTimeMs !== null && (
          <Chip
            icon={<TimerIcon sx={{ fontSize: 16 }} />}
            label={`${executionTimeMs}ms`}
            size="small"
            sx={{
              alignSelf: 'flex-start',
              bgcolor: 'rgba(13,115,119,0.08)',
              color: 'primary.dark',
              fontWeight: 600,
            }}
          />
        )}

        <Divider />

        {errors.length > 0 && (
          <Alert severity="error">
            {errors?.map((error, i) => (
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
                              sx={{ color: 'primary.main' }}
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
                          <Typography variant="body2" sx={{ fontWeight: 600, color: 'text.primary' }}>
                            {name}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={result.valueType}
                            size="small"
                            variant="outlined"
                            sx={{
                              borderColor: 'rgba(27,58,92,0.3)',
                              color: 'secondary.main',
                              fontWeight: 500,
                            }}
                          />
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
                                  bgcolor: '#F8FAFB',
                                  borderRadius: '8px',
                                  border: '1px solid rgba(13,115,119,0.1)',
                                  fontSize: '0.75rem',
                                  overflow: 'auto',
                                  maxHeight: 200,
                                  fontFamily: '"Consolas", monospace',
                                  color: 'text.primary',
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

        {debugTrace && <DebugPanel trace={debugTrace} />}
      </Stack>
    </Paper>
  )
}
