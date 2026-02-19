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

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { common: commonEn, validation: validationEn, editor: editorEn, builder: builderEn, measures: measuresEn },
      'zh-TW': { common: commonZhTW, validation: validationZhTW, editor: editorZhTW, builder: builderZhTW, measures: measuresZhTW },
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
