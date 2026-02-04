import { Box, Grid, Typography } from '@mui/material'
import MeasurePanel from '../components/measure/MeasurePanel'
import CqlEditor from '../components/editor/CqlEditor'

export default function MeasuresPage() {
  return (
    <Box sx={{ height: 'calc(100vh - 120px)', p: 2 }}>
      <Typography variant="h5" gutterBottom>
        Quality Measures (eCQM)
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Evaluate electronic Clinical Quality Measures using CQL. Define your measure logic in the
        editor and evaluate against patient data.
      </Typography>

      <Grid container spacing={2} sx={{ height: 'calc(100% - 80px)' }}>
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
