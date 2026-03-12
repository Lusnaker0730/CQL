import { Chip, type ChipProps } from '@mui/material'

/** Maps CQL/FHIR result types to display labels and colors */
const TYPE_STYLES: Record<string, { label: string; color: string; bg: string }> = {
  // Booleans
  'System.Boolean': { label: 'Boolean', color: '#42A5F5', bg: 'rgba(33,150,243,0.10)' },
  'System.Any': { label: 'Any', color: '#90A4AE', bg: 'rgba(84,110,122,0.10)' },
  // Numbers
  'System.Integer': { label: 'Integer', color: '#BA68C8', bg: 'rgba(156,39,176,0.10)' },
  'System.Decimal': { label: 'Decimal', color: '#BA68C8', bg: 'rgba(156,39,176,0.10)' },
  'System.Long': { label: 'Long', color: '#BA68C8', bg: 'rgba(156,39,176,0.10)' },
  // String / DateTime
  'System.String': { label: 'String', color: '#A1887F', bg: 'rgba(78,52,46,0.10)' },
  'System.DateTime': { label: 'DateTime', color: '#4DB6AC', bg: 'rgba(0,105,92,0.10)' },
  'System.Date': { label: 'Date', color: '#4DB6AC', bg: 'rgba(0,105,92,0.10)' },
  'System.Time': { label: 'Time', color: '#4DB6AC', bg: 'rgba(0,105,92,0.10)' },
  // Quantity
  'System.Quantity': { label: 'Quantity', color: '#FF9800', bg: 'rgba(230,81,0,0.10)' },
  // Code types
  'System.Code': { label: 'Code', color: '#F06292', bg: 'rgba(173,20,87,0.10)' },
  'System.Concept': { label: 'Concept', color: '#F06292', bg: 'rgba(173,20,87,0.10)' },
  // FHIR Patient
  'FHIR.Patient': { label: 'Patient', color: '#14A3A8', bg: 'rgba(13,115,119,0.10)' },
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
    return { label: `List<${inner.label}>`, color: '#66BB6A', bg: 'rgba(46,125,50,0.10)' }
  }

  // Interval<X>
  const intervalMatch = resultType.match(INTERVAL_PATTERN)
  if (intervalMatch) {
    const inner = resolveStyle(intervalMatch[1])
    return { label: `Interval<${inner.label}>`, color: '#FFD54F', bg: 'rgba(245,127,23,0.10)' }
  }

  // FHIR resource types (e.g. FHIR.Observation)
  if (resultType.startsWith('FHIR.')) {
    const name = resultType.slice(5)
    return { label: name, color: '#FF9800', bg: 'rgba(230,81,0,0.10)' }
  }

  // Fallback
  const short = resultType.includes('.') ? resultType.split('.').pop()! : resultType
  return { label: short, color: '#90A4AE', bg: 'rgba(84,110,122,0.10)' }
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
