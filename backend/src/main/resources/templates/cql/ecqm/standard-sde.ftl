<#if sdeType == "ethnicity">define "${sdeName}":
  SDE."SDE Ethnicity"
<#elseif sdeType == "race">define "${sdeName}":
  SDE."SDE Race"
<#elseif sdeType == "sex">define "${sdeName}":
  Patient.gender
<#elseif sdeType == "payer">define "${sdeName}":
  [Coverage: "${oid}"]
<#else>define "${sdeName}":
  null
</#if>