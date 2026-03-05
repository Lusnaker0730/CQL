package com.cqlplatform.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "ecqm_external_cql_library")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class EcqmExternalCqlLibraryEntity extends AbstractExternalCqlLibraryEntity {
}
