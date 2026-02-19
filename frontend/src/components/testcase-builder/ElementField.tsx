import { Box, Typography } from '@mui/material'
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

// Quantity profile types that all render as QuantityField
const QUANTITY_TYPES = new Set(['Quantity', 'SimpleQuantity', 'Age', 'Duration', 'Distance', 'Count'])

interface ElementFieldProps {
  element: ElementMetadata
  path: string
  value: unknown
  onChange: (value: unknown, choiceFieldName?: string) => void
  /** For choice type elements loaded from JSON, the detected type (e.g. "Quantity") */
  initialChoiceType?: string
  depth: number
}

export default function ElementField({ element, path, value, onChange, initialChoiceType, depth }: ElementFieldProps) {
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
        initialChoiceType={initialChoiceType}
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

  // Code with binding (external ValueSet lookup)
  if (type === 'code' && element.bindingValueSetUrl) {
    return <CodeField element={element} value={value} onChange={onChange} />
  }

  // Specialized complex type components
  if (type === 'CodeableConcept') {
    return <CodeableConceptField element={element} value={value} onChange={onChange} />
  }
  if (type === 'Coding') {
    return <CodeableConceptField element={{ ...element, type: 'CodeableConcept' }} value={value ? { coding: [value] } : undefined} onChange={(v) => {
      const cc = v as { coding?: unknown[] }
      onChange(cc?.coding?.[0])
    }} />
  }
  if (type === 'Period') {
    return <PeriodField element={element} value={value} onChange={onChange} />
  }
  if (QUANTITY_TYPES.has(type)) {
    return <QuantityField element={element} value={value} onChange={onChange} />
  }
  if (type === 'HumanName') {
    return <HumanNameField element={element} value={value} onChange={onChange} />
  }
  if (type === 'Address') {
    return <AddressField element={element} value={value} onChange={onChange} />
  }
  if (type === 'ContactPoint') {
    return <ContactPointField element={element} value={value} onChange={onChange} />
  }
  if (type === 'Identifier') {
    return <IdentifierField element={element} value={value} onChange={onChange} />
  }

  // Generic complex type with children
  if (element.children && element.children.length > 0) {
    return <GenericComplexField element={element} value={value} onChange={onChange} depth={depth} />
  }

  // All other types (primitives + unknown) — PrimitiveField handles the rendering
  return <PrimitiveField element={element} value={value} onChange={onChange} />
}

function getDefaultValue(element: ElementMetadata): unknown {
  const type = element.type
  if (type === 'boolean') return false
  // Specialized complex types
  if (type === 'CodeableConcept') return { coding: [{ system: '', code: '', display: '' }] }
  if (type === 'Coding') return { system: '', code: '', display: '' }
  if (type === 'Period') return { start: '', end: '' }
  if (QUANTITY_TYPES.has(type)) return { value: 0, unit: '' }
  if (type === 'Reference') return { reference: '' }
  if (type === 'Identifier') return { system: '', value: '' }
  if (type === 'HumanName') return { family: '', given: [] }
  if (type === 'ContactPoint') return { value: '' }
  if (type === 'Address') return { line: [], city: '' }
  // Complex with children → empty object
  if (element.children?.length > 0) return {}
  // Primitives → empty string
  return ''
}
