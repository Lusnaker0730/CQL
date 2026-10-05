package com.cqlplatform.service.measure;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** PAT-243 — what counts as an identifiable list of episodes in a serialised CQL result. */
class EpisodeKeysTest {

    @Test
    void aResourceList_yieldsItsKeysInOrder_duplicatesKept() {
        assertThat(EpisodeKeys.of(List.of("FHIR.Encounter/e1", "FHIR.Encounter/e2", "FHIR.Encounter/e1")))
                .containsExactly("FHIR.Encounter/e1", "FHIR.Encounter/e2", "FHIR.Encounter/e1");
        assertThat(EpisodeKeys.of(List.of())).isEmpty();
        assertThat(EpisodeKeys.of("FHIR.Procedure/p1")).containsExactly("FHIR.Procedure/p1");
        assertThat(EpisodeKeys.of(List.of(Map.of("resourceType", "Encounter", "id", "e7"))))
                .containsExactly("FHIR.Encounter/e7");
    }

    @Test
    void anythingThatIsNotAListOfIdentifiedResources_isNotAnEpisodeList() {
        assertThat(EpisodeKeys.of(true)).isNull();
        assertThat(EpisodeKeys.of(3)).isNull();
        assertThat(EpisodeKeys.of(null)).isNull();
        assertThat(EpisodeKeys.of("some text")).isNull();
        assertThat(EpisodeKeys.of(List.of(1, 2))).isNull();
        assertThat(EpisodeKeys.of(List.of("FHIR.Encounter"))).isNull();                       // no id
        assertThat(EpisodeKeys.of(List.of("FHIR.Encounter/e1", "FHIR.Encounter"))).isNull();   // one id-less item spoils the list
        assertThat(EpisodeKeys.of(List.of(Map.of("code", "x")))).isNull();
        assertThat(EpisodeKeys.of(List.of("[... truncated at 100 items]"))).isNull();
    }
}
