package io.dbeavererd.model;

import java.util.Objects;

/**
 * One column of an entity, with the flags DBeaver's ERD uses to render it.
 *
 * @param name           column name
 * @param fullTypeName   DBeaver-style type label, e.g. {@code varchar(255)}, {@code numeric(10, 2)}, {@code int4[]}
 * @param iconId         icon resource id for the type, e.g. {@code types/number}
 * @param required       NOT NULL
 * @param inPrimaryKey   part of the entity's "best identifier" (PK, else last unique constraint, else unique index)
 * @param inForeignKey   part of any FK of the entity (even if the referenced table is not in the diagram)
 * @param inAnyConstraint part of any PK/unique/FK constraint (used for the KEYS visibility mode)
 * @param comment        column comment, may be null
 */
public record Attribute(
    String name,
    String fullTypeName,
    String iconId,
    boolean required,
    boolean inPrimaryKey,
    boolean inForeignKey,
    boolean inAnyConstraint,
    String comment
) {
    public Attribute {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fullTypeName, "fullTypeName");
        Objects.requireNonNull(iconId, "iconId");
    }
}
