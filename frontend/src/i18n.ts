import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

// Bundle both languages statically (zh-TW is the default)
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
import patientGeneratorEn from './locales/en/patientGenerator.json'

import commonZhTW from './locales/zh-TW/common.json'
import validationZhTW from './locales/zh-TW/validation.json'
import editorZhTW from './locales/zh-TW/editor.json'
import builderZhTW from './locales/zh-TW/builder.json'
import measuresZhTW from './locales/zh-TW/measures.json'
import cdsZhTW from './locales/zh-TW/cds.json'
import fhirZhTW from './locales/zh-TW/fhir.json'
import terminologyZhTW from './locales/zh-TW/terminology.json'
import authoringZhTW from './locales/zh-TW/authoring.json'
import adminZhTW from './locales/zh-TW/admin.json'
import ecqmZhTW from './locales/zh-TW/ecqm.json'
import landingZhTW from './locales/zh-TW/landing.json'
import patientGeneratorZhTW from './locales/zh-TW/patientGenerator.json'

// Both languages statically bundled — zh-TW is the default

i18n
  .use(initReactI18next)
  .init({
    resources: {
      en: { common: commonEn, validation: validationEn, editor: editorEn, builder: builderEn, measures: measuresEn, cds: cdsEn, fhir: fhirEn, terminology: terminologyEn, authoring: authoringEn, admin: adminEn, ecqm: ecqmEn, landing: landingEn, patientGenerator: patientGeneratorEn },
      'zh-TW': { common: commonZhTW, validation: validationZhTW, editor: editorZhTW, builder: builderZhTW, measures: measuresZhTW, cds: cdsZhTW, fhir: fhirZhTW, terminology: terminologyZhTW, authoring: authoringZhTW, admin: adminZhTW, ecqm: ecqmZhTW, landing: landingZhTW, patientGenerator: patientGeneratorZhTW },
    },
    lng: localStorage.getItem('cql-platform-language') || 'zh-TW',
    fallbackLng: 'en',
    supportedLngs: ['en', 'zh-TW'],
    defaultNS: 'common',
    interpolation: {
      escapeValue: false,
    },
  })

// Persist language choice when user switches
i18n.on('languageChanged', (lang) => {
  localStorage.setItem('cql-platform-language', lang)
})

export default i18n
