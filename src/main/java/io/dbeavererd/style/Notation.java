package io.dbeavererd.style;

/** Connection notation; DBeaver's default is IDEF1X. */
public enum Notation {
    IDEF1X(20), CROWS_FOOT(30);

    /** Length of the perpendicular stub at each end of a connection (ERDNotation.getIndentation). */
    public final int indentation;

    Notation(int indentation) {
        this.indentation = indentation;
    }

    public static Notation parse(String s) {
        return switch (s.trim().toLowerCase().replace("_", "").replace("-", "")) {
            case "idef1x" -> IDEF1X;
            case "crowsfoot", "crow" -> CROWS_FOOT;
            default -> throw new IllegalArgumentException("unknown notation: " + s);
        };
    }
}
