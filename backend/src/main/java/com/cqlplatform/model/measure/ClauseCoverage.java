package com.cqlplatform.model.measure;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PAT-232 — which clauses of a CQL library were executed (Bonnie / MADiE style test coverage).
 *
 * <p>A clause is one ELM expression node that carries a source locator: a literal, an operator,
 * a retrieve, a query, a {@code where} condition, an {@code if} branch… The engine reports every
 * clause it evaluates; a clause it never reaches (the {@code else} of an {@code if} whose
 * condition was true, a define nobody referenced) stays uncovered. Coverage is the share of
 * clauses reached — over one test case, or merged over all test cases of a measure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClauseCoverage {

    /** The CQL text the locators refer to — the highlight must be drawn over exactly this text. */
    private String cql;
    private String libraryName;
    private int totalClauses;
    private int coveredClauses;
    /** 0–100, one decimal. */
    private double percent;
    private List<Statement> statements;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Statement {
        private String name;
        /** {@code startLine:startCol-endLine:endCol} of the whole define / function. */
        private String locator;
        private boolean function;
        private int totalClauses;
        private int coveredClauses;
        private List<Clause> clauses;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Clause {
        private String localId;
        /** {@code startLine:startCol-endLine:endCol}; a single-line clause may be {@code line:col}. */
        private String locator;
        /** ELM node type: {@code Retrieve}, {@code And}, {@code Query}, {@code Literal}… */
        private String type;
        /** How many times the engine evaluated it (a query's {@code where} runs once per item). */
        private int hits;
        /** The last value it produced, shortened for display; null when never evaluated or null. */
        private String value;

        public boolean isCovered() {
            return hits > 0;
        }
    }

    public static double percentOf(int covered, int total) {
        return total == 0 ? 0.0 : Math.round(covered * 1000.0 / total) / 10.0;
    }

    /**
     * Coverage over several runs of the SAME library (all test cases of a measure): a clause is
     * covered if any run reached it; hits add up; the value shown is the last run's.
     */
    public static ClauseCoverage merge(List<ClauseCoverage> runs) {
        ClauseCoverage first = runs.stream().filter(r -> r != null && r.getStatements() != null).findFirst().orElse(null);
        if (first == null) return null;
        Map<String, Map<String, Clause>> merged = new LinkedHashMap<>();
        Map<String, Statement> shells = new LinkedHashMap<>();
        for (ClauseCoverage run : runs) {
            if (run == null || run.getStatements() == null || !first.getCql().equals(run.getCql())) continue;
            for (Statement s : run.getStatements()) {
                shells.putIfAbsent(s.getName(), s);
                Map<String, Clause> byId = merged.computeIfAbsent(s.getName(), k -> new LinkedHashMap<>());
                for (Clause c : s.getClauses()) {
                    Clause acc = byId.get(c.getLocalId());
                    if (acc == null) {
                        byId.put(c.getLocalId(), Clause.builder().localId(c.getLocalId()).locator(c.getLocator())
                                .type(c.getType()).hits(c.getHits()).value(c.getValue()).build());
                    } else {
                        acc.setHits(acc.getHits() + c.getHits());
                        if (c.getValue() != null) acc.setValue(c.getValue());
                    }
                }
            }
        }
        List<Statement> statements = new ArrayList<>();
        int total = 0;
        int covered = 0;
        for (Map.Entry<String, Map<String, Clause>> e : merged.entrySet()) {
            List<Clause> clauses = new ArrayList<>(e.getValue().values());
            int c = (int) clauses.stream().filter(Clause::isCovered).count();
            Statement shell = shells.get(e.getKey());
            statements.add(Statement.builder().name(e.getKey()).locator(shell.getLocator()).function(shell.isFunction())
                    .totalClauses(clauses.size()).coveredClauses(c).clauses(clauses).build());
            total += clauses.size();
            covered += c;
        }
        return ClauseCoverage.builder().cql(first.getCql()).libraryName(first.getLibraryName())
                .totalClauses(total).coveredClauses(covered).percent(percentOf(covered, total))
                .statements(statements).build();
    }
}
