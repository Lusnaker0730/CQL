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
  const { cqlContent, isTranslating, errors } = useSelector((state: RootState) => state.editor)
  const [rightPanelTab, setRightPanelTab] = useState(0)

  const translateMutation = useTranslate()
  const saveLibraryMutation = useCreateLibrary()

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
          <Paper sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ p: 1, borderBottom: '1px solid', borderColor: 'divider' }}>
              <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center">
                <Typography variant="subtitle1" fontWeight="medium">
                  CQL Editor
                </Typography>
                <Stack direction="row" spacing={1}>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={
                      isTranslating ? <CircularProgress size={16} /> : <TranslateIcon />
                    }
                    onClick={handleTranslate}
                    disabled={isTranslating || !cqlContent}
                  >
                    {isTranslating ? 'Translating...' : 'Translate'}
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<SaveIcon />}
                    onClick={handleSaveLibrary}
                    disabled={saveLibraryMutation.isPending || errors.length > 0}
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
              />
            </Box>
          </Paper>
        </Grid>

        <Grid item xs={12} md={5} sx={{ height: '100%' }}>
          <Paper sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Tabs
              value={rightPanelTab}
              onChange={(_, v) => setRightPanelTab(v)}
              sx={{ borderBottom: '1px solid', borderColor: 'divider' }}
            >
              <Tab label="ELM / Errors" />
              <Tab label="Execute" />
            </Tabs>
            <Box sx={{ flexGrow: 1, overflow: 'auto' }}>
              <TabPanel value={rightPanelTab} index={0}>
                <ElmViewer />
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
