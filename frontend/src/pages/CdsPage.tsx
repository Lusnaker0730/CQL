import { Box, Grid, Typography } from '@mui/material'
import CdsPanel from '../components/cds/CdsPanel'
import CqlEditor from '../components/editor/CqlEditor'

export default function CdsPage() {
  return (
    <Box sx={{ height: 'calc(100vh - 120px)', p: 2 }}>
      <Typography variant="h5" gutterBottom>
        CDS Hooks Integration
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Clinical Decision Support Hooks allow you to invoke CQL-based decision support at key
        clinical decision points.
      </Typography>

      <Grid container spacing={2} sx={{ height: 'calc(100% - 80px)' }}>
        <Grid item xs={12} md={6}>
          <CqlEditor height="100%" />
        </Grid>
        <Grid item xs={12} md={6}>
          <CdsPanel />
        </Grid>
      </Grid>
    </Box>
  )
}
