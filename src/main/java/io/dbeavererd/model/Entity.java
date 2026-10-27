package io.dbeavererd.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A table or view in the diagram. Attribute order is the display order. */
public final class Entity {
    public enum Kind { TABLE, VIEW }

    private final String schema;
    private final String name;
    private final Kind kind;
    private final String comment;
    private final List<Attribute> attributes = new ArrayList<>();
    /** DBeaver's "primary" entity: the root table of a single-table diagram (orange header). */
    private boolean primary;
    /** Index of the containing schema among the diagram's schemas; 0 = first (regular header colour). */
    private int containerIndex;

    public Entity(String schema, String name, Kind kind, String comment) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.comment = comment;
    }

    public String schema() { return schema; }
    public String name() { return name; }
    public Kind kind() { return kind; }
    public String comment() { return comment; }
    public boolean isView() { return kind == Kind.VIEW; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
    public int containerIndex() { return containerIndex; }
    public void setContainerIndex(int containerIndex) { this.containerIndex = containerIndex; }

    public String fullName() { return schema + "." + name; }

    public List<Attribute> attributes() { return Collections.unmodifiableList(attributes); }
    public void addAttribute(Attribute a) { attributes.add(a); }
    public void setAttributes(List<Attribute> list) { attributes.clear(); attributes.addAll(list); }

    public List<Attribute> keyAttributes() {
        return attributes.stream().filter(Attribute::inPrimaryKey).toList();
    }

    public List<Attribute> nonKeyAttributes() {
        return attributes.stream().filter(a -> !a.inPrimaryKey()).toList();
    }

    @Override
    public String toString() { return fullName(); }
}
