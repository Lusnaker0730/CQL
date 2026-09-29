package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.service.ecqm.PublishedContent;

import java.util.List;
import java.util.Objects;

/**
 * BUG-147 — what counts as a measure's evaluated logic: the CQL, the group definitions (population
 * → define mapping, stratifiers, observations), the scoring type, the composite scoring and
 * components, and the CQL library id. Everything else (title, description, rationale, steward,
 * dates …) is descriptive metadata and may change on an approved measure. CQL and groups are
 * compared through {@link PublishedContent#hash}, so line endings, trailing whitespace and a
 * serialisation round trip do not count as a change.
 */
public final class MeasureLogic {

    private MeasureLogic() {
    }

    public static boolean changed(MeasureDefinitionEntity current, String cql, List<GroupDefinition> groups,
                                  String scoringType, String compositeScoring, List<Long> componentMeasureIds,
                                  String cqlLibraryId) {
        if (!PublishedContent.hash(current.getCqlContent(), current.getGroupDefinitionList())
                .equals(PublishedContent.hash(cql, groups))) {
            return true;
        }
        return !Objects.equals(blankToNull(current.getScoringType()), blankToNull(scoringType))
                || !Objects.equals(blankToNull(current.getCompositeScoring()), blankToNull(compositeScoring))
                || !Objects.equals(emptyToNull(current.getComponentMeasureIdList()), emptyToNull(componentMeasureIds))
                || !Objects.equals(blankToNull(current.getCqlLibraryId()), blankToNull(cqlLibraryId));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static List<Long> emptyToNull(List<Long> list) {
        return list == null || list.isEmpty() ? null : list;
    }
}
