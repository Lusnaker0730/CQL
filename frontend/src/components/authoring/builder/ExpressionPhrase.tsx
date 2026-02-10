import { Typography } from '@mui/material'
import type { ElementInstance } from '../../../types/authoring'

interface ExpressionPhraseProps {
  element: ElementInstance
}

export default function ExpressionPhrase({ element }: ExpressionPhraseProps) {
  const parts: string[] = []

  // Element name
  const nameField = element.fields?.find((f) => f.id === 'element_name')
  if (nameField?.value) {
    parts.push(String(nameField.value))
  } else {
    parts.push(element.name)
  }

  // VSAC field
  const vsacField = element.fields?.find((f) => f.type?.endsWith('_vsac'))
  if (vsacField?.value) {
    const vsVal = vsacField.value as Record<string, unknown> | string
    if (typeof vsVal === 'object' && vsVal !== null && 'name' in vsVal) {
      parts.push(`(${vsVal.name})`)
    } else if (typeof vsVal === 'string' && vsVal) {
      parts.push(`(${vsVal})`)
    }
  }

  // Modifiers
  if (element.modifiers && element.modifiers.length > 0) {
    const modNames = element.modifiers.map((m) => m.name).join(' > ')
    parts.push(`[${modNames}]`)
  }

  return (
    <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
      {parts.join(' ')}
    </Typography>
  )
}
