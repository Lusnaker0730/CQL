library BMI_CDS version '1.0.0'

using FHIR version '4.0.1'

include FHIRHelpers version '4.0.1' called FHIRHelpers

codesystem "LOINC": 'http://loinc.org'

code "Body Weight": '29463-7' from "LOINC" display 'Body Weight'
code "Body Height": '8302-2' from "LOINC" display 'Body Height'

context Patient

define "Most Recent Weight":
  Last([Observation] O
    where exists(O.code.coding C where C.code = '29463-7')
    sort by effective.value
  )

define "Most Recent Height":
  Last([Observation] O
    where exists(O.code.coding C where C.code = '8302-2')
    sort by effective.value
  )

define "Weight in Kg":
    case
        when ("Most Recent Weight".value as Quantity).unit = 'kg' then ("Most Recent Weight".value as Quantity).value
        when ("Most Recent Weight".value as Quantity).unit = 'g' then ("Most Recent Weight".value as Quantity).value / 1000.0
        when ("Most Recent Weight".value as Quantity).unit = 'lbs' or ("Most Recent Weight".value as Quantity).unit = '[lb_av]' then ("Most Recent Weight".value as Quantity).value * 0.45359237
        else null
    end

define "Height in M":
    case
        when ("Most Recent Height".value as Quantity).unit = 'm' then ("Most Recent Height".value as Quantity).value
        when ("Most Recent Height".value as Quantity).unit = 'cm' then ("Most Recent Height".value as Quantity).value / 100.0
        when ("Most Recent Height".value as Quantity).unit = 'in' or ("Most Recent Height".value as Quantity).unit = '[in_i]' then ("Most Recent Height".value as Quantity).value * 0.0254
        else null
    end

define "BMI Value":
  if "Weight in Kg" is not null and "Height in M" is not null and "Height in M" > 0 then
    "Weight in Kg" / ("Height in M" * "Height in M")
  else
    null

define "BMI Classification":
  case
    when "BMI Value" is null then 'Unknown'
    when "BMI Value" < 18.5 then 'Underweight'
    when "BMI Value" between 18.5 and 24.9 then 'Normal'
    when "BMI Value" between 25.0 and 29.9 then 'Overweight'
    when "BMI Value" >= 30.0 then 'Obese'
    else 'Unknown'
  end

define "Recommendation":
    case
        when "BMI Classification" = 'Underweight' then 'Assess nutritional status and consider nutritional counseling.'
        when "BMI Classification" = 'Overweight' then 'Recommend lifestyle modifications (diet, exercise).'
        when "BMI Classification" = 'Obese' then 'Recommend comprehensive weight management program.'
        else null
    end

define "Indicator":
    case
        when "BMI Classification" = 'Overweight' or "BMI Classification" = 'Underweight' then 'warning'
        when "BMI Classification" = 'Obese' then 'critical'
        else 'info'
    end

define "Summary":
    'BMI Status: ' + Coalesce("BMI Classification", 'Unknown') + ' (BMI: ' + Coalesce(ToString(Round("BMI Value", 1)), 'N/A') + ')'

define "Detail":
    'Based on recent observations, the patient is classified as ' + Coalesce("BMI Classification", 'Unknown') + '. ' + Coalesce("Recommendation", '')

// Standardized Output for CDS Service
define "CDS_Card":
  Tuple {
    uuid: 'bmi-card',
    summary: "Summary",
    detail: "Detail",
    indicator: "Indicator",
    sourceLabel: 'BMI Classification Service'
  }
