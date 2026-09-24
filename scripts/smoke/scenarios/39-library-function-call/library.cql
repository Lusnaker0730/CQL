library SmokeFnUtil version '1.0.0'

using FHIR version '4.0.1'

include FHIRHelpers version '4.0.1'

context Patient

// PAT-237 smoke: functions the builder calls with author-supplied arguments.

define function "AgeAtLeast"(threshold Integer):
  AgeInYearsAt(@2024-06-30) >= threshold

define function "AgeBetweenAsOf"(low Integer, high Integer, asOf Date):
  AgeInYearsAt(asOf) between low and high

define function "AtLeastAtPeriodEnd"(period Interval<DateTime>, threshold Integer):
  AgeInYearsAt(end of period) >= threshold

define function "Identity"(flag Boolean):
  flag
