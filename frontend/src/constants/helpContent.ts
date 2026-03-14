// Help content keys mapped to i18n translation keys in common.json under "help.*"
// Components should use t('help.editor.translate') etc. directly.
// This file is kept for backward compatibility with components that import helpContent.
// They should migrate to useTranslation() over time.

export const helpContent = {
  editor: {
    translate: 'help.editor.translate',
    save: 'help.editor.save',
    export: 'help.editor.export',
    import: 'help.editor.import',
  },
  cds: {
    hookType: 'help.cds.hookType',
    prefetch: 'help.cds.prefetch',
  },
  measures: {
    scoringType: 'help.measures.scoringType',
    details: 'help.measures.details',
    cql: 'help.measures.cql',
    populationCriteria: 'help.measures.populationCriteria',
    evaluate: 'help.measures.evaluate',
    testCases: 'help.measures.testCases',
    reports: 'help.measures.reports',
    riskAdjustment: 'help.measures.riskAdjustment',
    supplementalData: 'help.measures.supplementalData',
    observations: 'help.measures.observations',
    dataRequirements: 'help.measures.dataRequirements',
    versionManagement: 'help.measures.versionManagement',
  },
  fhir: {
    serverUrl: 'help.fhir.serverUrl',
    bulkExport: 'help.fhir.bulkExport',
  },
  quickStart: [
    { title: 'help.quickStart.gettingStarted.title', content: 'help.quickStart.gettingStarted.content' },
    { title: 'help.quickStart.cqlBasics.title', content: 'help.quickStart.cqlBasics.content' },
    { title: 'help.quickStart.cdsHooks.title', content: 'help.quickStart.cdsHooks.content' },
    { title: 'help.quickStart.qualityMeasures.title', content: 'help.quickStart.qualityMeasures.content' },
    { title: 'help.quickStart.fhirBrowser.title', content: 'help.quickStart.fhirBrowser.content' },
    { title: 'help.quickStart.keyboardShortcuts.title', content: 'help.quickStart.keyboardShortcuts.content' },
  ],
}
