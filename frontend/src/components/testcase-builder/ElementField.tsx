import { Box, TextField, Typography } from '@mui/material'
import Editor from '@monaco-editor/react'
import PrimitiveField from './PrimitiveField'
import CodeField from './CodeField'
import CodeableConceptField from './CodeableConceptField'
import PeriodField from './PeriodField'
import QuantityField from './QuantityField'
import HumanNameField from './HumanNameField'
import AddressField from './AddressField'
import ContactPointField from './ContactPointField'
import ReferenceField from './ReferenceField'
import IdentifierField from './IdentifierField'
import ChoiceTypeField from './ChoiceTypeField'
import GenericComplexField from './GenericComplexField'
import ArrayFieldWrapper from './ArrayFieldWrapper'
import type { ElementMetadata } from '../../types'

const PRIMITIVE_TYPES = new Set([
  'string', 'boolean', 'integer', 'decimal', 'uri', 'url', 'canonical',
  'date', 'dateTime', 'instant', 'time', 'code', 'oid', 'id', 'uuid',
  'markdown', 'base64Binary', 'positiveInt', 'unsignedInt', 'xhtml',
])

const COMPLEX_TYPE_MAP: Record<string, string> = {
  CodeableConcept: 'CodeableConcept',
  Period: 'Period',
  Quantity: 'Quantity',
  SimpleQuantity: 'Quantity',
  Age: 'Quantity',
  Duration: 'Quantity',
  Distance: 'Quantity',
  Count: 'Quantity',
  HumanName: 'HumanName',
  Address: 'Address',
  ContactPoint: 'ContactPoint',
  Reference: 'Reference',
  Identifier: 'Identifier',
  Coding: 'Coding',
}

interface ElementFieldProps {
  element: ElementMetadata
  path: string
  value: unknown
  onChange: (value: unknown) => void
  depth: number
}

export default function ElementField({ element, path, value, onChange, depth }: ElementFieldProps) {
  // Deep fallback: render inline JSON editor
  if (depth >= 3) {
    return (
      <Box sx={{ mb: 1 }}>
        <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
          {element.name} (JSON)
        </Typography>
        <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
          <Editor
            height="100px"
            language="json"
            value={value !== undefined ? JSON.stringify(value, null, 2) : ''}
            onChange={(v) => {
              try {
                onChange(v ? JSON.parse(v) : undefined)
              } catch {
                // Don't update on invalid JSON
              }
            }}
            options={{
              minimap: { enabled: false },
              fontSize: 12,
              lineNumbers: 'off',
              scrollBeyondLastLine: false,
              wordWrap: 'on',
              automaticLayout: true,
            }}
          />
        </Box>
      </Box>
    )
  }

  // Handle choice types
  if (element.isChoiceType) {
    return (
      <ChoiceTypeField
        element={element}
        value={value}
        onChange={onChange}
        depth={depth}
      />
    )
  }

  // Handle arrays
  if (element.isArray) {
    const items = Array.isArray(value) ? value : []
    const singleElement: ElementMetadata = { ...element, isArray: false }

    return (
      <ArrayFieldWrapper
        label={`${element.name}${element.isRequired ? ' *' : ''}`}
        items={items}
        onAdd={() => {
          const defaultValue = getDefaultValue(element)
          onChange([...items, defaultValue])
        }}
        onRemove={(i) => {
          const next = items.filter((_, idx) => idx !== i)
          onChange(next.length > 0 ? next : undefined)
        }}
        renderItem={(item, i) => (
          <ElementField
            element={singleElement}
            path={`${path}[${i}]`}
            value={item}
            onChange={(val) => {
              const next = [...items]
              next[i] = val
              onChange(next)
            }}
            depth={depth}
          />
        )}
      />
    )
  }

  const type = element.type

  // Reference type (from referenceTargets)
  if (type === 'Reference' || element.referenceTargets.length > 0) {
    return <ReferenceField element={element} value={value} onChange={onChange} />
  }

  // Code with binding
  if (type === 'code' && element.bindingValueSetUrl) {
    return <CodeField element={element} value={value} onChange={onChange} />
  }

  // Primitive types
  if (PRIMITIVE_TYPES.has(type)) {
    return <PrimitiveField element={element} value={value} onChange={onChange} />
  }

  // Known complex types
  const mapped = COMPLEX_TYPE_MAP[type]
  if (mapped) {
    switch (mapped) {
      case 'CodeableConcept':
        return <CodeableConceptField element={element} value={value} onChange={onChange} />
      case 'Period':
        return <PeriodField element={element} value={value} onChange={onChange} />
      case 'Quantity':
        return <QuantityField element={element} value={value} onChange={onChange} />
      case 'HumanName':
        return <HumanNameField element={element} value={value} onChange={onChange} />
      case 'Address':
        return <AddressField element={element} value={value} onChange={onChange} />
      case 'ContactPoint':
        return <ContactPointField element={element} value={value} onChange={onChange} />
      case 'Reference':
        return <ReferenceField element={element} value={value} onChange={onChange} />
      case 'Identifier':
        return <IdentifierField element={element} value={value} onChange={onChange} />
      case 'Coding':
        return <CodeableConceptField element={{ ...element, type: 'CodeableConcept' }} value={value ? { coding: [value] } : undefined} onChange={(v) => {
          const cc = v as { coding?: unknown[] }
          onChange(cc?.coding?.[0])
        }} />
    }
  }

  // Generic complex type with children
  if (element.children && element.children.length > 0) {
    return <GenericComplexField element={element} value={value} onChange={onChange} depth={depth} />
  }

  // Fallback: text field
  return (
    <TextField
      label={element.name}
      size="small"
      fullWidth
      value={typeof value === 'object' ? JSON.stringify(value) : (value ?? '')}
      onChange={(e) => onChange(e.target.value || undefined)}
      required={element.isRequired}
      helperText={element.description || `Type: ${type}`}
      sx={{ mb: 1 }}
    />
  )
}

function getDefaultValue(element: ElementMetadata): unknown {
  const type = element.type
  if (type === 'boolean') return false
  if (PRIMITIVE_TYPES.has(type)) return ''
  if (COMPLEX_TYPE_MAP[type] === 'CodeableConcept') return { coding: [{ system: '', code: '', display: '' }] }
  if (COMPLEX_TYPE_MAP[type] === 'Period') return { start: '', end: '' }
  if (COMPLEX_TYPE_MAP[type] === 'Quantity') return { value: 0, unit: '' }
  if (COMPLEX_TYPE_MAP[type] === 'Reference') return { reference: '' }
  if (COMPLEX_TYPE_MAP[type] === 'Identifier') return { system: '', value: '' }
  if (COMPLEX_TYPE_MAP[type] === 'HumanName') return { family: '', given: [] }
  if (COMPLEX_TYPE_MAP[type] === 'ContactPoint') return { system: 'phone', value: '' }
  if (COMPLEX_TYPE_MAP[type] === 'Address') return { line: [], city: '' }
  return {}
}
