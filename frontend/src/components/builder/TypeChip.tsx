import { Chip, type ChipProps } from '@mui/material'

/** Maps CQL/FHIR result types to display labels and colors */
const TYPE_STYLES: Record<string, { label: string; color: string; bg: string }> = {
  // Booleans
  'System.Boolean': { label: 'Boolean', color: '#1565C0', bg: '#E3F2FD' },
  'System.Any': { label: 'Any', color: '#546E7A', bg: '#ECEFF1' },
  // Numbers
  'System.Integer': { label: 'Integer', color: '#6A1B9A', bg: '#F3E5F5' },
  'System.Decimal': { label: 'Decimal', color: '#6A1B9A', bg: '#F3E5F5' },
  'System.Long': { label: 'Long', color: '#6A1B9A', bg: '#F3E5F5' },
  // String / DateTime
  'System.String': { label: 'String', color: '#4E342E', bg: '#EFEBE9' },
  'System.DateTime': { label: 'DateTime', color: '#00695C', bg: '#E0F2F1' },
  'System.Date': { label: 'Date', color: '#00695C', bg: '#E0F2F1' },
  'System.Time': { label: 'Time', color: '#00695C', bg: '#E0F2F1' },
  // Quantity
  'System.Quantity': { label: 'Quantity', color: '#E65100', bg: '#FFF3E0' },
  // Code types
  'System.Code': { label: 'Code', color: '#AD1457', bg: '#FCE4EC' },
  'System.Concept': { label: 'Concept', color: '#AD1457', bg: '#FCE4EC' },
  // FHIR Patient
  'FHIR.Patient': { label: 'Patient', color: '#0D7377', bg: '#E0F7FA' },
}

/** Patterns for list-of types */
const LIST_PATTERN = /^list<(.+)>$/i
const INTERVAL_PATTERN = /^interval<(.+)>$/i

function resolveStyle(resultType: string): { label: string; color: string; bg: string } {
  // Direct match
  const direct = TYPE_STYLES[resultType]
  if (direct) return direct

  // List<X>
  const listMatch = resultType.match(LIST_PATTERN)
  if (listMatch) {
    const inner = resolveStyle(listMatch[1])
    return { label: `List<${inner.label}>`, color: '#2E7D32', bg: '#E8F5E9' }
  }

  // Interval<X>
  const intervalMatch = resultType.match(INTERVAL_PATTERN)
  if (intervalMatch) {
    const inner = resolveStyle(intervalMatch[1])
    return { label: `Interval<${inner.label}>`, color: '#F57F17', bg: '#FFFDE7' }
  }

  // FHIR resource types (e.g. FHIR.Observation)
  if (resultType.startsWith('FHIR.')) {
    const name = resultType.slice(5)
    return { label: name, color: '#E65100', bg: '#FFF3E0' }
  }

  // Fallback
  const short = resultType.includes('.') ? resultType.split('.').pop()! : resultType
  return { label: short, color: '#546E7A', bg: '#ECEFF1' }
}

interface TypeChipProps {
  resultType: string | undefined
  size?: ChipProps['size']
}

export default function TypeChip({ resultType, size = 'small' }: TypeChipProps) {
  if (!resultType) return null
  const style = resolveStyle(resultType)
  return (
    <Chip
      label={style.label}
      size={size}
      sx={{
        height: 18,
        fontSize: '0.6rem',
        fontWeight: 600,
        fontFamily: 'monospace',
        color: style.color,
        bgcolor: style.bg,
        border: `1px solid ${style.color}22`,
        '& .MuiChip-label': { px: 0.75 },
      }}
    />
  )
}
