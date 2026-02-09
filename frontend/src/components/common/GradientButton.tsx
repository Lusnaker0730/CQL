import { Button, type ButtonProps } from '@mui/material'

const gradientSx = {
  background: 'linear-gradient(135deg, #0D7377 0%, #14A3A8 100%)',
  '&:hover': { background: 'linear-gradient(135deg, #095052 0%, #0D7377 100%)' },
} as const

export default function GradientButton(props: ButtonProps) {
  const { sx, variant = 'contained', size = 'small', ...rest } = props
  return (
    <Button
      variant={variant}
      size={size}
      sx={{ ...gradientSx, ...((sx as object) || {}) }}
      {...rest}
    />
  )
}
