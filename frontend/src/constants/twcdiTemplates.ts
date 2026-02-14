import data from './twcdiTemplates.json'

export interface TwcdiTemplate {
  name: string
  category: string
  description: string
  cql: string
}

export const TWCDI_TEMPLATES: TwcdiTemplate[] = data

// Group templates by category for UI display
export function getTemplatesByCategory(): Map<string, TwcdiTemplate[]> {
  const map = new Map<string, TwcdiTemplate[]>()
  for (const template of TWCDI_TEMPLATES) {
    const list = map.get(template.category) || []
    list.push(template)
    map.set(template.category, list)
  }
  return map
}
