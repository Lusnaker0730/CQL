import { useState } from 'react'
import {
  Box,
  Grid,
  Paper,
  Button,
  Stack,
  Tabs,
  Tab,
  Typography,
  CircularProgress,
} from '@mui/material'
import {
  Translate as TranslateIcon,
  Save as SaveIcon,
} from '@mui/icons-material'
import { useSelector } from 'react-redux'
import CqlEditor from '../components/editor/CqlEditor'
import ElmViewer from '../components/editor/ElmViewer'
import ExecutionPanel from '../components/execution/ExecutionPanel'
import type { RootState } from '../store'
import { useTranslate, useCreateLibrary } from '../hooks/useCql'
import { useTerminologyValidation } from '../hooks/useTerminologyValidation'

interface TabPanelProps {
  children?: React.ReactNode
  index: number
  value: number
}

function TabPanel({ children, value, index }: TabPanelProps) {
  return (
    <div role="tabpanel" hidden={value !== index} style={{ height: '100%' }}>
      {value === index && <Box sx={{ height: '100%' }}>{children}</Box>}
    </div>
  )
}

export default function EditorPage() {
  const { cqlContent, isTranslating, errors, elmJson } = useSelector((state: RootState) => state.editor)
  const [rightPanelTab, setRightPanelTab] = useState(0)

  const translateMutation = useTranslate()
  const saveLibraryMutation = useCreateLibrary()
  const { results: terminologyResults, isValidating: isTermValidating } = useTerminologyValidation(elmJson)

  const handleTranslate = () => {
    translateMutation.mutate({ cql: cqlContent })
  }

  const handleSaveLibrary = () => {
    saveLibraryMutation.mutate({ cql: cqlContent })
  }

  return (
    <Box sx={{ height: 'calc(100vh - 120px)', p: 2 }}>
      <Grid container spacing={2} sx={{ height: '100%' }}>
        <Grid item xs={12} md={7} sx={{ height: '100%' }}>
          <Paper
            sx={{
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              borderLeft: '3px solid',
              borderLeftColor: 'primary.main',
              overflow: 'hidden',
            }}
          >
            <Box
              sx={{
                p: 1,
                px: 1.5,
                background: 'linear-gradient(135deg, rgba(13,115,119,0.06) 0%, rgba(20,163,168,0.03) 100%)',
                borderBottom: '1px solid',
                borderColor: 'rgba(13,115,119,0.1)',
              }}
            >
              <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center">
                <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'secondary.main' }}>
                  CQL Editor
                </Typography>
                <Stack direction="row" spacing={1}>
                  <Button
                    size="small"
                    variant="contained"
                    startIcon={
                      isTranslating ? <CircularProgress size={16} color="inherit" /> : <TranslateIcon />
                    }
                    onClick={handleTranslate}
                    disabled={isTranslating || !cqlContent}
                    sx={{
                      background: 'linear-gradient(135deg, #1B3A5C 0%, #2D5F8A 100%)',
                      '&:hover': {
                        background: 'linear-gradient(135deg, #0F2440 0%, #1B3A5C 100%)',
                      },
                      '&.Mui-disabled': {
                        background: 'rgba(0,0,0,0.12)',
                      },
                    }}
                  >
                    {isTranslating ? 'Translating...' : 'Translate'}
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<SaveIcon />}
                    onClick={handleSaveLibrary}
                    disabled={saveLibraryMutation.isPending || errors.length > 0}
                    sx={{
                      borderColor: 'rgba(13,115,119,0.4)',
                      color: 'primary.dark',
                      '&:hover': {
                        borderColor: 'primary.main',
                        bgcolor: 'rgba(13,115,119,0.04)',
                      },
                    }}
                  >
                    Save Library
                  </Button>
                </Stack>
              </Stack>
            </Box>
            <Box sx={{ flexGrow: 1, overflow: 'hidden' }}>
              <CqlEditor
                height="100%"
                onTranslate={handleTranslate}
                terminologyIssues={terminologyResults.filter((r) => r.status !== 'valid')}
              />
            </Box>
          </Paper>
        </Grid>

        <Grid item xs={12} md={5} sx={{ height: '100%' }}>
          <Paper
            sx={{
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              borderLeft: '3px solid',
              borderLeftColor: 'secondary.main',
              overflow: 'hidden',
            }}
          >
            <Tabs
              value={rightPanelTab}
              onChange={(_, v) => setRightPanelTab(v)}
              sx={{
                borderBottom: '1px solid',
                borderColor: 'rgba(13,115,119,0.1)',
                bgcolor: 'rgba(27,58,92,0.03)',
              }}
            >
              <Tab label="ELM / Errors" />
              <Tab label="Execute" />
            </Tabs>
            <Box sx={{ flexGrow: 1, overflow: 'auto' }}>
              <TabPanel value={rightPanelTab} index={0}>
                <ElmViewer
                  terminologyResults={terminologyResults}
                  isTermValidating={isTermValidating}
                />
              </TabPanel>
              <TabPanel value={rightPanelTab} index={1}>
                <ExecutionPanel />
              </TabPanel>
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  )
}
