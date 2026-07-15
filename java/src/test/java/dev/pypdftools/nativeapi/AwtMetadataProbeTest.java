package dev.pypdftools.nativeapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import org.junit.jupiter.api.Test;

/** Exercises the headless AWT JNI initialization reachable through PDFBox image filters. */
final class AwtMetadataProbeTest {
    @Test
    void initializesHeadlessAwtJniSurface() {
        assertNotNull(ColorModel.getRGBdefault());
        assertNotNull(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getRaster());
    }
}
