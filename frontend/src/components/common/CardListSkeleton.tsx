import { memo } from 'react'
import { Box, Skeleton, Stack } from '@mui/material'

interface CardListSkeletonProps {
  count?: number
}

function CardListSkeleton({ count = 3 }: CardListSkeletonProps) {
  return (
    <Stack spacing={2}>
      {Array.from({ length: count }).map((_, i) => (
        <Box key={i} sx={{ p: 2, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
          <Stack spacing={1}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Skeleton variant="text" width="40%" height={28} />
              <Skeleton variant="rounded" width={60} height={24} />
            </Stack>
            <Skeleton variant="text" width="70%" />
            <Skeleton variant="text" width="50%" />
          </Stack>
        </Box>
      ))}
    </Stack>
  )
}

export default memo(CardListSkeleton)
