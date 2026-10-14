package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.model.measure.MeasureListQuery;
import com.cqlplatform.security.InputValidator;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PAT-257: the measure list page as a JPA Specification — the tenant predicate first (every list query is
 * tenant-scoped, see CLAUDE.md), then the optional filters. LIKE patterns go through
 * {@link InputValidator#escapeLikeWildcards} and declare {@code \} as the escape character, so a search for
 * {@code HbA1c_v2} does not match {@code HbA1c-v2}.
 */
final class MeasureSpecifications {

    private MeasureSpecifications() {}

    static Specification<MeasureDefinitionEntity> list(Long tenantId, MeasureListQuery q) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            and.add(cb.equal(root.get("tenantId"), tenantId));
            if (q.department() != null && !q.department().isBlank()) {
                and.add(cb.equal(root.get("department"), q.department()));
            }
            if (q.statuses() != null && !q.statuses().isEmpty()) {
                and.add(root.get("status").in(q.statuses()));
            }
            if (q.search() != null && !q.search().isBlank()) {
                String pattern = "%" + InputValidator.escapeLikeWildcards(q.search().trim().toLowerCase(Locale.ROOT)) + "%";
                and.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("title")), pattern, '\\')));
            }
            MeasureListQuery.Scope scope = q.scope() == null ? MeasureListQuery.Scope.ALL : q.scope();
            switch (scope) {
                case MINE -> and.add(cb.equal(root.get("ownerUsername"), q.username()));
                case SHARED -> {
                    // shared_with is a JSON array of user names: ["alice","bob"]
                    and.add(cb.like(root.get("sharedWith"), "%\"" + InputValidator.escapeLikeWildcards(q.username()) + "\"%", '\\'));
                    and.add(cb.or(cb.isNull(root.get("ownerUsername")), cb.notEqual(root.get("ownerUsername"), q.username())));
                }
                case PUBLIC -> and.add(cb.equal(root.get("accessLevel"), "public"));
                case ALL -> { }
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
    }
}
