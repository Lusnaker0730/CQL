import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  TextField,
  MenuItem,
  ListSubheader,
  InputAdornment,
  Typography,
} from '@mui/material'
import { Search as SearchIcon } from '@mui/icons-material'
import TypeChip from './TypeChip'
import { extractCqlName } from '../../utils/cqlNames'

interface ElementOption {
  value: string
  label: string
  resultType?: string
  category: 'definition' | 'parameter' | 'function' | 'builtin'
}

interface ElementSelectFieldProps {
  value: string
  onChange: (value: string) => void
  expressions?: { name: string; resultType?: string }[]
  parameters?: string[]
  functions?: { name: string; resultType?: string }[]
  label?: string
  size?: 'small' | 'medium'
  /** Include built-in age/date functions */
  includeBuiltins?: boolean
  /** Filter to specific return types (e.g. 'System.Boolean') */
  filterType?: string
}

const BUILTINS: ElementOption[] = [
  { value: 'AgeInYearsAt(Today())', label: 'AgeInYearsAt(Today())', resultType: 'System.Integer', category: 'builtin' },
  { value: 'AgeInYears()', label: 'AgeInYears()', resultType: 'System.Integer', category: 'builtin' },
  { value: 'AgeInMonths()', label: 'AgeInMonths()', resultType: 'System.Integer', category: 'builtin' },
  { value: 'Today()', label: 'Today()', resultType: 'System.Date', category: 'builtin' },
  { value: 'Now()', label: 'Now()', resultType: 'System.DateTime', category: 'builtin' },
  { value: 'true', label: 'true', resultType: 'System.Boolean', category: 'builtin' },
  { value: 'false', label: 'false', resultType: 'System.Boolean', category: 'builtin' },
  { value: 'null', label: 'null', resultType: 'System.Any', category: 'builtin' },
]

export default function ElementSelectField({
  value,
  onChange,
  expressions = [],
  parameters = [],
  functions = [],
  label,
  size = 'small',
  includeBuiltins = true,
  filterType,
}: ElementSelectFieldProps) {
  const { t } = useTranslation('builder')
  const [search, setSearch] = useState('')

  const allOptions = useMemo(() => {
    const opts: ElementOption[] = []

    for (const expr of expressions) {
      opts.push({
        value: `"${expr.name}"`,
        label: expr.name,
        resultType: expr.resultType,
        category: 'definition',
      })
    }

    for (const p of parameters) {
      const name = extractCqlName(p)
      opts.push({
        value: `"${name}"`,
        label: name,
        resultType: 'Parameter',
        category: 'parameter',
      })
    }

    for (const f of functions) {
      opts.push({
        value: `"${f.name}"()`,
        label: `${f.name}()`,
        resultType: f.resultType,
        category: 'function',
      })
    }

    if (includeBuiltins) {
      opts.push(...BUILTINS)
    }

    return opts
  }, [expressions, parameters, functions, includeBuiltins])

  const filteredOptions = useMemo(() => {
    let opts = allOptions
    if (filterType) {
      opts = opts.filter((o) => !o.resultType || o.resultType.includes(filterType))
    }
    if (search) {
      const q = search.toLowerCase()
      opts = opts.filter((o) => o.label.toLowerCase().includes(q))
    }
    return opts
  }, [allOptions, filterType, search])

  // Group by category
  const grouped = useMemo(() => {
    const map = new Map<string, ElementOption[]>()
    for (const opt of filteredOptions) {
      const list = map.get(opt.category) || []
      list.push(opt)
      map.set(opt.category, list)
    }
    return map
  }, [filteredOptions])

  const categoryLabels: Record<string, string> = {
    definition: t('elementSelect.definitions'),
    parameter: t('elementSelect.parameters'),
    function: t('elementSelect.functions'),
    builtin: t('elementSelect.builtIn'),
  }

  return (
    <TextField
      select
      size={size}
      label={label || t('expression.select')}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      SelectProps={{ displayEmpty: true }}
      InputLabelProps={{ shrink: true }}
    >
      {/* Search input at the top of the dropdown */}
      <ListSubheader sx={{ p: 0.5 }}>
        <TextField
          size="small"
          fullWidth
          placeholder={t('elementSelect.searchPlaceholder')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => e.stopPropagation()}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon sx={{ fontSize: 16 }} />
              </InputAdornment>
            ),
          }}
          sx={{ '& .MuiInputBase-input': { fontSize: '0.8rem' } }}
        />
      </ListSubheader>

      <MenuItem value="" disabled>
        <em>{t('expression.select')}</em>
      </MenuItem>

      {['definition', 'parameter', 'function', 'builtin'].map((cat) => {
        const items = grouped.get(cat)
        if (!items || items.length === 0) return null
        return [
          <ListSubheader
            key={`header-${cat}`}
            sx={{
              fontSize: '0.7rem',
              fontWeight: 700,
              lineHeight: '28px',
              bgcolor: 'background.paper',
              color: 'text.secondary',
            }}
          >
            {categoryLabels[cat]}
          </ListSubheader>,
          ...items.map((opt) => (
            <MenuItem
              key={opt.value}
              value={opt.value}
              sx={{ fontSize: '0.8rem', py: 0.5, gap: 1 }}
            >
              <Typography
                variant="body2"
                sx={{ fontFamily: 'monospace', fontSize: '0.75rem', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis' }}
              >
                {opt.label}
              </Typography>
              <TypeChip resultType={opt.resultType} />
            </MenuItem>
          )),
        ]
      })}
    </TextField>
  )
}
