package io.dbeavererd.model;

import java.util.List;
import java.util.Objects;

/**
 * A foreign key drawn as a connection. Like DBeaver, {@code source} is the FK-owning (child)
 * entity and {@code target} is the referenced (parent) entity.
 *
 * @param name         constraint name
 * @param source       child entity (FK side)
 * @param target       parent entity (referenced side)
 * @param sourceColumns FK column names in the child
 * @param targetColumns referenced column names in the parent (same order)
 * @param identifying  all FK columns are part of the child's PK (or of a unique constraint if it has no PK);
 *                     always false for self-references (DBUtils.isIdentifyingAssociation)
 * @param optional     any FK column is nullable (DBUtils.isOptionalAssociation)
 * @param oneToOne     a unique index on the child covers exactly the FK columns (crow's foot "1" vs "1..n")
 * @param comment      constraint comment, may be null
 */
public record Association(
    String name,
    Entity source,
    Entity target,
    List<String> sourceColumns,
    List<String> targetColumns,
    boolean identifying,
    boolean optional,
    boolean oneToOne,
    String comment
) {
    public Association {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        sourceColumns = List.copyOf(sourceColumns);
        targetColumns = List.copyOf(targetColumns);
    }

    public boolean isSelfReference() { return source == target; }
}
