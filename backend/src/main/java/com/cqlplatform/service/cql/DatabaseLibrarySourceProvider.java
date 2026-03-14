package com.cqlplatform.service.cql;

import com.cqlplatform.entity.CqlLibraryEntity;
import com.cqlplatform.repository.CqlLibraryRepository;
import kotlinx.io.CoreKt;
import kotlinx.io.JvmCoreKt;
import kotlinx.io.Source;
import org.cqframework.cql.cql2elm.LibrarySourceProvider;
import org.hl7.elm.r1.VersionedIdentifier;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class DatabaseLibrarySourceProvider implements LibrarySourceProvider {

    private final CqlLibraryRepository libraryRepository;

    public DatabaseLibrarySourceProvider(CqlLibraryRepository libraryRepository) {
        this.libraryRepository = libraryRepository;
    }

    @Override
    public Source getLibrarySource(VersionedIdentifier libraryIdentifier) {
        String name = libraryIdentifier.getId();
        String version = libraryIdentifier.getVersion();

        Optional<CqlLibraryEntity> entity;
        if (version != null && !version.isBlank()) {
            entity = libraryRepository.findByNameAndVersion(name, version);
        } else {
            // Resolve latest version
            List<CqlLibraryEntity> versions = libraryRepository.findByName(name);
            entity = versions.stream()
                    .max(Comparator.comparing(CqlLibraryEntity::getVersion, new SemanticVersionComparator()));
        }

        return entity
                .map(e -> {
                    ByteArrayInputStream bais = new ByteArrayInputStream(
                            e.getCqlContent().getBytes(StandardCharsets.UTF_8));
                    return (Source) CoreKt.buffered(JvmCoreKt.asSource(bais));
                })
                .orElse(null);
    }
}
