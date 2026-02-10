import { Select, MenuItem, FormControl } from '@mui/material'

interface ConjunctionTypeSelectProps {
  value: string
  onChange: (value: string) => void
}

export default function ConjunctionTypeSelect({ value, onChange }: ConjunctionTypeSelectProps) {
  return (
    <FormControl size="small">
      <Select
        value={value}
        onChange={(e) => onChange(e.target.value as string)}
        sx={{
          minWidth: 80,
          '& .MuiSelect-select': {
            py: 0.5,
            fontWeight: 600,
            fontSize: '0.85rem',
          },
        }}
      >
        <MenuItem value="And">And</MenuItem>
        <MenuItem value="Or">Or</MenuItem>
      </Select>
    </FormControl>
  )
}
