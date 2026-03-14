<#list clauses as clause><#if clause?is_first>if ${clause.condition} then '${clause.thenClause}'<#else>
  else if ${clause.condition} then '${clause.thenClause}'</#if></#list>
<#if elseClause?has_content>  else '${elseClause}'<#else>  else null</#if>