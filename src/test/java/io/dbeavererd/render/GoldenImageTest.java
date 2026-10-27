package io.dbeavererd.render;

import io.dbeavererd.style.RenderOptions;
import io.dbeavererd.testutil.Fixtures;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the fixture render with a stored image. Anti-aliasing differs slightly between JDKs, so a
 * small fraction of differing pixels is tolerated. Regenerate with {@code ./gradlew test -Pregolden=true}.
 */
class GoldenImageTest {
    private static final Path GOLDEN = Path.of("src/test/resources/golden/shop.png");
    private static final double MAX_DIFFERENT_PIXELS = 0.02;

    @Test
    void fixtureMatchesGoldenImage() throws Exception {
        RenderOptions o = Fixtures.options();
        BufferedImage actual = PngExporter.render(Pipeline.render(Fixtures.shop(), o));

        if (Boolean.parseBoolean(System.getProperty("regolden", "false")) || !Files.exists(GOLDEN)) {
            Files.createDirectories(GOLDEN.getParent());
            ImageIO.write(actual, "png", GOLDEN.toFile());
            System.out.println("golden image written to " + GOLDEN);
            return;
        }

        BufferedImage expected = ImageIO.read(GOLDEN.toFile());
        assertEquals(expected.getWidth(), actual.getWidth(), "width");
        assertEquals(expected.getHeight(), actual.getHeight(), "height");
        long different = 0;
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                if ((expected.getRGB(x, y) & 0xffffff) != (actual.getRGB(x, y) & 0xffffff)) different++;
            }
        }
        double ratio = (double) different / ((double) expected.getWidth() * expected.getHeight());
        if (ratio > MAX_DIFFERENT_PIXELS) {
            File out = new File("build/test-output/shop-golden-diff.png");
            out.getParentFile().mkdirs();
            ImageIO.write(actual, "png", out);
        }
        assertTrue(ratio <= MAX_DIFFERENT_PIXELS, String.format("%.2f%% of pixels differ from %s", ratio * 100, GOLDEN));
    }
}
