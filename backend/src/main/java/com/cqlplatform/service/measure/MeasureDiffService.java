package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureVersionDiff;
import com.cqlplatform.model.measure.MeasureVersionDiff.CqlLine;
import com.cqlplatform.model.measure.MeasureVersionDiff.FieldChange;
import com.cqlplatform.model.measure.MeasureVersionDiff.GroupChange;
import com.cqlplatform.model.measure.MeasureVersionDiff.ItemChange;
import com.cqlplatform.model.measure.ObservationDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.StratifierDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PAT-263 — the structured, human-readable difference between two measure versions (MADiE gap
 * item 11). Pure: no repositories, no HTTP. Metadata is compared field by field in named sections,
 * groups are matched by {@code groupId} (populations by type, stratifiers by id, observations by
 * position) so a reviewer sees "numerator changed from X to Y" instead of "population count
 * changed", and the CQL is diffed line by line (longest common subsequence after trimming the
 * common prefix / suffix; texts above {@link #MAX_DIFF_LINES} lines are reported as changed without
 * line detail).
 */
@Service
public class MeasureDiffService {

    static final int MAX_DIFF_LINES = 4000;

    private record Field(String section, String name, Function<MeasureDefinition, String> value) {}

    private static final List<Field> FIELDS = List.of(
            new Field("identity", "name", MeasureDefinition::getName),
            new Field("identity", "title", MeasureDefinition::getTitle),
            new Field("identity", "ecqmTitle", MeasureDefinition::getEcqmTitle),
            new Field("identity", "status", MeasureDefinition::getStatus),
            new Field("identity", "cqlLibraryId", MeasureDefinition::getCqlLibraryId),
            new Field("description", "description", MeasureDefinition::getDescription),
            new Field("description", "rationale", MeasureDefinition::getRationale),
            new Field("description", "clinicalGuidance", MeasureDefinition::getClinicalGuidance),
            new Field("description", "clinicalRecommendationStatement", MeasureDefinition::getClinicalRecommendationStatement),
            new Field("description", "definitionTerms", m -> joinTerms(m.getDefinitionTerms())),
            new Field("description", "disclaimer", MeasureDefinition::getDisclaimer),
            new Field("description", "copyright", MeasureDefinition::getCopyright),
            new Field("scoring", "scoringType", MeasureDefinition::getScoringType),
            new Field("scoring", "improvementNotation", MeasureDefinition::getImprovementNotation),
            new Field("scoring", "rateAggregation", MeasureDefinition::getRateAggregation),
            new Field("scoring", "measureTypes", m -> join(m.getMeasureTypes())),
            new Field("scoring", "compositeScoring", MeasureDefinition::getCompositeScoring),
            new Field("scoring", "componentMeasureIds", m -> join(m.getComponentMeasureIds())),
            new Field("scoring", "componentWeights", m -> joinMap(m.getComponentWeights())),
            new Field("stewardship", "steward", MeasureDefinition::getSteward),
            new Field("stewardship", "developers", m -> join(m.getDevelopers())),
            new Field("stewardship", "endorser", MeasureDefinition::getEndorser),
            new Field("stewardship", "endorsementId", MeasureDefinition::getEndorsementId),
            new Field("stewardship", "references", m -> joinRefs(m.getReferences())),
            new Field("stewardship", "nqfNumber", MeasureDefinition::getNqfNumber),
            new Field("stewardship", "cmsMeasureId", MeasureDefinition::getCmsMeasureId),
            new Field("stewardship", "mohIndicatorCode", MeasureDefinition::getMohIndicatorCode),
            new Field("stewardship", "nhiaP4pCode", MeasureDefinition::getNhiaP4pCode),
            new Field("stewardship", "drgIndicatorCode", MeasureDefinition::getDrgIndicatorCode),
            new Field("stewardship", "indicatorCategory", MeasureDefinition::getIndicatorCategory),
            new Field("stewardship", "department", MeasureDefinition::getDepartment),
            new Field("stewardship", "setting", MeasureDefinition::getSetting),
            new Field("stewardship", "measureSet", MeasureDefinition::getMeasureSet),
            new Field("dates", "effectiveStart", m -> str(m.getEffectiveStart())),
            new Field("dates", "effectiveEnd", m -> str(m.getEffectiveEnd())),
            new Field("dates", "approvalDate", m -> str(m.getApprovalDate())),
            new Field("dates", "lastReviewDate", m -> str(m.getLastReviewDate())),
            new Field("dates", "measurementPeriodStart", m -> str(m.getMeasurementPeriodStart())),
            new Field("dates", "measurementPeriodEnd", m -> str(m.getMeasurementPeriodEnd())),
            new Field("dates", "experimental", m -> str(m.getExperimental())),
            new Field("supplementalData", "supplementalData", m -> joinDefs(m.getSupplementalData() == null ? null
                    : m.getSupplementalData().stream().map(d -> d.getDefinition() + (d.getDescription() != null ? " — " + d.getDescription() : "")).toList())),
            new Field("supplementalData", "riskAdjustments", m -> joinDefs(m.getRiskAdjustments() == null ? null
                    : m.getRiskAdjustments().stream().map(d -> d.getDefinition() + (d.getDescription() != null ? " — " + d.getDescription() : "")).toList())),
            new Field("supplementalData", "supplementalDataGuidance", MeasureDefinition::getSupplementalDataGuidance),
            new Field("supplementalData", "riskAdjustmentDescription", MeasureDefinition::getRiskAdjustmentDescription),
            new Field("terminology", "valueSetVersions", m -> joinMap(m.getValueSetVersions()))
    );

