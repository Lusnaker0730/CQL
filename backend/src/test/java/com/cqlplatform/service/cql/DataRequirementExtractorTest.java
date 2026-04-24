package com.cqlplatform.service.cql;

import com.cqlplatform.model.measure.DataRequirementInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DataRequirementExtractorTest {

    private DataRequirementExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new DataRequirementExtractor();
    }

    @Test
    void extract_nullInput_shouldReturnEmptyList() {
        List<DataRequirementInfo> result = extractor.extract(null);
        assertThat(result).isEmpty();
    }

    @Test
    void extract_emptyString_shouldReturnEmptyList() {
        List<DataRequirementInfo> result = extractor.extract("");
        assertThat(result).isEmpty();
    }

    @Test
    void extract_blankString_shouldReturnEmptyList() {
        List<DataRequirementInfo> result = extractor.extract("   ");
        assertThat(result).isEmpty();
    }

    @Test
    void extract_invalidJson_shouldReturnEmptyList() {
        List<DataRequirementInfo> result = extractor.extract("not valid json");
        assertThat(result).isEmpty();
    }

    @Test
    void extract_noRetrieves_shouldReturnEmptyList() {
        String elmJson = """
                {
                  "library": {
                    "statements": {
                      "def": [
                        { "name": "IsAlive", "expression": { "type": "Literal", "value": "true" } }
                      ]
                    }
                  }
                }
                """;
        List<DataRequirementInfo> result = extractor.extract(elmJson);
        assertThat(result).isEmpty();
    }

    @Test
    void extract_singleRetrieveWithValueSet_shouldExtractCorrectly() {
        String elmJson = """
                {
                  "library": {
                    "valueSets": {
                      "def": [
                        { "name": "Diabetes", "id": "2.16.840.1.113883.3.464.1003.103.12.1001" }
                      ]
                    },
                    "statements": {
                      "def": [
                        {
                          "name": "DiabetesConditions",
                          "expression": {
                            "type": "Retrieve",
                            "dataType": "{http://hl7.org/fhir}Condition",
                            "codeProperty": "code",
                            "codes": {
                              "type": "ValueSetRef",
                              "name": "Diabetes"
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);

        assertThat(result).hasSize(1);
        DataRequirementInfo req = result.get(0);
        assertThat(req.getType()).isEqualTo("Condition");
        assertThat(req.getCodeFilter()).hasSize(1);
        assertThat(req.getCodeFilter().get(0).getPath()).isEqualTo("code");
        assertThat(req.getCodeFilter().get(0).getValueSet()).isEqualTo("2.16.840.1.113883.3.464.1003.103.12.1001");
        assertThat(req.getCodeFilter().get(0).getValueSetName()).isEqualTo("Diabetes");
    }

    @Test
    void extract_retrieveWithDateProperty_shouldIncludeDateFilter() {
        String elmJson = """
                {
                  "library": {
                    "statements": {
                      "def": [
                        {
                          "name": "RecentMeds",
                          "expression": {
                            "type": "Retrieve",
                            "dataType": "{http://hl7.org/fhir}MedicationRequest",
                            "codeProperty": "medication",
                            "dateProperty": "authoredOn",
                            "codes": {
                              "type": "ValueSetRef",
                              "name": "SomeMeds"
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);

        assertThat(result).hasSize(1);
        DataRequirementInfo req = result.get(0);
        assertThat(req.getType()).isEqualTo("MedicationRequest");
        assertThat(req.getDateFilter()).hasSize(1);
        assertThat(req.getDateFilter().get(0).getPath()).isEqualTo("authoredOn");
    }

    @Test
    void extract_duplicateRetrieves_shouldDeduplicate() {
        String elmJson = """
                {
                  "library": {
                    "valueSets": {
                      "def": [
                        { "name": "Diabetes", "id": "urn:oid:2.16.840.1.113883.3.464" }
                      ]
                    },
                    "statements": {
                      "def": [
                        {
                          "name": "Expr1",
                          "expression": {
                            "type": "Retrieve",
                            "dataType": "{http://hl7.org/fhir}Condition",
                            "codeProperty": "code",
                            "codes": { "type": "ValueSetRef", "name": "Diabetes" }
                          }
                        },
                        {
                          "name": "Expr2",
                          "expression": {
                            "type": "Retrieve",
                            "dataType": "{http://hl7.org/fhir}Condition",
                            "codeProperty": "code",
                            "codes": { "type": "ValueSetRef", "name": "Diabetes" }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);
        assertThat(result).hasSize(1);
    }

    @Test
    void extract_multipleDistinctRetrieves_shouldReturnAll() {
        String elmJson = """
                {
                  "library": {
                    "valueSets": {
                      "def": [
                        { "name": "Diabetes", "id": "oid:1" },
                        { "name": "LabTests", "id": "oid:2" }
                      ]
                    },
                    "statements": {
                      "def": [
                        {
                          "name": "Conditions",
                          "expression": {
                            "type": "Retrieve",
                            "dataType": "{http://hl7.org/fhir}Condition",
                            "codeProperty": "code",
                            "codes": { "type": "ValueSetRef", "name": "Diabetes" }
                          }
                        },
                        {
                          "name": "Observations",
                          "expression": {
                            "type": "Retrieve",
                            "dataType": "{http://hl7.org/fhir}Observation",
                            "codeProperty": "code",
                            "codes": { "type": "ValueSetRef", "name": "LabTests" }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(DataRequirementInfo::getType)
                .containsExactly("Condition", "Observation");
    }

    @Test
    void extract_retrieveWithoutCodeProperty_shouldReturnWithoutCodeFilter() {
        String elmJson = """
                {
                  "library": {
                    "statements": {
                      "def": [
                        {
                          "name": "AllPatients",
                          "expression": {
                            "type": "Retrieve",
                            "dataType": "{http://hl7.org/fhir}Patient"
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("Patient");
        assertThat(result.get(0).getCodeFilter()).isNull();
        assertThat(result.get(0).getDateFilter()).isNull();
    }

    @Test
    void extract_nestedRetrieveInQuery_shouldBeFound() {
        String elmJson = """
                {
                  "library": {
                    "statements": {
                      "def": [
                        {
                          "name": "FilteredConditions",
                          "expression": {
                            "type": "Query",
                            "source": [
                              {
                                "alias": "E",
                                "expression": {
                                  "type": "Retrieve",
                                  "dataType": "{http://hl7.org/fhir}Encounter"
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("Encounter");
    }

    @Test
    void extract_retrieveWithCodeRef_resolvesAgainstCodeDefs() {
        // PAT-116: CodeRef resolution. The ELM carries CodeRef { name: "BloodPressure" },
        // and the library defines code "BloodPressure": '85354-9' from "LOINC" with
        // codesystem "LOINC": 'http://loinc.org'. Extractor must yield the resolved
        // pair (code=85354-9, system=http://loinc.org), not the ref name.
        String elmJson = """
                {
                  "library": {
                    "codeSystems": {
                      "def": [
                        { "name": "LOINC", "id": "http://loinc.org" }
                      ]
                    },
                    "codes": {
                      "def": [
                        {
                          "name": "BloodPressure",
                          "id": "85354-9",
                          "display": "Blood pressure panel",
                          "codeSystem": { "name": "LOINC" }
                        }
                      ]
                    },
                    "statements": {
                      "def": [
                        {
                          "name": "SpecificObs",
                          "expression": {
                            "type": "Retrieve",
                            "dataType": "{http://hl7.org/fhir}Observation",
                            "codeProperty": "code",
                            "codes": {
                              "type": "ToList",
                              "operand": {
                                "type": "CodeRef",
                                "name": "BloodPressure"
                              }
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);
        assertThat(result).hasSize(1);
        var coding = result.get(0).getCodeFilter().get(0).getCode().get(0);
        assertThat(coding.getCode()).isEqualTo("85354-9");
        assertThat(coding.getSystem()).isEqualTo("http://loinc.org");
        assertThat(coding.getDisplay()).isEqualTo("Blood pressure panel");
    }

    @Test
    void extract_retrieveWithUnresolvableCodeRef_fallsBackToRefName() {
        // Fallback: when the ref name is not in library.codes.def (e.g. the code
        // is declared in an included external library we don't have ELM for),
        // preserve the ref name as the code — matches legacy behavior so downstream
        // consumers at least see a human-readable hint.
        String elmJson = """
                {
                  "library": {
                    "statements": {
                      "def": [
                        {
                          "name": "SpecificObs",
                          "expression": {
                            "type": "Retrieve",
                            "dataType": "{http://hl7.org/fhir}Observation",
                            "codeProperty": "code",
                            "codes": {
                              "type": "ToList",
                              "operand": {
                                "type": "CodeRef",
                                "name": "ExternalLibCode"
                              }
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);
        var coding = result.get(0).getCodeFilter().get(0).getCode().get(0);
        assertThat(coding.getCode()).isEqualTo("ExternalLibCode");
        assertThat(coding.getSystem()).isNull();
    }

    @Test
    void extract_retrieveWithCodeLiteral_resolvesSystemNameToUrl() {
        // The `Code` literal node has system: CodeSystemRef { name }; resolve to URL.
        String elmJson = """
                {
                  "library": {
                    "codeSystems": {
                      "def": [
                        { "name": "ICD10CM", "id": "http://hl7.org/fhir/sid/icd-10-cm" }
                      ]
                    },
                    "statements": {
                      "def": [
                        {
                          "name": "DiabetesObs",
                          "expression": {
                            "type": "Retrieve",
                            "dataType": "{http://hl7.org/fhir}Condition",
                            "codeProperty": "code",
                            "codes": {
                              "type": "List",
                              "element": [
                                {
                                  "type": "Code",
                                  "code": "E11",
                                  "display": "Type 2 diabetes mellitus",
                                  "system": { "type": "CodeSystemRef", "name": "ICD10CM" }
                                }
                              ]
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);
        var coding = result.get(0).getCodeFilter().get(0).getCode().get(0);
        assertThat(coding.getCode()).isEqualTo("E11");
        assertThat(coding.getSystem()).isEqualTo("http://hl7.org/fhir/sid/icd-10-cm");
        assertThat(coding.getDisplay()).isEqualTo("Type 2 diabetes mellitus");
    }

    @Test
    void extract_valueSetRefWithUnknownName_shouldUseNameAsValueSet() {
        String elmJson = """
                {
                  "library": {
                    "statements": {
                      "def": [
                        {
                          "name": "SomeExpr",
                          "expression": {
                            "type": "Retrieve",
                            "dataType": "{http://hl7.org/fhir}Procedure",
                            "codeProperty": "code",
                            "codes": {
                              "type": "ValueSetRef",
                              "name": "UnknownValueSet"
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);
        assertThat(result).hasSize(1);
        DataRequirementInfo req = result.get(0);
        assertThat(req.getCodeFilter().get(0).getValueSet()).isEqualTo("UnknownValueSet");
        assertThat(req.getCodeFilter().get(0).getValueSetName()).isEqualTo("UnknownValueSet");
    }

    // ===== New tests for Where clause extraction =====

    @Test
    void extract_queryWithExistsCodeSystem_shouldExtractCodeSystemFromWhere() {
        // Pattern: [Condition] C where exists(C.code.coding Y where Y.system = "http://hl7.org/fhir/sid/icd-10-cm")
        String elmJson = """
                {
                  "library": {
                    "codeSystems": {
                      "def": [
                        { "name": "ICD10CM", "id": "http://hl7.org/fhir/sid/icd-10-cm" }
                      ]
                    },
                    "statements": {
                      "def": [
                        {
                          "name": "DiabetesConditions",
                          "expression": {
                            "type": "Query",
                            "source": [
                              {
                                "alias": "C",
                                "expression": {
                                  "type": "Retrieve",
                                  "dataType": "{http://hl7.org/fhir}Condition"
                                }
                              }
                            ],
                            "where": {
                              "type": "Exists",
                              "operand": {
                                "type": "Query",
                                "source": [
                                  {
                                    "alias": "Y",
                                    "expression": {
                                      "type": "Property",
                                      "path": "coding",
                                      "source": {
                                        "type": "Property",
                                        "path": "code",
                                        "source": {
                                          "type": "AliasRef",
                                          "name": "C"
                                        }
                                      }
                                    }
                                  }
                                ],
                                "where": {
                                  "type": "Equal",
                                  "operand": [
                                    {
                                      "type": "Property",
                                      "path": "system",
                                      "source": {
                                        "type": "AliasRef",
                                        "name": "Y"
                                      }
                                    },
                                    {
                                      "type": "Literal",
                                      "valueType": "{urn:hl7-org:elm-types:r1}String",
                                      "value": "http://hl7.org/fhir/sid/icd-10-cm"
                                    }
                                  ]
                                }
                              }
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);

        assertThat(result).hasSize(1);
        DataRequirementInfo req = result.get(0);
        assertThat(req.getType()).isEqualTo("Condition");
        assertThat(req.getCodeFilter()).hasSize(1);
        assertThat(req.getCodeFilter().get(0).getPath()).isEqualTo("code");
        assertThat(req.getCodeFilter().get(0).getCodeSystemUrl()).isEqualTo("http://hl7.org/fhir/sid/icd-10-cm");
        assertThat(req.getCodeFilter().get(0).getCodeSystemName()).isEqualTo("ICD10CM");
    }

    @Test
    void extract_queryWithOverlapsDateFilter_shouldExtractDateFilter() {
        // Pattern: [Encounter] E where E.period overlaps "Measurement Period"
        String elmJson = """
                {
                  "library": {
                    "statements": {
                      "def": [
                        {
                          "name": "EncountersDuringMP",
                          "expression": {
                            "type": "Query",
                            "source": [
                              {
                                "alias": "E",
                                "expression": {
                                  "type": "Retrieve",
                                  "dataType": "{http://hl7.org/fhir}Encounter"
                                }
                              }
                            ],
                            "where": {
                              "type": "Overlaps",
                              "operand": [
                                {
                                  "type": "Property",
                                  "path": "period",
                                  "source": {
                                    "type": "AliasRef",
                                    "name": "E"
                                  }
                                },
                                {
                                  "type": "ParameterRef",
                                  "name": "Measurement Period"
                                }
                              ]
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);

        assertThat(result).hasSize(1);
        DataRequirementInfo req = result.get(0);
        assertThat(req.getType()).isEqualTo("Encounter");
        assertThat(req.getDateFilter()).hasSize(1);
        assertThat(req.getDateFilter().get(0).getPath()).isEqualTo("period");
    }

    @Test
    void extract_queryWithFunctionRefWrappedDateFilter_shouldExtractDateFilter() {
        // Pattern: NormalizeInterval(P.performed) overlaps "Measurement Period"
        String elmJson = """
                {
                  "library": {
                    "statements": {
                      "def": [
                        {
                          "name": "ProceduresDuringMP",
                          "expression": {
                            "type": "Query",
                            "source": [
                              {
                                "alias": "P",
                                "expression": {
                                  "type": "Retrieve",
                                  "dataType": "{http://hl7.org/fhir}Procedure"
                                }
                              }
                            ],
                            "where": {
                              "type": "Overlaps",
                              "operand": [
                                {
                                  "type": "FunctionRef",
                                  "name": "NormalizeInterval",
                                  "operand": [
                                    {
                                      "type": "Property",
                                      "path": "performed",
                                      "source": {
                                        "type": "AliasRef",
                                        "name": "P"
                                      }
                                    }
                                  ]
                                },
                                {
                                  "type": "ParameterRef",
                                  "name": "Measurement Period"
                                }
                              ]
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);

        assertThat(result).hasSize(1);
        DataRequirementInfo req = result.get(0);
        assertThat(req.getType()).isEqualTo("Procedure");
        assertThat(req.getDateFilter()).hasSize(1);
        assertThat(req.getDateFilter().get(0).getPath()).isEqualTo("performed");
    }

    @Test
    void extract_queryWithBothCodeSystemAndDateFilter_shouldExtractBoth() {
        // Combined pattern: bare Retrieve + exists(code system) + overlaps(date)
        String elmJson = """
                {
                  "library": {
                    "codeSystems": {
                      "def": [
                        { "name": "ATC", "id": "http://www.whocc.no/atc" }
                      ]
                    },
                    "statements": {
                      "def": [
                        {
                          "name": "MedsWithDateFilter",
                          "expression": {
                            "type": "Query",
                            "source": [
                              {
                                "alias": "M",
                                "expression": {
                                  "type": "Retrieve",
                                  "dataType": "{http://hl7.org/fhir}MedicationRequest"
                                }
                              }
                            ],
                            "where": {
                              "type": "And",
                              "operand": [
                                {
                                  "type": "Exists",
                                  "operand": {
                                    "type": "Query",
                                    "source": [
                                      {
                                        "alias": "Y",
                                        "expression": {
                                          "type": "Property",
                                          "path": "coding",
                                          "source": {
                                            "type": "Property",
                                            "path": "medication",
                                            "source": {
                                              "type": "AliasRef",
                                              "name": "M"
                                            }
                                          }
                                        }
                                      }
                                    ],
                                    "where": {
                                      "type": "Equal",
                                      "operand": [
                                        {
                                          "type": "Property",
                                          "path": "system",
                                          "source": {
                                            "type": "AliasRef",
                                            "name": "Y"
                                          }
                                        },
                                        {
                                          "type": "Literal",
                                          "valueType": "{urn:hl7-org:elm-types:r1}String",
                                          "value": "http://www.whocc.no/atc"
                                        }
                                      ]
                                    }
                                  }
                                },
                                {
                                  "type": "Overlaps",
                                  "operand": [
                                    {
                                      "type": "Property",
                                      "path": "authoredOn",
                                      "source": {
                                        "type": "AliasRef",
                                        "name": "M"
                                      }
                                    },
                                    {
                                      "type": "ParameterRef",
                                      "name": "Measurement Period"
                                    }
                                  ]
                                }
                              ]
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);

        assertThat(result).hasSize(1);
        DataRequirementInfo req = result.get(0);
        assertThat(req.getType()).isEqualTo("MedicationRequest");

        // Code filter with code system
        assertThat(req.getCodeFilter()).hasSize(1);
        assertThat(req.getCodeFilter().get(0).getPath()).isEqualTo("medication");
        assertThat(req.getCodeFilter().get(0).getCodeSystemUrl()).isEqualTo("http://www.whocc.no/atc");
        assertThat(req.getCodeFilter().get(0).getCodeSystemName()).isEqualTo("ATC");

        // Date filter
        assertThat(req.getDateFilter()).hasSize(1);
        assertThat(req.getDateFilter().get(0).getPath()).isEqualTo("authoredOn");
    }

    @Test
    void extract_mixedInlineAndWhereRetrieves_shouldExtractAll() {
        // One Retrieve with inline ValueSet, another bare Retrieve with Where clause
        String elmJson = """
                {
                  "library": {
                    "valueSets": {
                      "def": [
                        { "name": "Diabetes", "id": "urn:oid:2.16.840.1.113883.3.464" }
                      ]
                    },
                    "codeSystems": {
                      "def": [
                        { "name": "LOINC", "id": "http://loinc.org" }
                      ]
                    },
                    "statements": {
                      "def": [
                        {
                          "name": "InlineConditions",
                          "expression": {
                            "type": "Retrieve",
                            "dataType": "{http://hl7.org/fhir}Condition",
                            "codeProperty": "code",
                            "codes": { "type": "ValueSetRef", "name": "Diabetes" }
                          }
                        },
                        {
                          "name": "WhereClauseObs",
                          "expression": {
                            "type": "Query",
                            "source": [
                              {
                                "alias": "O",
                                "expression": {
                                  "type": "Retrieve",
                                  "dataType": "{http://hl7.org/fhir}Observation"
                                }
                              }
                            ],
                            "where": {
                              "type": "Exists",
                              "operand": {
                                "type": "Query",
                                "source": [
                                  {
                                    "alias": "Y",
                                    "expression": {
                                      "type": "Property",
                                      "path": "coding",
                                      "source": {
                                        "type": "Property",
                                        "path": "code",
                                        "source": {
                                          "type": "AliasRef",
                                          "name": "O"
                                        }
                                      }
                                    }
                                  }
                                ],
                                "where": {
                                  "type": "Equal",
                                  "operand": [
                                    {
                                      "type": "Property",
                                      "path": "system",
                                      "source": {
                                        "type": "AliasRef",
                                        "name": "Y"
                                      }
                                    },
                                    {
                                      "type": "Literal",
                                      "valueType": "{urn:hl7-org:elm-types:r1}String",
                                      "value": "http://loinc.org"
                                    }
                                  ]
                                }
                              }
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);

        assertThat(result).hasSize(2);

        // First: inline ValueSet Condition
        DataRequirementInfo condReq = result.stream()
                .filter(r -> "Condition".equals(r.getType()))
                .findFirst().orElseThrow();
        assertThat(condReq.getCodeFilter()).hasSize(1);
        assertThat(condReq.getCodeFilter().get(0).getValueSet()).isEqualTo("urn:oid:2.16.840.1.113883.3.464");

        // Second: Where-clause Observation with code system
        DataRequirementInfo obsReq = result.stream()
                .filter(r -> "Observation".equals(r.getType()))
                .findFirst().orElseThrow();
        assertThat(obsReq.getCodeFilter()).hasSize(1);
        assertThat(obsReq.getCodeFilter().get(0).getPath()).isEqualTo("code");
        assertThat(obsReq.getCodeFilter().get(0).getCodeSystemUrl()).isEqualTo("http://loinc.org");
        assertThat(obsReq.getCodeFilter().get(0).getCodeSystemName()).isEqualTo("LOINC");
    }

    @Test
    void extract_queryWithNoWhereClause_shouldStillExtractBareRetrieve() {
        // A Query with a bare Retrieve but no Where clause should still capture the Retrieve
        String elmJson = """
                {
                  "library": {
                    "statements": {
                      "def": [
                        {
                          "name": "AllEncounters",
                          "expression": {
                            "type": "Query",
                            "source": [
                              {
                                "alias": "E",
                                "expression": {
                                  "type": "Retrieve",
                                  "dataType": "{http://hl7.org/fhir}Encounter"
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("Encounter");
        assertThat(result.get(0).getCodeFilter()).isNull();
        assertThat(result.get(0).getDateFilter()).isNull();
    }

    @Test
    void extract_queryWithInlineCodesAndWhereDate_shouldNotOverrideInlineCodes() {
        // Retrieve with inline ValueSet + Where clause with date filter
        // Should keep the inline codes AND add the date filter
        String elmJson = """
                {
                  "library": {
                    "valueSets": {
                      "def": [
                        { "name": "HbA1c", "id": "urn:oid:hba1c" }
                      ]
                    },
                    "statements": {
                      "def": [
                        {
                          "name": "HbA1cDuringMP",
                          "expression": {
                            "type": "Query",
                            "source": [
                              {
                                "alias": "O",
                                "expression": {
                                  "type": "Retrieve",
                                  "dataType": "{http://hl7.org/fhir}Observation",
                                  "codeProperty": "code",
                                  "codes": { "type": "ValueSetRef", "name": "HbA1c" }
                                }
                              }
                            ],
                            "where": {
                              "type": "Overlaps",
                              "operand": [
                                {
                                  "type": "Property",
                                  "path": "effective",
                                  "source": {
                                    "type": "AliasRef",
                                    "name": "O"
                                  }
                                },
                                {
                                  "type": "ParameterRef",
                                  "name": "Measurement Period"
                                }
                              ]
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);

        assertThat(result).hasSize(1);
        DataRequirementInfo req = result.get(0);
        assertThat(req.getType()).isEqualTo("Observation");

        // Code filter should still have the inline ValueSet
        assertThat(req.getCodeFilter()).hasSize(1);
        assertThat(req.getCodeFilter().get(0).getValueSet()).isEqualTo("urn:oid:hba1c");

        // Date filter from Where clause
        assertThat(req.getDateFilter()).hasSize(1);
        assertThat(req.getDateFilter().get(0).getPath()).isEqualTo("effective");
    }

    @Test
    void extract_queryWithExistsAndCondition_shouldExtractCodeSystem() {
        // Where clause: exists(coding...) AND status = 'active' — should handle the And wrapper
        String elmJson = """
                {
                  "library": {
                    "codeSystems": {
                      "def": [
                        { "name": "ICD10CM", "id": "http://hl7.org/fhir/sid/icd-10-cm" }
                      ]
                    },
                    "statements": {
                      "def": [
                        {
                          "name": "ActiveConditions",
                          "expression": {
                            "type": "Query",
                            "source": [
                              {
                                "alias": "C",
                                "expression": {
                                  "type": "Retrieve",
                                  "dataType": "{http://hl7.org/fhir}Condition"
                                }
                              }
                            ],
                            "where": {
                              "type": "And",
                              "operand": [
                                {
                                  "type": "Exists",
                                  "operand": {
                                    "type": "Query",
                                    "source": [
                                      {
                                        "alias": "Y",
                                        "expression": {
                                          "type": "Property",
                                          "path": "coding",
                                          "source": {
                                            "type": "Property",
                                            "path": "code",
                                            "source": {
                                              "type": "AliasRef",
                                              "name": "C"
                                            }
                                          }
                                        }
                                      }
                                    ],
                                    "where": {
                                      "type": "And",
                                      "operand": [
                                        {
                                          "type": "Equal",
                                          "operand": [
                                            {
                                              "type": "Property",
                                              "path": "system",
                                              "source": {
                                                "type": "AliasRef",
                                                "name": "Y"
                                              }
                                            },
                                            {
                                              "type": "Literal",
                                              "valueType": "{urn:hl7-org:elm-types:r1}String",
                                              "value": "http://hl7.org/fhir/sid/icd-10-cm"
                                            }
                                          ]
                                        },
                                        {
                                          "type": "Equal",
                                          "operand": [
                                            {
                                              "type": "Property",
                                              "path": "code",
                                              "source": {
                                                "type": "AliasRef",
                                                "name": "Y"
                                              }
                                            },
                                            {
                                              "type": "Literal",
                                              "valueType": "{urn:hl7-org:elm-types:r1}String",
                                              "value": "E11"
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  }
                                },
                                {
                                  "type": "Equal",
                                  "operand": [
                                    {
                                      "type": "Property",
                                      "path": "clinicalStatus",
                                      "source": {
                                        "type": "AliasRef",
                                        "name": "C"
                                      }
                                    },
                                    {
                                      "type": "Literal",
                                      "value": "active"
                                    }
                                  ]
                                }
                              ]
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);

        assertThat(result).hasSize(1);
        DataRequirementInfo req = result.get(0);
        assertThat(req.getType()).isEqualTo("Condition");
        assertThat(req.getCodeFilter()).hasSize(1);
        assertThat(req.getCodeFilter().get(0).getPath()).isEqualTo("code");
        assertThat(req.getCodeFilter().get(0).getCodeSystemUrl()).isEqualTo("http://hl7.org/fhir/sid/icd-10-cm");
        assertThat(req.getCodeFilter().get(0).getCodeSystemName()).isEqualTo("ICD10CM");
    }

    @Test
    void extract_deduplicatesQueriesWithSameCodeSystem() {
        // Two queries both retrieving Condition with same code system should deduplicate
        String elmJson = """
                {
                  "library": {
                    "codeSystems": {
                      "def": [
                        { "name": "ICD10CM", "id": "http://hl7.org/fhir/sid/icd-10-cm" }
                      ]
                    },
                    "statements": {
                      "def": [
                        {
                          "name": "Expr1",
                          "expression": {
                            "type": "Query",
                            "source": [{ "alias": "C", "expression": { "type": "Retrieve", "dataType": "{http://hl7.org/fhir}Condition" } }],
                            "where": {
                              "type": "Exists",
                              "operand": {
                                "type": "Query",
                                "source": [{ "alias": "Y", "expression": { "type": "Property", "path": "coding", "source": { "type": "Property", "path": "code", "source": { "type": "AliasRef", "name": "C" } } } }],
                                "where": { "type": "Equal", "operand": [{ "type": "Property", "path": "system", "source": { "type": "AliasRef", "name": "Y" } }, { "type": "Literal", "valueType": "{urn:hl7-org:elm-types:r1}String", "value": "http://hl7.org/fhir/sid/icd-10-cm" }] }
                              }
                            }
                          }
                        },
                        {
                          "name": "Expr2",
                          "expression": {
                            "type": "Query",
                            "source": [{ "alias": "C2", "expression": { "type": "Retrieve", "dataType": "{http://hl7.org/fhir}Condition" } }],
                            "where": {
                              "type": "Exists",
                              "operand": {
                                "type": "Query",
                                "source": [{ "alias": "Z", "expression": { "type": "Property", "path": "coding", "source": { "type": "Property", "path": "code", "source": { "type": "AliasRef", "name": "C2" } } } }],
                                "where": { "type": "Equal", "operand": [{ "type": "Property", "path": "system", "source": { "type": "AliasRef", "name": "Z" } }, { "type": "Literal", "valueType": "{urn:hl7-org:elm-types:r1}String", "value": "http://hl7.org/fhir/sid/icd-10-cm" }] }
                              }
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        List<DataRequirementInfo> result = extractor.extract(elmJson);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCodeFilter().get(0).getCodeSystemUrl())
                .isEqualTo("http://hl7.org/fhir/sid/icd-10-cm");
    }
}
