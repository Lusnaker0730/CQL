import { Box, Grid, Typography } from '@mui/material'
import MeasurePanel from '../components/measure/MeasurePanel'
import CqlEditor from '../components/editor/CqlEditor'

export default function MeasuresPage() {
  return (
    <Box sx={{ height: 'calc(100vh - 120px)', p: 2 }}>
      <Box sx={{ mb: 2 }}>
        <Typography variant="h5" sx={{ mb: 0.5 }}>
          Quality Measures (eCQM)
        </Typography>
        <Box
          sx={{
            width: 48,
            height: 3,
            borderRadius: 2,
            background: 'linear-gradient(90deg, #0D7377, #14A3A8)',
            mb: 1,
          }}
        />
        <Typography variant="body2" color="text.secondary">
          Evaluate electronic Clinical Quality Measures using CQL. Define your measure logic in the
          editor and evaluate against patient data.
        </Typography>
      </Box>

      <Grid container spacing={2} sx={{ height: 'calc(100% - 90px)' }}>
        <Grid item xs={12} md={6}>
          <CqlEditor height="100%" />
        </Grid>
        <Grid item xs={12} md={6}>
          <MeasurePanel />
        </Grid>
      </Grid>
    </Box>
  )
}
