package io.dbeavererd.render;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a string into runs the given font can display and runs it cannot (e.g. Cyrillic with the
 * bundled Latin-only Selawik). Fonts created from a file never fall back on their own in Java2D, so the
 * undisplayable runs are drawn and measured with a logical sans-serif font of the same size and style,
 * which the JDK maps to the system's fonts with proper script fallback.
 */
public final class TextRuns {
    public record Run(String text, Font font) {
    }

    private TextRuns() {
    }

    public static Font fallbackFor(Font f) {
        return new Font(Font.SANS_SERIF, f.getStyle(), 1).deriveFont(f.getSize2D());
    }

    public static List<Run> split(String s, Font font) {
        List<Run> runs = new ArrayList<>();
        if (s == null || s.isEmpty()) return runs;
        if (font.canDisplayUpTo(s) == -1) {
            runs.add(new Run(s, font));
            return runs;
        }
        Font fallback = fallbackFor(font);
        int start = 0;
        boolean displayable = canDisplay(font, s, 0);
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int len = Character.charCount(cp);
            boolean d = font.canDisplay(cp) || Character.isWhitespace(cp);
            if (d != displayable) {
                runs.add(new Run(s.substring(start, i), displayable ? font : fallback));
                start = i;
                displayable = d;
            }
            i += len;
        }
        runs.add(new Run(s.substring(start), displayable ? font : fallback));
        return runs;
    }

    private static boolean canDisplay(Font font, String s, int index) {
        int cp = s.codePointAt(index);
        return font.canDisplay(cp) || Character.isWhitespace(cp);
    }
}