    public MeasureVersionDiff diff(MeasureDefinition oldM, MeasureDefinition newM) {
        List<FieldChange> metadata = new ArrayList<>();
        for (Field f : FIELDS) {
            FieldChange change = fieldChange(f.section(), f.name(), f.value().apply(oldM), f.value().apply(newM));
            if (change != null) metadata.add(change);
        }
        List<GroupChange> groups = diffGroups(oldM.getGroupDefinitions(), newM.getGroupDefinitions());

        String oldCql = oldM.getCqlContent() != null ? oldM.getCqlContent() : "";
        String newCql = newM.getCqlContent() != null ? newM.getCqlContent() : "";
        boolean cqlChanged = !normalize(oldCql).equals(normalize(newCql));
        List<CqlLine> lines = cqlChanged ? lineDiff(normalize(oldCql), normalize(newCql)) : List.of();
        int added = 0, removed = 0;
        if (lines != null) {
            for (CqlLine l : lines) {
                if (MeasureVersionDiff.ADDED.equals(l.getType())) added++;
                else if (MeasureVersionDiff.REMOVED.equals(l.getType())) removed++;
            }
        }

        int populations = 0, stratifiers = 0, observations = 0, groupEdits = 0;
        for (GroupChange g : groups) {
            if (!MeasureVersionDiff.CHANGED.equals(g.getKind()) && !MeasureVersionDiff.UNCHANGED.equals(g.getKind())) groupEdits++;
            populations += g.getPopulations().size();
            stratifiers += g.getStratifiers().size();
            observations += g.getObservations().size();
        }
        int total = metadata.size() + populations + stratifiers + observations + groupEdits + (cqlChanged ? 1 : 0);

        return MeasureVersionDiff.builder()
                .oldId(oldM.getId()).newId(newM.getId())
                .name(newM.getName() != null ? newM.getName() : oldM.getName())
                .oldVersion(oldM.getVersion()).newVersion(newM.getVersion())
                .oldStatus(oldM.getStatus()).newStatus(newM.getStatus())
                .oldCql(oldCql).newCql(newCql).cqlChanged(cqlChanged).cqlLines(lines)
                .metadata(metadata).groups(groups)
                .summary(MeasureVersionDiff.Summary.builder()
                        .metadata(metadata.size()).groups(groupEdits).populations(populations)
                        .stratifiers(stratifiers).observations(observations)
                        .cqlAdded(added).cqlRemoved(removed).total(total).build())
                .metadataChanges(metadata.stream().map(MeasureDiffService::sentence).toList())
                .populationChanges(legacyPopulationSentences(groups))
                .build();
    }

    // ------------------------------------------------------------------ groups

