import { Box, Typography } from '@mui/material'
import ElementField from './ElementField'
import type { ElementMetadata } from '../../types'

interface GenericComplexFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
  depth: number
}

export default function GenericComplexField({ element, value, onChange, depth }: GenericComplexFieldProps) {
  const obj = (value as Record<string, unknown>) || {}

  const handleChildChange = (childName: string, childValue: unknown) => {
    const newObj = { ...obj }
    if (childValue === undefined || childValue === null || childValue === '') {
      delete newObj[childName]
    } else {
      newObj[childName] = childValue
    }
    onChange(Object.keys(newObj).length > 0 ? newObj : undefined)
  }

  if (!element.children || element.children.length === 0) {
    return null
  }

  return (
    <Box sx={{ mb: 1, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
        {element.name} {element.isRequired && '*'}
      </Typography>
      {element.children.map((child) => (
        <ElementField
          key={child.name}
          element={child}
          path={`${element.path}.${child.name}`}
          value={obj[child.name]}
          onChange={(val) => handleChildChange(child.name, val)}
          depth={depth + 1}
        />
      ))}
    </Box>
  )
}
