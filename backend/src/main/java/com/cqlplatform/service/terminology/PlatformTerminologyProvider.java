package com.cqlplatform.service.terminology;

import com.cqlplatform.model.terminology.PlatformValueSet;
import com.cqlplatform.service.terminology.PlatformValueSetService.Resolved;
import lombok.extern.slf4j.Slf4j;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.cql.engine.terminology.CodeSystemInfo;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.opencds.cqf.cql.engine.terminology.ValueSetInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PAT-230 — puts this installation's own value sets in front of every other terminology source
 * for ONE evaluation.
 *
 * <p>The tenant is fixed when the provider is created (on the request thread, where
 * {@code TenantContext} is reliable) rather than read when the engine calls back: the shared
 * provider underneath is cached process-wide per terminology server, and the engine may call from
 * a thread that carries no tenant. A value set of another tenant is therefore never consulted,
 * whatever thread asks.
 *
 * <p>Each reference is read from the database once per evaluation ({@code in()} is called for
 * every code of every retrieved resource), so an edit is picked up by the next evaluation and
 * nothing is shared between evaluations or tenants.
 */
@Slf4j
public class PlatformTerminologyProvider implements TerminologyProvider {

    private final Long tenantId;
    private final PlatformValueSetService valueSets;
    private final TerminologyProvider delegate;
    private final Map<String, Optional<List<PlatformValueSet.Concept>>> resolved = new HashMap<>();

    public PlatformTerminologyProvider(Long tenantId, PlatformValueSetService valueSets, TerminologyProvider delegate) {
        this.tenantId = tenantId;
        this.valueSets = valueSets;
        this.delegate = delegate;
    }

    @Override
    public boolean in(Code code, ValueSetInfo valueSet) {
        Optional<List<PlatformValueSet.Concept>> concepts = concepts(valueSet);
        if (concepts.isEmpty()) return delegate.in(code, valueSet);
        if (code == null || code.getCode() == null) return false;
        for (PlatformValueSet.Concept c : concepts.get()) {
            // Same tolerance as LocalTerminologyProvider: a code without a system matches on code alone.
            if (code.getCode().equals(c.getCode()) && (code.getSystem() == null || code.getSystem().equals(c.getSystem()))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterable<Code> expand(ValueSetInfo valueSet) {
        Optional<List<PlatformValueSet.Concept>> concepts = concepts(valueSet);
        if (concepts.isEmpty()) return delegate.expand(valueSet);
        List<Code> codes = new ArrayList<>(concepts.get().size());
        for (PlatformValueSet.Concept c : concepts.get()) {
            codes.add(new Code().withSystem(c.getSystem()).withVersion(c.getVersion())
                    .withCode(c.getCode()).withDisplay(c.getDisplay()));
        }
        return codes;
    }

    @Override
    public Code lookup(Code code, CodeSystemInfo codeSystem) {
        return delegate.lookup(code, codeSystem);
    }

    private synchronized Optional<List<PlatformValueSet.Concept>> concepts(ValueSetInfo info) {
        if (info == null || info.getId() == null) return Optional.empty();
        String key = info.getId() + "|" + (info.getVersion() == null ? "" : info.getVersion());
        return resolved.computeIfAbsent(key, k -> {
            Optional<Resolved> found = valueSets.resolve(tenantId, info.getId(), info.getVersion());
            found.ifPresent(r -> log.info("Value set {} resolved from the platform store: version {} ({}, {} codes{})",
                    info.getId(), r.valueSet().getVersion(), r.valueSet().getStatus(), r.valueSet().getConcepts().size(),
                    r.pinned() ? ", pinned" : ""));
            return found.map(r -> r.valueSet().getConcepts());
        });
    }
}
