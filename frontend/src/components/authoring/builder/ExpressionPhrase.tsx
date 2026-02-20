import { Box, Typography, Chip } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import type { ElementInstance } from '../../../types/authoring'

interface ExpressionPhraseProps {
  element: ElementInstance
  variant?: 'inline' | 'full'
}

export default function ExpressionPhrase({ element, variant = 'inline' }: ExpressionPhraseProps) {
  const { t } = useTranslation('authoring')
  const phrase = buildPhrase(element, t)

  if (variant === 'full') {
    return (
      <Box
        sx={{
          borderLeft: 3,
          borderColor: 'primary.main',
          pl: 2,
          py: 1,
          my: 1,
          backgroundColor: 'action.hover',
          borderRadius: '0 4px 4px 0',
        }}
      >
        {phrase}
      </Box>
    )
  }

  return (
    <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
      {phraseToString(element, t)}
    </Typography>
  )
}

function buildPhrase(element: ElementInstance, t: TFunction) {
  switch (element.type) {
    case 'AgeRange':
      return buildAgeRangePhrase(element, t)
    case 'Gender':
      return buildGenderPhrase(element, t)
    default:
      if (element.type?.startsWith('Generic')) {
        return buildGenericResourcePhrase(element, t)
      }
      return buildDefaultPhrase(element)
  }
}

function buildAgeRangePhrase(element: ElementInstance, t: TFunction) {
  const minAge = getFieldValue(element, 'min_age')
  const maxAge = getFieldValue(element, 'max_age')
  const unit = getFieldValue(element, 'unit_of_time') || 'year'
  const unitLabel = unit.endsWith('s') ? unit : unit + 's'

  const parts: React.ReactNode[] = []
  parts.push(<span key="prefix" dangerouslySetInnerHTML={{ __html: t('expression.ageIs') }} />)

  if (minAge && maxAge) {
    parts.push(<span key="between">{t('expression.between')}</span>)
    parts.push(<ValueChip key="min" label={`${minAge} ${unitLabel}`} />)
    parts.push(<span key="and"> and </span>)
    parts.push(<ValueChip key="max" label={`${maxAge} ${unitLabel}`} />)
  } else if (minAge) {
    parts.push(<span key="gte">{t('expression.atLeast')}</span>)
    parts.push(<ValueChip key="min" label={`${minAge} ${unitLabel}`} />)
  } else if (maxAge) {
    parts.push(<span key="lte">{t('expression.atMost')}</span>)
    parts.push(<ValueChip key="max" label={`${maxAge} ${unitLabel}`} />)
  } else {
    parts.push(<span key="any">{t('expression.anyAge')}</span>)
  }

  const modifierPhrase = buildModifierPhrase(element)
  if (modifierPhrase) {
    parts.push(<span key="mod"> {modifierPhrase}</span>)
  }

  return (
    <Typography variant="body2" component="div" sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 0.5 }}>
      {parts}
    </Typography>
  )
}

function buildGenderPhrase(element: ElementInstance, t: TFunction) {
  const gender = getFieldValue(element, 'gender')

  return (
    <Typography variant="body2" component="div" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
      <span dangerouslySetInnerHTML={{ __html: t('expression.genderIs') }} />
      {gender ? <ValueChip label={gender} /> : <Box component="span" sx={{ fontStyle: 'italic', color: 'text.disabled' }}>{t('expression.notSet')}</Box>}
    </Typography>
  )
}

