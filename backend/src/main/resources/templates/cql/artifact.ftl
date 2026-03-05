library ${safeName} version '${version}'

using FHIR version '${fhirVersion}'

<#list includes as inc>
${inc}
</#list>

<#if valueSets?has_content>
<#list valueSets as vs>
valueset "${vs}": '${vs}'
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
define "${defMeetsInclusion}":
  ${inclusionExpr}

define "${defMeetsExclusion}":
  ${exclusionExpr}

define "${defInPopulation}":
  "${defMeetsInclusion}" and not "${defMeetsExclusion}"

<#list subpopulations as sp>
define "${sp.name}":
  ${sp.expression}

</#list>
<#list recommendations as rec>
define "${rec.defName}":
  if ${rec.condition} then ${rec.value}
  else null

</#list>
<#if errorExpr?has_content>
define "${defErrors}":
  ${errorExpr}

</#if>
