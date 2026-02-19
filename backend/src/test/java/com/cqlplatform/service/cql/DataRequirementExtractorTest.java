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
    void extract_retrieveWithCodeRef_shouldCaptureDirectCode() {
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
        DataRequirementInfo req = result.get(0);
        assertThat(req.getCodeFilter()).hasSize(1);
        assertThat(req.getCodeFilter().get(0).getCode()).hasSize(1);
        assertThat(req.getCodeFilter().get(0).getCode().get(0).getCode()).isEqualTo("BloodPressure");
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
}
