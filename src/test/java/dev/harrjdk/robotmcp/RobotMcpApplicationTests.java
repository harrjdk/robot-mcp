package dev.harrjdk.robotmcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full application context load test.
 *
 * Requires a Windows environment with a display because RobotTools creates a
 * java.awt.Robot (which fails headless) and ControlPanelWindow opens an AWT frame.
 * This test is skipped automatically on non-Windows / headless CI agents.
 */
@EnabledOnOs(OS.WINDOWS)
@SpringBootTest(properties = "spring.main.headless=false")
class RobotMcpApplicationTests {

    // @SpringBootTest does not invoke main(), so the headless flag set there is never applied.
    // Set it here before the class is loaded so AWT initialises correctly.
    static {
        System.setProperty("java.awt.headless", "false");
    }

    @Test
    void contextLoads() {
    }
}
