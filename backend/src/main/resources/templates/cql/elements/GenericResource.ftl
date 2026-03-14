<#if queryParts?size == 1>${queryParts[0]}<#elseif (queryParts?size > 1)><#list queryParts as part>${part}<#if part?has_next> union
  </#if></#list><#else>[${resourceType}]</#if>