function buildGenericResourcePhrase(element: ElementInstance, t: TFunction) {
  const resourceType = element.type.replace('Generic', '').replace('_vsac', '')
  const vsacField = element.fields?.find((f) => f.type?.endsWith('_vsac'))

  const valueSets = vsacField?.valueSets || []
  const codes = vsacField?.codes || []
  const legacyValue = typeof vsacField?.value === 'string' ? vsacField.value : ''

  const parts: React.ReactNode[] = []
  parts.push(
    <span key="prefix">
      <strong>{resourceType}</strong>
      {valueSets.length > 0 || codes.length > 0 || legacyValue ? t('expression.matching') : ''}
    </span>
  )

  if (valueSets.length > 0) {
    valueSets.forEach((vs, i) => {
      if (i > 0) parts.push(<span key={`vs-sep-${i}`}>, </span>)
      parts.push(<ValueChip key={`vs-${i}`} label={vs.name} />)
    })
  }

  if (codes.length > 0) {
    if (valueSets.length > 0) parts.push(<span key="code-sep"> or </span>)
    codes.forEach((c, i) => {
      if (i > 0) parts.push(<span key={`c-sep-${i}`}>, </span>)
      parts.push(<ValueChip key={`code-${i}`} label={c.display || `${c.codeSystem.name} ${c.code}`} />)
    })
  }

  if (!valueSets.length && !codes.length && legacyValue) {
    parts.push(<ValueChip key="legacy" label={legacyValue} />)
  }

  const modifierPhrase = buildModifierPhrase(element)
  if (modifierPhrase) {
    parts.push(<span key="mod"> {modifierPhrase}</span>)
  }

  return (
    <Typography variant="body2" component="div" sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 0.5 }}>
      {parts}
    </Typography>
  )
}

function buildDefaultPhrase(element: ElementInstance) {
  const nameField = element.fields?.find((f) => f.id === 'element_name')
  const displayName = (nameField?.value as string) || element.name

  const modifierPhrase = buildModifierPhrase(element)

  return (
    <Typography variant="body2" component="div">
      <strong>{displayName}</strong>
      {modifierPhrase && <span> {modifierPhrase}</span>}
    </Typography>
  )
}

function buildModifierPhrase(element: ElementInstance): string | null {
  if (!element.modifiers || element.modifiers.length === 0) return null
  const names = element.modifiers.map((m) => m.name)
  return `(${names.join(' \u2192 ')})`
}

function ValueChip({ label }: { label: string }) {
  return (
    <Chip
      label={label}
      size="small"
      sx={{
        backgroundColor: 'primary.main',
        color: 'primary.contrastText',
        fontWeight: 600,
        fontSize: '0.8rem',
        height: 26,
      }}
    />
  )
}

/** Flat string version for collapsed view */
function phraseToString(element: ElementInstance, t: TFunction): string {
  switch (element.type) {
    case 'AgeRange': {
      const min = getFieldValue(element, 'min_age')
      const max = getFieldValue(element, 'max_age')
      const unit = getFieldValue(element, 'unit_of_time') || 'year'
      const u = unit.endsWith('s') ? unit : unit + 's'
      if (min && max) return `${stripHtml(t('expression.ageIs'))}${t('expression.between')}${min} ${u} and ${max} ${u}`
      if (min) return `${stripHtml(t('expression.ageIs'))}${t('expression.atLeast')}${min} ${u}`
      if (max) return `${stripHtml(t('expression.ageIs'))}${t('expression.atMost')}${max} ${u}`
      return t('expression.ageRangeNotConfigured')
    }
    case 'Gender': {
      const g = getFieldValue(element, 'gender')
      return g ? `${stripHtml(t('expression.genderIs'))}${g}` : t('expression.genderNotSet')
    }
    default: {
      const rt = element.type?.startsWith('Generic')
        ? element.type.replace('Generic', '').replace('_vsac', '')
        : element.name
      const mods = element.modifiers?.length ? ` [${element.modifiers.map((m) => m.name).join(' \u2192 ')}]` : ''
      return `${rt}${mods}`
    }
  }
}

/** Strip HTML tags from a string for use in plain-text contexts */
function stripHtml(html: string): string {
  return html.replace(/<[^>]*>/g, '')
}

function getFieldValue(element: ElementInstance, fieldId: string): string {
  const field = element.fields?.find((f) => f.id === fieldId)
  return field?.value != null ? String(field.value) : ''
}
