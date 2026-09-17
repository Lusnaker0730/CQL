import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../test/test-utils'
import TemplateCatalogPage from '../TemplateCatalogPage'

// The catalog surfaces the bundled TWCDI templates. i18n returns keys in tests, but the
// template names/categories/descriptions are real data from twcdiTemplates.json.
describe('TemplateCatalogPage', () => {
  it('renders the catalog title and a known template', () => {
    render(<TemplateCatalogPage />)
    // i18n key (test-utils has no I18nextProvider, so t() returns the key)
    expect(screen.getByText('catalog.title')).toBeInTheDocument()
    // Real template data from the bundled JSON
    expect(screen.getByText('TWCDI Overview')).toBeInTheDocument()
  })

  it('groups templates by category', () => {
    render(<TemplateCatalogPage />)
    // "General" is a category present in the bundled TWCDI templates.
    expect(screen.getByText('General')).toBeInTheDocument()
  })
})
