import { Box, Typography, Slider, Stack, Input } from '@mui/material'

interface SliderFieldProps {
  label: string
  value: number
  min: number
  max: number
  onChange: (value: number) => void
}

export default function SliderField({ label, value, min, max, onChange }: SliderFieldProps) {
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value
    if (raw === '') return
    const num = parseInt(raw, 10)
    if (!isNaN(num)) onChange(Math.max(min, Math.min(max, num)))
  }

  return (
    <Box>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
        <Typography variant="body2">{label}</Typography>
        <Input
          value={value}
          size="small"
          onChange={handleInputChange}
          inputProps={{ min, max, step: 1, type: 'number', style: { textAlign: 'right', width: 48, fontWeight: 600 } }}
          sx={{ color: 'primary.main', '& input::-webkit-inner-spin-button': { opacity: 1 } }}
          disableUnderline
        />
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
