package org.akhq.configs;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoggingConfigTest {
    // files, not classpath resources: src/test/resources has its own logback.xml
    private static final String RESOURCES = "src/main/resources/";

    @Test
    void jsonConfigKeepsDefaultLoggerLevels() throws Exception {
        Map<String, String> json = loggers("logback-json.xml");

        loggers("logback.xml").forEach((name, level) ->
            assertEquals(level, json.get(name), "logger " + name + " in logback-json.xml"));
    }

    @Test
    void log4jApiLogsThroughSlf4j() {
        assertEquals("org.apache.logging.slf4j.SLF4JLogger", LogManager.getLogger(getClass()).getClass().getName());
    }

    private static Map<String, String> loggers(String file) throws Exception {
        NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new File(RESOURCES + file))
            .getElementsByTagName("logger");

        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element logger = (Element) nodes.item(i);
            result.put(logger.getAttribute("name"), logger.getAttribute("level"));
        }
        return result;
    }
}
