package io.github.jamerlybob.windroute.gpx;

import org.junit.Test;

import javax.xml.parsers.DocumentBuilderFactory;

/** Behaviour needed when Android's XML parser lacks desktop parser features. */
public class GpxParserCompatibilityTest {

    @Test
    public void unsupportedParserFeatureDoesNotStopImportSetup() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        GpxParser.setFeatureIfSupported(factory,
                "https://windroute.invalid/xml/unsupported-feature", false);
    }
}
