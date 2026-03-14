import { Box, Grid, Skeleton, Stack } from '@mui/material'

export default function DashboardSkeleton() {
  return (
    <Box sx={{ p: 3 }}>
      <Stack spacing={3}>
        {/* Stat cards row */}
        <Grid container spacing={2}>
          {Array.from({ length: 4 }).map((_, i) => (
            <Grid item xs={6} md={3} key={i}>
              <Skeleton variant="rounded" height={100} />
            </Grid>
          ))}
        </Grid>

        {/* Chart areas */}
        <Grid container spacing={2}>
          <Grid item xs={12} md={8}>
            <Skeleton variant="rounded" height={300} />
          </Grid>
          <Grid item xs={12} md={4}>
            <Skeleton variant="rounded" height={300} />
          </Grid>
        </Grid>

        {/* Table area */}
        <Skeleton variant="rounded" height={200} />
      </Stack>
    </Box>
  )
}
