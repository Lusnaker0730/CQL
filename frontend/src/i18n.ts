import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

// Only bundle English (fallback language) statically
import commonEn from './locales/en/common.json'
import validationEn from './locales/en/validation.json'
import editorEn from './locales/en/editor.json'
import builderEn from './locales/en/builder.json'
import measuresEn from './locales/en/measures.json'
import cdsEn from './locales/en/cds.json'
import fhirEn from './locales/en/fhir.json'
import terminologyEn from './locales/en/terminology.json'
import authoringEn from './locales/en/authoring.json'
import adminEn from './locales/en/admin.json'
import ecqmEn from './locales/en/ecqm.json'
import landingEn from './locales/en/landing.json'

const NAMESPACES = ['common', 'validation', 'editor', 'builder', 'measures', 'cds', 'fhir', 'terminology', 'authoring', 'admin', 'ecqm', 'landing'] as const

// Lazy-load non-English languages on demand
async function loadLanguage(lang: string) {
  if (lang === 'en') return // already bundled
  const modules = await Promise.all(
    NAMESPACES.map(async (ns) => {
      const mod = await import(`./locales/${lang}/${ns}.json`)
      return { ns, data: mod.default }
    })
  )
  for (const { ns, data } of modules) {
    i18n.addResourceBundle(lang, ns, data, true, true)
  }
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { common: commonEn, validation: validationEn, editor: editorEn, builder: builderEn, measures: measuresEn, cds: cdsEn, fhir: fhirEn, terminology: terminologyEn, authoring: authoringEn, admin: adminEn, ecqm: ecqmEn, landing: landingEn },
    },
    partialBundledLanguages: true,
    fallbackLng: 'en',
    supportedLngs: ['en', 'zh-TW'],
    defaultNS: 'common',
    interpolation: {
      escapeValue: false,
    },
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'cql-platform-language',
      caches: ['localStorage'],
    },
  })

// If detected language is not English, load it immediately
if (i18n.language && i18n.language !== 'en') {
  loadLanguage(i18n.language)
}

// Load language resources when user switches language
i18n.on('languageChanged', (lang) => {
  loadLanguage(lang)
})

export default i18n
