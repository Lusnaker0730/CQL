import { describe, it, expect } from 'vitest'
import {
  validateRequired,
  validateUsername,
  validatePassword,
  isValidPassword,
  validateFhirUrl,
  validateJson,
  validateDateRange,
  generateId,
  safeParseJson,
  getStoredUsername,
} from '../validation'

describe('validateRequired', () => {
  it('should return error when value is empty', () => {
    expect(validateRequired('', 'Name')).toBe('Name is required')
  })

  it('should return error when value is whitespace', () => {
    expect(validateRequired('   ', 'Name')).toBe('Name is required')
  })

  it('should return null when value is provided', () => {
    expect(validateRequired('hello', 'Name')).toBeNull()
  })
})

describe('validateUsername', () => {
  it('should return error when empty', () => {
    expect(validateUsername('')).toBe('Username is required')
  })

  it('should return error when too short', () => {
    expect(validateUsername('ab')).toBe('Username must be at least 3 characters')
  })

  it('should return error when too long', () => {
    expect(validateUsername('a'.repeat(51))).toBe('Username must be at most 50 characters')
  })

  it('should return error for invalid characters', () => {
    expect(validateUsername('user@name')).toBe('Username can only contain letters, numbers, hyphens, and underscores')
  })

  it('should accept valid username with letters and numbers', () => {
    expect(validateUsername('user123')).toBeNull()
  })

  it('should accept username with hyphens and underscores', () => {
    expect(validateUsername('user-name_1')).toBeNull()
  })
})

describe('validatePassword', () => {
  it('should return error when empty', () => {
    expect(validatePassword('')).toBe('Password is required')
  })

  it('should return error when too short', () => {
    expect(validatePassword('Abc1')).toBe('Password must be at least 8 characters')
  })

  it('should return error when no uppercase', () => {
    expect(validatePassword('abcdefg1')).toBe('Password must contain at least one uppercase letter')
  })

  it('should return error when no lowercase', () => {
    expect(validatePassword('ABCDEFG1')).toBe('Password must contain at least one lowercase letter')
  })

  it('should return error when no number', () => {
    expect(validatePassword('Abcdefgh')).toBe('Password must contain at least one number')
  })

  it('should accept valid password', () => {
    expect(validatePassword('Abcdefg1')).toBeNull()
  })
})

describe('isValidPassword', () => {
  it('should return false for short password', () => {
    expect(isValidPassword('Ab1')).toBe(false)
  })

  it('should return false without uppercase', () => {
    expect(isValidPassword('abcdefg1')).toBe(false)
  })

  it('should return true for valid password', () => {
    expect(isValidPassword('Abcdefg1')).toBe(true)
  })
})

describe('validateFhirUrl', () => {
  it('should return error when empty', () => {
    expect(validateFhirUrl('')).toBe('FHIR server URL is required')
  })

  it('should return error for invalid URL', () => {
    expect(validateFhirUrl('not-a-url')).toBe('Invalid URL format')
  })

  it('should return error for non-http protocol', () => {
    expect(validateFhirUrl('ftp://example.com')).toBe('URL must start with http:// or https://')
  })

  it('should accept http URL', () => {
    expect(validateFhirUrl('http://hapi.fhir.org/baseR4')).toBeNull()
  })

  it('should accept https URL', () => {
    expect(validateFhirUrl('https://fhir.example.com/r4')).toBeNull()
  })
})

describe('validateJson', () => {
  it('should return null for empty string', () => {
    expect(validateJson('')).toBeNull()
  })

  it('should return null for whitespace', () => {
    expect(validateJson('   ')).toBeNull()
  })

  it('should return null for valid JSON', () => {
    expect(validateJson('{"key": "value"}')).toBeNull()
  })

  it('should return error for invalid JSON', () => {
    expect(validateJson('{bad json}')).toBe('Invalid JSON format')
  })
})

describe('validateDateRange', () => {
  it('should return null when start is empty', () => {
    expect(validateDateRange('', '2024-12-31')).toBeNull()
  })

  it('should return null when end is empty', () => {
    expect(validateDateRange('2024-01-01', '')).toBeNull()
  })

  it('should return null when start is before end', () => {
    expect(validateDateRange('2024-01-01', '2024-12-31')).toBeNull()
  })

  it('should return error when start is after end', () => {
    expect(validateDateRange('2024-12-31', '2024-01-01')).toBe('Start date must be before end date')
  })
})

describe('generateId', () => {
  it('should return a string', () => {
    expect(typeof generateId()).toBe('string')
  })

  it('should return unique values', () => {
    const id1 = generateId()
    const id2 = generateId()
    expect(id1).not.toBe(id2)
  })
})

describe('safeParseJson', () => {
  it('should return fallback for null', () => {
    expect(safeParseJson(null, 'default')).toBe('default')
  })

  it('should parse valid JSON', () => {
    expect(safeParseJson('{"a":1}', {})).toEqual({ a: 1 })
  })

  it('should return fallback for invalid JSON', () => {
    expect(safeParseJson('{bad}', [])).toEqual([])
  })
})

describe('getStoredUsername', () => {
  it('should return "anonymous" when no stored user', () => {
    expect(getStoredUsername()).toBe('anonymous')
  })

  it('should return stored username', () => {
    localStorage.setItem('user', JSON.stringify({ username: 'testuser' }))
    expect(getStoredUsername()).toBe('testuser')
  })
})
