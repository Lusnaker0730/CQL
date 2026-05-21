import FieldWrapper from './FieldWrapper'
import ElementField from './ElementField'
import { asObject } from '../../utils/fhirGuards'
import type { ElementMetadata } from '../../types'

interface GenericComplexFieldProps {
  element: ElementMetadata
  value: unknown
  onChange: (value: unknown) => void
  depth: number
}

export default function GenericComplexField({ element, value, onChange, depth }: GenericComplexFieldProps) {
  const obj = asObject(value)

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
    <FieldWrapper name={element.name} isRequired={element.isRequired}>
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
    </FieldWrapper>
  )
}
