package io.dbeavererd.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** The whole diagram: entities in display/layout order plus the associations between them. */
public final class Diagram {
    private final String name;
    private final List<Entity> entities = new ArrayList<>();
    private final List<Association> associations = new ArrayList<>();

    public Diagram(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String name() { return name; }
    public List<Entity> entities() { return Collections.unmodifiableList(entities); }
    public List<Association> associations() { return Collections.unmodifiableList(associations); }

    public void addEntity(Entity e) { entities.add(e); }

    public void addAssociation(Association a) {
        if (!entities.contains(a.source()) || !entities.contains(a.target())) {
            throw new IllegalArgumentException("association " + a.name() + " references an entity outside the diagram");
        }
        associations.add(a);
    }

    public boolean hasSelfReference(Entity e) {
        return associations.stream().anyMatch(a -> a.source() == e && a.target() == e);
    }

    /** Associations where {@code e} is the source (FK child). */
    public List<Association> sourceAssociations(Entity e) {
        return associations.stream().filter(a -> a.source() == e).toList();
    }

    public int connectionCount(Entity e) {
        return (int) associations.stream().filter(a -> a.source() == e || a.target() == e).count();
    }

    public boolean isEmpty() { return entities.isEmpty(); }
}
