import { memo } from 'react'
import { Skeleton, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material'

interface TableSkeletonProps {
  columns: number
  rows?: number
  hasCheckbox?: boolean
}

function TableSkeleton({ columns, rows = 5, hasCheckbox = false }: TableSkeletonProps) {
  const totalColumns = hasCheckbox ? columns + 1 : columns

  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            {hasCheckbox && (
              <TableCell padding="checkbox" scope="col">
                <Skeleton variant="rectangular" width={20} height={20} />
              </TableCell>
            )}
            {Array.from({ length: columns }).map((_, i) => (
              <TableCell key={i} scope="col">
                <Skeleton variant="text" width={`${60 + Math.random() * 40}%`} />
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {Array.from({ length: rows }).map((_, rowIdx) => (
            <TableRow key={rowIdx}>
              {Array.from({ length: totalColumns }).map((_, colIdx) => (
                <TableCell key={colIdx}>
                  <Skeleton variant="text" width={`${50 + (colIdx * 7) % 50}%`} />
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

export default memo(TableSkeleton)
