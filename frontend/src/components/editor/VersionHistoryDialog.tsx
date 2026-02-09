import { useState } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Typography,
} from '@mui/material'
import StatusChip from '../common/StatusChip'

interface VersionEntry {
  id?: number | string
  version: string
  status: string
  createdAt?: string
  updatedAt?: string
}

interface VersionHistoryDialogProps {
  open: boolean
  onClose: () => void
  versions: VersionEntry[]
  onSelectVersion: (id: string | number) => void
  entityType: 'library' | 'measure'
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  try {
    return new Date(dateStr).toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return dateStr
  }
}

export default function VersionHistoryDialog({
  open,
  onClose,
  versions,
  onSelectVersion,
  entityType,
}: VersionHistoryDialogProps) {
  const [page, setPage] = useState(0)
  const rowsPerPage = 10

  const label = entityType === 'library' ? 'Library' : 'Measure'

  const paginatedVersions = versions.slice(
    page * rowsPerPage,
    page * rowsPerPage + rowsPerPage,
  )

  const handleRowClick = (entry: VersionEntry) => {
    if (entry.id != null) {
      onSelectVersion(entry.id)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{label} Version History</DialogTitle>
      <DialogContent sx={{ px: 0 }}>
        {versions.length === 0 ? (
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ px: 3, py: 2 }}
          >
            No version history available.
          </Typography>
        ) : (
          <>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Version</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Date</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {paginatedVersions.map((entry, index) => (
                    <TableRow
                      key={entry.id ?? index}
                      hover
                      sx={{
                        cursor: entry.id != null ? 'pointer' : 'default',
                      }}
                      onClick={() => handleRowClick(entry)}
                    >
                      <TableCell>
                        <Typography variant="body2" fontWeight={500}>
                          {entry.version}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <StatusChip status={entry.status} />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" color="text.secondary">
                          {formatDate(entry.updatedAt || entry.createdAt)}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              component="div"
              count={versions.length}
              page={page}
              onPageChange={(_, newPage) => setPage(newPage)}
              rowsPerPage={rowsPerPage}
              rowsPerPageOptions={[10]}
            />
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} size="small">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  )
}
