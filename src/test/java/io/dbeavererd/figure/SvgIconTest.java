package io.dbeavererd.figure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SvgIconTest {

    @Test
    void parsesEveryBundledIcon() throws IOException {
        URL dir = SvgIconTest.class.getResource("/icons");
        assertNotNull(dir);
        Icons icons = new Icons();
        List<Path> files;
        try (Stream<Path> s = Files.walk(Path.of(dir.getPath()))) {
            files = s.filter(p -> p.toString().endsWith(".svg")).toList();
        }
        assertFalse(files.isEmpty());
        for (Path p : files) {
            String id = p.toString().substring(p.toString().indexOf("/icons/") + "/icons/".length()).replace(".svg", "");
            SvgIcon icon = icons.shape(id);
            assertFalse(icon.ops.isEmpty(), id);
            assertEquals(16, icon.width, id);
            assertEquals(16, icons.raster(id, false).getWidth(), id);
        }
        assertEquals(8, icons.keyOverlay().getWidth());
    }
}