    private List<GroupChange> diffGroups(List<GroupDefinition> oldGroups, List<GroupDefinition> newGroups) {
        Map<String, GroupDefinition> olds = keyed(oldGroups, GroupDefinition::getGroupId, "group");
        Map<String, GroupDefinition> news = keyed(newGroups, GroupDefinition::getGroupId, "group");
        List<GroupChange> out = new ArrayList<>();
        for (String key : union(olds.keySet(), news.keySet())) {
            GroupDefinition o = olds.get(key);
            GroupDefinition n = news.get(key);
            if (o == null || n == null) {
                GroupDefinition present = o != null ? o : n;
                String kind = o == null ? MeasureVersionDiff.ADDED : MeasureVersionDiff.REMOVED;
                out.add(GroupChange.builder().groupId(key).kind(kind)
                        .fields(List.of())
                        .populations(items(o == null ? null : o.getPopulations(), n == null ? null : n.getPopulations(),
                                PopulationDefinition::getPopulationType, PopulationDefinition::getCriteriaExpression,
                                PopulationDefinition::getDescription, "population"))
                        .stratifiers(items(o == null ? null : o.getStratifiers(), n == null ? null : n.getStratifiers(),
                                StratifierDefinition::getStratifierId, MeasureDiffService::stratifierExpression,
                                StratifierDefinition::getDescription, "stratifier"))
                        .observations(items(o == null ? null : o.getObservations(), n == null ? null : n.getObservations(),
                                obs -> null, MeasureDiffService::observationExpression, ObservationDefinition::getDescription, "observation"))
                        .build());
                if (present == null) continue;
                continue;
            }
            List<FieldChange> fields = new ArrayList<>();
            addIf(fields, fieldChange("group", "description", o.getDescription(), n.getDescription()));
            addIf(fields, fieldChange("group", "populationBasis", o.getPopulationBasis(), n.getPopulationBasis()));
            addIf(fields, fieldChange("group", "scoringUnit", o.getScoringUnit(), n.getScoringUnit()));
            addIf(fields, fieldChange("group", "rateIndex", str(o.getRateIndex()), str(n.getRateIndex())));
            addIf(fields, fieldChange("group", "rateDescription", o.getRateDescription(), n.getRateDescription()));
            List<ItemChange> populations = items(o.getPopulations(), n.getPopulations(),
                    PopulationDefinition::getPopulationType, PopulationDefinition::getCriteriaExpression, PopulationDefinition::getDescription, "population");
            List<ItemChange> stratifiers = items(o.getStratifiers(), n.getStratifiers(),
                    StratifierDefinition::getStratifierId, MeasureDiffService::stratifierExpression, StratifierDefinition::getDescription, "stratifier");
            List<ItemChange> observations = items(o.getObservations(), n.getObservations(),
                    obs -> null, MeasureDiffService::observationExpression, ObservationDefinition::getDescription, "observation");
            boolean changed = !fields.isEmpty() || !populations.isEmpty() || !stratifiers.isEmpty() || !observations.isEmpty();
            out.add(GroupChange.builder().groupId(key).kind(changed ? MeasureVersionDiff.CHANGED : MeasureVersionDiff.UNCHANGED)
                    .fields(fields).populations(populations).stratifiers(stratifiers).observations(observations).build());
        }
        return out;
    }

    /** Items of one kind matched by key (type / id, or position when the key is blank); only the differences are returned. */
    private static <T> List<ItemChange> items(List<T> olds, List<T> news, Function<T, String> key,
                                               Function<T, String> value, Function<T, String> description, String fallback) {
        Map<String, T> o = keyed(olds, key, fallback);
        Map<String, T> n = keyed(news, key, fallback);
        List<ItemChange> out = new ArrayList<>();
        for (String k : union(o.keySet(), n.keySet())) {
            T a = o.get(k);
            T b = n.get(k);
            String av = a == null ? null : value.apply(a);
            String bv = b == null ? null : value.apply(b);
            String ad = a == null ? null : description.apply(a);
            String bd = b == null ? null : description.apply(b);
            String kind = a == null ? MeasureVersionDiff.ADDED : b == null ? MeasureVersionDiff.REMOVED
                    : (Objects.equals(nz(av), nz(bv)) && Objects.equals(nz(ad), nz(bd)) ? null : MeasureVersionDiff.CHANGED);
            if (kind == null) continue;
            out.add(ItemChange.builder().key(k).kind(kind).oldValue(av).newValue(bv).oldDescription(ad).newDescription(bd).build());
        }
        return out;
    }

