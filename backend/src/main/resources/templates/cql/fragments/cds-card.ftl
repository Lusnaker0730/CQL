Tuple {
    summary: '${summary}'<#if detail?has_content>,
    detail: '${detail}'</#if>,
    indicator: '${indicator}'<#if sourceLabel?has_content>,
    sourceLabel: '${sourceLabel}'</#if><#if selectionBehavior?has_content>,
    selectionBehavior: '${selectionBehavior}'</#if><#if suggestions?has_content>,
    suggestions: {
<#list suggestions as sug>
      Tuple { label: '${sug.label}'<#if sug.isRecommended!false>, isRecommended: true</#if><#if sug.actions?has_content>, actions: {
<#list sug.actions as act>
        Tuple { type: '${act.type}'<#if act.description?has_content>, description: '${act.description}'</#if> }<#if act?has_next>,
</#if>
</#list>
      }</#if> }<#if sug?has_next>,
</#if>
</#list>
    }</#if>
  }