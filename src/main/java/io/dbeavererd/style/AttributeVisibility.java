package io.dbeavererd.style;

/** DBeaver's ERDAttributeVisibility. */
public enum AttributeVisibility {
    ALL, KEYS, PRIMARY, NONE;

    public static AttributeVisibility parse(String s) {
        return valueOf(s.trim().toUpperCase());
    }
}
