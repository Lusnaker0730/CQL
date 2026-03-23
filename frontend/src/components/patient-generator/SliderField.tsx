import { Box, Typography, Slider, Stack } from '@mui/material'

interface SliderFieldProps {
  label: string
  value: number
  min: number
  max: number
  onChange: (value: number) => void
}

export default function SliderField({ label, value, min, max, onChange }: SliderFieldProps) {
  return (
    <Box>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
        <Typography variant="body2">{label}</Typography>
        <Typography variant="body2" fontWeight={600} sx={{ color: 'primary.main' }}>
          {value}
        </Typography>
      </Stack>
      <Slider
        value={value}
        min={min}
        max={max}
        onChange={(_, v) => onChange(v as number)}
        valueLabelDisplay="auto"
        size="small"
      />
    </Box>
  )
}
