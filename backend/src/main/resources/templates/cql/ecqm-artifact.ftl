library ${safeName} version '${version}'

using FHIR version '${fhirVersion}'

<#list includes as inc>
${inc}
</#list>

<#if valueSets?has_content>
<#list valueSets as vs>
valueset "${vs.identifier}": '${vs.uri}'
</#list>

</#if>
<#if codeSystemEntries?has_content>
<#list codeSystemEntries as cs>
codesystem "${cs.name}": '${cs.id}'
</#list>

</#if>
<#if codes?has_content>
<#list codes as codeDecl>
${codeDecl}
</#list>

</#if>
parameter "Measurement Period" Interval<DateTime>
  default Interval[@2025-01-01T00:00:00.0, @2025-12-31T23:59:59.999]

<#if params?has_content>
<#list params as p>
<#if p.formattedDefault?has_content>
parameter "${p.name}" ${p.cqlType} default ${p.formattedDefault}
<#else>
parameter "${p.name}" ${p.cqlType}
</#if>
</#list>

</#if>
context Patient

<#list baseElements as be>
define "${be.name}":
  ${be.expression}

</#list>
<#list groupBlocks as block>
${block}
</#list>
<#list topStratifiers as strat>
<#if strat.description?has_content>
// ${strat.description}
</#if>
define "Stratifier ${strat.id}":
  ${strat.expression}

</#list>
<#list supplementalDefines as sde>
${sde}
</#list>
