import { useState } from 'react'
import {
  Box,
  Paper,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  IconButton,
  Collapse,
  Menu,
  MenuItem,
} from '@mui/material'
import {
  Delete as DeleteIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  Download as DownloadIcon,
} from '@mui/icons-material'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import type { MeasureReport } from '../../types'

export default function MeasureReportHistory() {
  const queryClient = useQueryClient()
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [exportAnchor, setExportAnchor] = useState<{ el: HTMLElement; id: number } | null>(null)

  const { data: reports = [], isLoading } = useQuery({
    queryKey: ['measureReports'],
    queryFn: () => measureApi.getReports(),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => measureApi.deleteReport(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['measureReports'] }),
  })

  const handleExport = async (reportId: number, format: string) => {
    setExportAnchor(null)
    try {
      const blob = await measureApi.exportReport(reportId, format)
      const ext = format === 'csv' ? 'csv' : format === 'excel' ? 'xlsx' : 'json'
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `measure-report-${reportId}.${ext}`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      console.error('Export failed', err)
    }
  }

  const getScoreColor = (score: number | undefined): 'success' | 'warning' | 'error' | 'default' => {
    if (score == null) return 'default'
    if (score >= 80) return 'success'
    if (score >= 60) return 'warning'
    return 'error'
  }

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>Report History</Typography>

      {isLoading && <Typography variant="body2" color="text.secondary">Loading reports...</Typography>}

      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell width={40} />
              <TableCell>Measure</TableCell>
              <TableCell>Period</TableCell>
              <TableCell align="right">Score</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Date</TableCell>
              <TableCell align="right">Duration</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {reports.map((report: MeasureReport) => (
              <Box component="tbody" key={report.id}>
                <TableRow hover>
                  <TableCell>
                    <IconButton size="small" onClick={() => setExpandedId(expandedId === report.id ? null : report.id)}>
                      {expandedId === report.id ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                    </IconButton>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" fontWeight={500}>{report.measureName}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption">{report.periodStart} - {report.periodEnd}</Typography>
                  </TableCell>
                  <TableCell align="right">
                    {report.measureScore != null && (
                      <Chip
                        label={`${report.measureScore.toFixed(1)}%`}
                        size="small"
                        color={getScoreColor(report.measureScore)}
                      />
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip label={report.status || 'complete'} size="small"
                      color={report.status === 'complete' ? 'success' : 'warning'} />
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption">
                      {report.createdAt ? new Date(report.createdAt).toLocaleDateString() : ''}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Typography variant="caption">
                      {report.evaluationDurationMs != null ? `${report.evaluationDurationMs}ms` : ''}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <IconButton size="small" onClick={(e) => setExportAnchor({ el: e.currentTarget, id: report.id })}>
                      <DownloadIcon fontSize="small" />
                    </IconButton>
                    <IconButton size="small" color="error" onClick={() => deleteMutation.mutate(report.id)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell colSpan={8} sx={{ py: 0, borderBottom: expandedId === report.id ? undefined : 'none' }}>
                    <Collapse in={expandedId === report.id}>
                      <Box sx={{ p: 2 }}>
                        {report.evaluationResult && (
                          <Box component="pre" sx={{
                            p: 2,
                            bgcolor: '#F8FAFB',
                            borderRadius: '8px',
                            border: '1px solid rgba(13,115,119,0.1)',
                            fontSize: '0.7rem',
                            overflow: 'auto',
                            maxHeight: 300,
                            fontFamily: '"Consolas", monospace',
                          }}>
                            {JSON.stringify(report.evaluationResult, null, 2)}
                          </Box>
                        )}
                        {!report.evaluationResult && report.resultJson && (
                          <Box component="pre" sx={{
                            p: 2,
                            bgcolor: '#F8FAFB',
                            borderRadius: '8px',
                            border: '1px solid rgba(13,115,119,0.1)',
                            fontSize: '0.7rem',
                            overflow: 'auto',
                            maxHeight: 300,
                            fontFamily: '"Consolas", monospace',
                          }}>
                            {report.resultJson}
                          </Box>
                        )}
                      </Box>
                    </Collapse>
                  </TableCell>
                </TableRow>
              </Box>
            ))}
            {!isLoading && reports.length === 0 && (
              <TableRow>
                <TableCell colSpan={8} align="center">
                  <Typography variant="body2" color="text.secondary" sx={{ py: 4 }}>
                    No reports yet. Evaluate a measure to generate reports.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Export Menu */}
      <Menu
        anchorEl={exportAnchor?.el}
        open={Boolean(exportAnchor)}
        onClose={() => setExportAnchor(null)}
      >
        <MenuItem onClick={() => exportAnchor && handleExport(exportAnchor.id, 'fhir')}>
          FHIR MeasureReport (JSON)
        </MenuItem>
        <MenuItem onClick={() => exportAnchor && handleExport(exportAnchor.id, 'csv')}>
          CSV
        </MenuItem>
        <MenuItem onClick={() => exportAnchor && handleExport(exportAnchor.id, 'excel')}>
          Excel (XLSX)
        </MenuItem>
      </Menu>
    </Paper>
  )
}
