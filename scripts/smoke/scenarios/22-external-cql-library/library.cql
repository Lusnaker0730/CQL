library SmokeExternalUtil version '1.0.0'

using FHIR version '4.0.1'

include FHIRHelpers version '4.0.1'

context Patient

define "IsAdultAtPeriodEnd":
  AgeInYearsAt(@2024-06-30) >= 18

define "IsSeniorAtPeriodEnd":
  AgeInYearsAt(@2024-06-30) >= 65
