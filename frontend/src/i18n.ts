import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

import commonEn from './locales/en/common.json'
import commonZhTW from './locales/zh-TW/common.json'
import validationEn from './locales/en/validation.json'
import validationZhTW from './locales/zh-TW/validation.json'
import editorEn from './locales/en/editor.json'
import editorZhTW from './locales/zh-TW/editor.json'
import builderEn from './locales/en/builder.json'
import builderZhTW from './locales/zh-TW/builder.json'
import measuresEn from './locales/en/measures.json'
import measuresZhTW from './locales/zh-TW/measures.json'
import cdsEn from './locales/en/cds.json'
import cdsZhTW from './locales/zh-TW/cds.json'
import fhirEn from './locales/en/fhir.json'
import fhirZhTW from './locales/zh-TW/fhir.json'
import terminologyEn from './locales/en/terminology.json'
import terminologyZhTW from './locales/zh-TW/terminology.json'
import authoringEn from './locales/en/authoring.json'
import authoringZhTW from './locales/zh-TW/authoring.json'
import adminEn from './locales/en/admin.json'
import adminZhTW from './locales/zh-TW/admin.json'

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { common: commonEn, validation: validationEn, editor: editorEn, builder: builderEn, measures: measuresEn, cds: cdsEn, fhir: fhirEn, terminology: terminologyEn, authoring: authoringEn, admin: adminEn },
      'zh-TW': { common: commonZhTW, validation: validationZhTW, editor: editorZhTW, builder: builderZhTW, measures: measuresZhTW, cds: cdsZhTW, fhir: fhirZhTW, terminology: terminologyZhTW, authoring: authoringZhTW, admin: adminZhTW },
    },
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

export default i18n
