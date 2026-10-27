package io.dbeavererd.style;

/** DBeaver's ERDViewStyle flags. Default set is {@link #ICONS} only. */
public enum ViewStyle {
    /** Type icon in front of each column name. */
    ICONS,
    /** Full type name in the right-hand column of each row. */
    TYPES,
    /** " NOT NULL" appended to the right-hand column for required columns. */
    NULLABILITY,
    /** Table comment under the title, column comment after the column name, FK comment on the line. */
    COMMENTS,
    /** Schema-qualified entity title. */
    ENTITY_FQN,
    /** Columns sorted by name (case-insensitive) instead of ordinal position. */
    ALPHABETICAL_ORDER;

    public static ViewStyle parse(String s) {
        return switch (s.trim().toLowerCase()) {
            case "icons" -> ICONS;
            case "types" -> TYPES;
            case "nullability" -> NULLABILITY;
            case "comments" -> COMMENTS;
            case "fqn", "entity_fqn" -> ENTITY_FQN;
            case "alpha", "alphabetical_order" -> ALPHABETICAL_ORDER;
            default -> throw new IllegalArgumentException("unknown view style: " + s);
        };
    }
}
