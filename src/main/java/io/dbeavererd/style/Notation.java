package io.dbeavererd.style;

/** Connection notation; DBeaver's default is IDEF1X. */
public enum Notation {
    /** IDEF1X: 10 px rhombus at the parent end -> 12 px between fanned-out ends. */
    IDEF1X(20, 12),
    /** Crow's foot: 10 px bars/circle plus a cardinality label above the line -> 24 px between ends. */
    CROWS_FOOT(30, 24);

    /** Length of the perpendicular stub at each end of a connection (ERDNotation.getIndentation). */
    public final int indentation;
    /** Vertical distance between line ends that share a table side (see ConnectionRouter fan-out). */
    public final int fanStep;

    Notation(int indentation, int fanStep) {
        this.indentation = indentation;
        this.fanStep = fanStep;
    }

    public static Notation parse(String s) {
        return switch (s.trim().toLowerCase().replace("_", "").replace("-", "")) {
            case "idef1x" -> IDEF1X;
            case "crowsfoot", "crow" -> CROWS_FOOT;
            default -> throw new IllegalArgumentException("unknown notation: " + s);
        };
    }
}
