package com.cqlplatform.service.cql;

import com.cqlplatform.model.measure.ClauseCoverage;
import org.cqframework.cql.elm.visiting.BaseElmLibraryVisitor;
import org.hl7.elm.r1.Element;
import org.hl7.elm.r1.Expression;
import org.hl7.elm.r1.ExpressionDef;
import org.hl7.elm.r1.FunctionDef;
import org.hl7.elm.r1.Library;
import org.hl7.elm.r1.SingletonFrom;
import org.opencds.cqf.cql.engine.debug.BreakpointAction;
import org.opencds.cqf.cql.engine.debug.BreakpointHandler;
import org.opencds.cqf.cql.engine.execution.State;
import org.opencds.cqf.cql.engine.runtime.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PAT-232 — records every clause the engine evaluates, then reports it against every clause the
 * library has.
 *
 * <p>Two walks over the same ELM tree. The engine's {@code EvaluationVisitor.visitExpression}
 * calls {@link #onAfterExpression} for each expression node it evaluates (cql-engine 5.x); the
 * translator's {@code BaseElmLibraryVisitor.visitExpression} lets {@link #universe} enumerate the
 * same nodes statically. Both key on the node's {@code localId}, which the translator assigns when
 * {@code EnableLocators} is on — the platform always translates with it.
 *
 * <p>Only expression nodes count as clauses. Type specifiers, operand declarations and the
 * ExpressionDef / FunctionDef wrappers carry locators too, but the engine never "evaluates" them,
 * so they would be permanently uncovered noise. A define that nobody referenced contributes all
 * its clauses as uncovered — that is what an author needs to see.
 *
 * <p>One collector per evaluation: it is created after the engine and attached with
 * {@code engine.getState().setBreakpointHandler(this)}. Not shared between evaluations.
 */
public final class ClauseCoverageCollector implements BreakpointHandler {

    static final int MAX_VALUE_CHARS = 80;

    private static final class Hit {
        int hits;
        String lastValue;
    }

    private final Map<String, Hit> hitsByLocalId = new ConcurrentHashMap<>();

    @Override
    public BreakpointAction onBeforeExpression(Element element, State state) {
        return BreakpointAction.CONTINUE;
    }

    @Override
    public void onAfterExpression(Element element, State state, Value value) {
        if (element == null || element.getLocalId() == null || element.getLocator() == null) return;
        Hit hit = hitsByLocalId.computeIfAbsent(element.getLocalId(), k -> new Hit());
        hit.hits++;
        hit.lastValue = shortValue(value);
    }

    /** Coverage of {@code library} (whose CQL source is {@code cql}) given what was recorded. */
    public ClauseCoverage report(Library library, String cql) {
        List<ClauseCoverage.Statement> statements = new ArrayList<>();
        int total = 0;
        int covered = 0;
        for (Map.Entry<ExpressionDef, List<Expression>> e : universe(library).entrySet()) {
            ExpressionDef def = e.getKey();
            List<ClauseCoverage.Clause> clauses = new ArrayList<>();
            int coveredHere = 0;
            for (Expression node : e.getValue()) {
                Hit hit = hitsByLocalId.get(node.getLocalId());
                int hits = hit == null ? 0 : hit.hits;
                if (hits > 0) coveredHere++;
                clauses.add(ClauseCoverage.Clause.builder()
                        .localId(node.getLocalId())
                        .locator(node.getLocator())
                        .type(node.getClass().getSimpleName())
                        .hits(hits)
                        .value(hit == null ? null : hit.lastValue)
                        .build());
            }
            statements.add(ClauseCoverage.Statement.builder()
                    .name(def.getName())
                    .locator(def.getLocator())
                    .function(def instanceof FunctionDef)
                    .totalClauses(clauses.size())
                    .coveredClauses(coveredHere)
                    .clauses(clauses)
                    .build());
            total += clauses.size();
            covered += coveredHere;
        }
        return ClauseCoverage.builder()
                .cql(cql)
                .libraryName(library.getIdentifier() != null ? library.getIdentifier().getId() : null)
                .totalClauses(total)
                .coveredClauses(covered)
                .percent(ClauseCoverage.percentOf(covered, total))
                .statements(statements)
                .build();
    }

    /**
     * Every expression node with a locator, grouped by the statement it belongs to, in source
     * order — each node once. The translator shares nodes between parents (for
     * {@code duration in days of E.period} the {@code E.period} Property sits under both
     * {@code Start} and {@code End}), so a plain walk would list — and count — such a clause twice.
     *
     * <p>The implicit context definition ({@code context Patient} becomes
     * {@code define "Patient": SingletonFrom([Patient])}, located at the {@code context} line) is
     * skipped: the engine resolves the context patient without evaluating it through the
     * visitor, and it is not logic the author wrote.
     */
    static Map<ExpressionDef, List<Expression>> universe(Library library) {
        Map<ExpressionDef, List<Expression>> byDef = new LinkedHashMap<>();
        if (library == null || library.getStatements() == null) return byDef;
        for (ExpressionDef def : library.getStatements().getDef()) {
            if (def.getExpression() == null) continue; // an external function declaration
            if (isImplicitContextDef(def)) continue;
            Map<String, Expression> nodes = new LinkedHashMap<>();
            new BaseElmLibraryVisitor<Void, Map<String, Expression>>() {
                @Override
                protected Void defaultResult(Element element, Map<String, Expression> context) {
                    return null;
                }

                @Override
                public Void visitExpression(Expression elm, Map<String, Expression> context) {
                    if (elm.getLocalId() != null && elm.getLocator() != null) context.putIfAbsent(elm.getLocalId(), elm);
                    return super.visitExpression(elm, context);
                }
            }.visitExpression(def.getExpression(), nodes);
            byDef.put(def, new ArrayList<>(nodes.values()));
        }
        return byDef;
    }

    /** {@code define "Patient": SingletonFrom([Patient])} as the translator emits it for {@code context Patient}. */
    static boolean isImplicitContextDef(ExpressionDef def) {
        return def.getContext() != null
                && def.getContext().equals(def.getName())
                && !(def instanceof FunctionDef)
                && def.getExpression() instanceof SingletonFrom;
    }

    /** One line, at most {@link #MAX_VALUE_CHARS}: enough to tell {@code true} from {@code false} or {@code {}} from {@code {…}}. */
    static String shortValue(Value value) {
        if (value == null) return null;
        String s = describe(CqlValues.unwrap(value)).replaceAll("\\s+", " ");
        return s.length() > MAX_VALUE_CHARS ? s.substring(0, MAX_VALUE_CHARS - 1) + "…" : s;
    }

    /** FHIR resources as {@code FHIR.Type/id}, lists item by item — never a resource's whole tree. */
    private static String describe(Object plain) {
        if (plain instanceof org.opencds.cqf.cql.engine.runtime.ClassInstance ci) return CqlValues.describe(ci);
        if (plain instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (Object item : list) {
                if (sb.length() > MAX_VALUE_CHARS) break; // no point describing what will be cut off
                if (sb.length() > 1) sb.append(", ");
                sb.append(describe(item));
            }
            return sb.append("]").toString();
        }
        return String.valueOf(plain);
    }
}