    /** Keys items by {@code key}, falling back to {@code <fallback> #<position>}; duplicate keys get {@code (2)}, {@code (3)}… */
    private static <T> Map<String, T> keyed(List<T> items, Function<T, String> key, String fallback) {
        Map<String, T> out = new LinkedHashMap<>();
        if (items == null) return out;
        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);
            String k = item == null ? null : key.apply(item);
            if (k == null || k.isBlank()) k = fallback + " #" + (i + 1);
            String candidate = k;
            int n = 2;
            while (out.containsKey(candidate)) candidate = k + " (" + n++ + ")";
            out.put(candidate, item);
        }
        return out;
    }

    private static Set<String> union(Collection<String> a, Collection<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return out;
    }

    private static String stratifierExpression(StratifierDefinition s) {
        if (s.hasComponents()) {
            return s.getComponents().stream().map(c -> c.getCode() + ": " + c.getCriteriaExpression()).collect(Collectors.joining(" × "));
        }
        return s.getCriteriaExpression();
    }

    private static String observationExpression(ObservationDefinition o) {
        StringBuilder sb = new StringBuilder();
        if (o.getCriteriaExpression() != null) sb.append(o.getCriteriaExpression());
        if (o.getAggregateMethod() != null) sb.append(" [").append(o.getAggregateMethod()).append("]");
        if (o.getPopulationRef() != null) sb.append(" of ").append(o.getPopulationRef());
        return sb.toString();
    }

    // ------------------------------------------------------------------ CQL line diff

    /** Longest-common-subsequence line diff after trimming the common prefix and suffix; null above the size limit. */
    static List<CqlLine> lineDiff(String oldText, String newText) {
        String[] a = oldText.isEmpty() ? new String[0] : oldText.split("\n", -1);
        String[] b = newText.isEmpty() ? new String[0] : newText.split("\n", -1);
        if (a.length > MAX_DIFF_LINES || b.length > MAX_DIFF_LINES) return null;
        int prefix = 0;
        while (prefix < a.length && prefix < b.length && a[prefix].equals(b[prefix])) prefix++;
        int suffix = 0;
        while (suffix < a.length - prefix && suffix < b.length - prefix && a[a.length - 1 - suffix].equals(b[b.length - 1 - suffix])) suffix++;
        int n = a.length - prefix - suffix;
        int m = b.length - prefix - suffix;
        List<CqlLine> out = new ArrayList<>();
        for (int i = 0; i < prefix; i++) out.add(line("equal", i + 1, i + 1, a[i]));
        if (n > 0 || m > 0) {
            int[][] lcs = new int[n + 1][m + 1];
            for (int i = n - 1; i >= 0; i--) {
                for (int j = m - 1; j >= 0; j--) {
                    lcs[i][j] = a[prefix + i].equals(b[prefix + j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            int i = 0, j = 0;
            while (i < n && j < m) {
                if (a[prefix + i].equals(b[prefix + j])) {
                    out.add(line("equal", prefix + i + 1, prefix + j + 1, a[prefix + i]));
                    i++; j++;
                } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                    out.add(line(MeasureVersionDiff.REMOVED, prefix + i + 1, null, a[prefix + i]));
                    i++;
                } else {
                    out.add(line(MeasureVersionDiff.ADDED, null, prefix + j + 1, b[prefix + j]));
                    j++;
                }
            }
            while (i < n) { out.add(line(MeasureVersionDiff.REMOVED, prefix + i + 1, null, a[prefix + i])); i++; }
            while (j < m) { out.add(line(MeasureVersionDiff.ADDED, null, prefix + j + 1, b[prefix + j])); j++; }
        }
        for (int k = 0; k < suffix; k++) {
            int ai = a.length - suffix + k;
            int bi = b.length - suffix + k;
            out.add(line("equal", ai + 1, bi + 1, a[ai]));
        }
        return out;
    }

    private static CqlLine line(String type, Integer oldLine, Integer newLine, String text) {
        return CqlLine.builder().type(type).oldLine(oldLine).newLine(newLine).text(text).build();
    }

    private static String normalize(String cql) {
        return cql.replace("\r\n", "\n").replace('\r', '\n');
    }

    // ------------------------------------------------------------------ helpers

    private static FieldChange fieldChange(String section, String field, String oldValue, String newValue) {
        String o = nz(oldValue);
        String n = nz(newValue);
        if (o.equals(n)) return null;
        String kind = o.isEmpty() ? MeasureVersionDiff.ADDED : n.isEmpty() ? MeasureVersionDiff.REMOVED : MeasureVersionDiff.CHANGED;
        return FieldChange.builder().section(section).field(field).kind(kind)
                .oldValue(o.isEmpty() ? null : o).newValue(n.isEmpty() ? null : n).build();
    }

    private static void addIf(List<FieldChange> list, FieldChange change) {
        if (change != null) list.add(change);
    }

    /** The pre-PAT-263 sentence for a field change. */
    static String sentence(FieldChange c) {
        String label = Character.toUpperCase(c.getField().charAt(0)) + c.getField().substring(1).replaceAll("([A-Z])", " $1");
        return switch (c.getKind()) {
            case MeasureVersionDiff.ADDED -> label + " added: \"" + c.getNewValue() + "\"";
            case MeasureVersionDiff.REMOVED -> label + " removed (was: \"" + c.getOldValue() + "\")";
            default -> label + " changed: \"" + c.getOldValue() + "\" → \"" + c.getNewValue() + "\"";
        };
    }

    private static List<String> legacyPopulationSentences(List<GroupChange> groups) {
        List<String> out = new ArrayList<>();
        for (GroupChange g : groups) {
            switch (g.getKind()) {
                case MeasureVersionDiff.ADDED -> out.add("Group " + g.getGroupId() + " added");
                case MeasureVersionDiff.REMOVED -> out.add("Group " + g.getGroupId() + " removed");
                case MeasureVersionDiff.CHANGED -> {
                    for (FieldChange f : g.getFields()) out.add(g.getGroupId() + ": " + sentence(f));
                    for (ItemChange p : g.getPopulations()) out.add(g.getGroupId() + ": population " + itemSentence(p));
                    for (ItemChange s : g.getStratifiers()) out.add(g.getGroupId() + ": stratifier " + itemSentence(s));
                    for (ItemChange o : g.getObservations()) out.add(g.getGroupId() + ": " + itemSentence(o));
                }
                default -> { }
            }
        }
        return out;
    }

    private static String itemSentence(ItemChange c) {
        return switch (c.getKind()) {
            case MeasureVersionDiff.ADDED -> c.getKey() + " added: \"" + nz(c.getNewValue()) + "\"";
            case MeasureVersionDiff.REMOVED -> c.getKey() + " removed (was: \"" + nz(c.getOldValue()) + "\")";
            default -> c.getKey() + " changed: \"" + nz(c.getOldValue()) + "\" → \"" + nz(c.getNewValue()) + "\"";
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String join(List<?> items) {
        return items == null || items.isEmpty() ? null : items.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static String joinDefs(List<String> items) {
        return items == null || items.isEmpty() ? null : String.join("; ", items);
    }

    private static String joinMap(Map<String, ?> map) {
        return map == null || map.isEmpty() ? null
                : map.entrySet().stream().map(e -> e.getKey() + " = " + e.getValue()).sorted().collect(Collectors.joining("; "));
    }

    private static String joinTerms(List<MeasureDefinition.DefinitionTerm> terms) {
        return terms == null || terms.isEmpty() ? null
                : terms.stream().map(t -> nz(t.getTerm()) + ": " + nz(t.getDefinition())).collect(Collectors.joining("; "));
    }

    private static String joinRefs(List<MeasureDefinition.MeasureReference> refs) {
        return refs == null || refs.isEmpty() ? null
                : refs.stream().map(r -> (r.getType() != null ? r.getType() + ": " : "") + nz(r.getReference())).collect(Collectors.joining("; "));
    }
}
