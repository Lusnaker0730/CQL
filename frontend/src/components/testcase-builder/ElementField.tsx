import { lazy, Suspense, useState } from 'react'
import { Box, Typography, useTheme, CircularProgress, Alert } from '@mui/material'
import { useTranslation } from 'react-i18next'
import PrimitiveField from './PrimitiveField'

const Editor = lazy(() => import('@monaco-editor/react'))
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
import { QUANTITY_TYPES } from '../../constants/fhirTypes'
import { getDefaultValue } from '../../utils/fhirDefaults'
import type { ElementMetadata } from '../../types'

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
  const { t } = useTranslation('measures')
  const theme = useTheme()
  const [parseError, setParseError] = useState<string | null>(null)

  // Deep fallback: render inline JSON editor
  if (depth >= 3) {
    return (
      <Box sx={{ mb: 1 }}>
        <Typography
          variant="caption"
          sx={{
            fontWeight: 600,
            color: "text.secondary",
            display: 'block',
            mb: 0.5
          }}>
          {element.name} {t('testCaseBuilder.fields.jsonSuffix')}
        </Typography>
        <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
          <Suspense fallback={<Box sx={{ height: 100, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><CircularProgress size={20} /></Box>}>
            <Editor
              height="100px"
              language="json"
              theme={theme.palette.mode === 'dark' ? 'vs-dark' : 'light'}
              value={value !== undefined ? JSON.stringify(value, null, 2) : ''}
              onChange={(v) => {
                if (!v) {
                  setParseError(null)
                  onChange(undefined)
                  return
                }
                try {
                  onChange(JSON.parse(v))
                  setParseError(null)
                } catch (e) {
                  // Surface parse errors so users know edits aren't being committed.
                  setParseError(e instanceof Error ? e.message : String(e))
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
          </Suspense>
        </Box>
        {parseError && (
          <Alert severity="error" variant="outlined" sx={{ mt: 0.5, py: 0, fontSize: '0.7rem' }}>
            {t('testCaseBuilder.fields.jsonParseError', { message: parseError })}
          </Alert>
        )}
      </Box>
    );
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
  if ((element.children?.length ?? 0) > 0) {
    return <GenericComplexField element={element} value={value} onChange={onChange} depth={depth} />
  }

  // All other types (primitives + unknown) — PrimitiveField handles the rendering
  return <PrimitiveField element={element} value={value} onChange={onChange} />
}
