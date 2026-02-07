import { Box, Grid, Typography } from '@mui/material'
import CdsPanel from '../components/cds/CdsPanel'
import CqlEditor from '../components/editor/CqlEditor'

export default function CdsPage() {
  return (
    <Box sx={{ height: 'calc(100vh - 120px)', p: 2 }}>
      <Box sx={{ mb: 2 }}>
        <Typography variant="h5" sx={{ mb: 0.5 }}>
          CDS Hooks Integration
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
          Clinical Decision Support Hooks allow you to invoke CQL-based decision support at key
          clinical decision points.
        </Typography>
      </Box>

      <Grid container spacing={2} sx={{ height: 'calc(100% - 90px)' }}>
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
