package dev.harrjdk.robotmcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for RobotTools logic.
 * Uses the package-private constructor to inject a Mockito mock of Robot,
 * so no display or OS interaction is required.
 */
class RobotToolsUnitTest {

    private Robot mockRobot;
    private RobotTools tools;

    @BeforeEach
    void setUp() {
        mockRobot = mock(Robot.class);
        tools = new RobotTools(mockRobot);
    }

    // -------------------------------------------------------------------------
    // Mouse
    // -------------------------------------------------------------------------

    @Test
    void mouseMove_returnsConfirmation() {
        assertThat(tools.mouseMove(100, 200)).isEqualTo("Mouse moved to (100, 200)");
        verify(mockRobot).mouseMove(100, 200);
    }

    @Test
    void leftClick_returnsConfirmation() {
        assertThat(tools.leftClick(10, 20)).isEqualTo("Left clicked at (10, 20)");
    }

    @Test
    void doubleClick_returnsConfirmation() {
        assertThat(tools.doubleClick(5, 15)).isEqualTo("Double-clicked at (5, 15)");
        verify(mockRobot, times(2)).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(mockRobot, times(2)).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    @Test
    void rightClick_returnsConfirmation() {
        assertThat(tools.rightClick(30, 40)).isEqualTo("Right clicked at (30, 40)");
    }

    @Test
    void middleClick_returnsConfirmation() {
        assertThat(tools.middleClick(50, 60)).isEqualTo("Middle-clicked at (50, 60)");
    }

    @Test
    void mouseScroll_returnsScrollAmount() {
        assertThat(tools.mouseScroll(0, 0, 3)).isEqualTo("Scrolled 3 at (0, 0)");
        verify(mockRobot).mouseWheel(3);
    }

    @Test
    void mouseScroll_negativeAmount() {
        assertThat(tools.mouseScroll(0, 0, -5)).isEqualTo("Scrolled -5 at (0, 0)");
        verify(mockRobot).mouseWheel(-5);
    }

    @Test
    void mouseDrag_returnsConfirmation() {
        assertThat(tools.mouseDrag(0, 0, 100, 100))
                .isEqualTo("Dragged from (0, 0) to (100, 100)");
        verify(mockRobot).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(mockRobot).mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    // -------------------------------------------------------------------------
    // Keyboard — pressKey
    // -------------------------------------------------------------------------

    @Test
    void pressKey_knownNamedKey_returnsConfirmation() {
        assertThat(tools.pressKey("ENTER")).isEqualTo("Pressed key: ENTER");
        verify(mockRobot).keyPress(java.awt.event.KeyEvent.VK_ENTER);
        verify(mockRobot).keyRelease(java.awt.event.KeyEvent.VK_ENTER);
    }

    @Test
    void pressKey_caseInsensitive() {
        assertThat(tools.pressKey("enter")).isEqualTo("Pressed key: enter");
    }

    @Test
    void pressKey_escAlias() {
        assertThat(tools.pressKey("ESC")).isEqualTo("Pressed key: ESC");
        verify(mockRobot).keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
    }

    @Test
    void pressKey_singleLetter() {
        assertThat(tools.pressKey("A")).isEqualTo("Pressed key: A");
        verify(mockRobot).keyPress(java.awt.event.KeyEvent.VK_A);
    }

    @Test
    void pressKey_unknownKey_returnsError() {
        assertThat(tools.pressKey("NOTAKEY")).isEqualTo("Unknown key: NOTAKEY");
        verifyNoInteractions(mockRobot);
    }

    @Test
    void pressKey_functionKeys() {
        assertThat(tools.pressKey("F5")).isEqualTo("Pressed key: F5");
        verify(mockRobot).keyPress(java.awt.event.KeyEvent.VK_F5);
    }

    // -------------------------------------------------------------------------
    // Keyboard — pressKeyCombination
    // -------------------------------------------------------------------------

    @Test
    void pressKeyCombination_ctrlC() {
        assertThat(tools.pressKeyCombination("CTRL+C")).isEqualTo("Pressed combination: CTRL+C");
        verify(mockRobot).keyPress(java.awt.event.KeyEvent.VK_CONTROL);
        verify(mockRobot).keyPress(java.awt.event.KeyEvent.VK_C);
        // Released in reverse order
        verify(mockRobot).keyRelease(java.awt.event.KeyEvent.VK_C);
        verify(mockRobot).keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
    }

    @Test
    void pressKeyCombination_unknownKey_returnsError() {
        assertThat(tools.pressKeyCombination("CTRL+BADKEY"))
                .isEqualTo("Unknown key: BADKEY");
        verify(mockRobot, never()).keyPress(anyInt());
    }

    @Test
    void pressKeyCombination_threeKeys() {
        assertThat(tools.pressKeyCombination("CTRL+SHIFT+T"))
                .isEqualTo("Pressed combination: CTRL+SHIFT+T");
    }

    // -------------------------------------------------------------------------
    // Keyboard — typeText
    // -------------------------------------------------------------------------

    @Test
    void typeText_plainText_returnsTyped() {
        assertThat(tools.typeText("hello")).isEqualTo("Typed: hello");
    }

    @Test
    void typeText_uppercaseUsesShift() {
        tools.typeText("A");
        verify(mockRobot).keyPress(java.awt.event.KeyEvent.VK_SHIFT);
        verify(mockRobot).keyRelease(java.awt.event.KeyEvent.VK_SHIFT);
    }

    @Test
    void typeText_unmappableCharacterReported() {
        // \u0000 (null char) has no key code
        String result = tools.typeText("a\u0000b");
        assertThat(result).startsWith("Typed: a\u0000b");
        assertThat(result).contains("skipped 1 unmappable character(s)");
    }

    // -------------------------------------------------------------------------
    // Timing
    // -------------------------------------------------------------------------

    @Test
    void sleep_respectsMaxCap() {
        String result = tools.sleep(999_999);
        assertThat(result).isEqualTo("Slept 30000ms");
    }

    @Test
    void sleep_underCap_sleepsRequested() {
        String result = tools.sleep(50);
        assertThat(result).isEqualTo("Slept 50ms");
    }

    @Test
    void sleep_exactCap() {
        assertThat(tools.sleep(30_000)).isEqualTo("Slept 30000ms");
    }

    // -------------------------------------------------------------------------
    // Screen — pixel color
    // -------------------------------------------------------------------------

    @Test
    void findPixelColor_returnsHexString() {
        when(mockRobot.getPixelColor(50, 50)).thenReturn(new Color(255, 0, 128));
        assertThat(tools.findPixelColor(50, 50)).isEqualTo("#FF0080");
    }

    @Test
    void findPixelColor_black() {
        when(mockRobot.getPixelColor(0, 0)).thenReturn(Color.BLACK);
        assertThat(tools.findPixelColor(0, 0)).isEqualTo("#000000");
    }

    @Test
    void findPixelColor_white() {
        when(mockRobot.getPixelColor(0, 0)).thenReturn(Color.WHITE);
        assertThat(tools.findPixelColor(0, 0)).isEqualTo("#FFFFFF");
    }

    // -------------------------------------------------------------------------
    // Screen — waitForPixelColor
    // -------------------------------------------------------------------------

    @Test
    void waitForPixelColor_invalidHex_returnsError() {
        assertThat(tools.waitForPixelColor(0, 0, "ZZZZZZ", 100))
                .isEqualTo("Invalid color: ZZZZZZ");
        verifyNoInteractions(mockRobot);
    }

    @Test
    void waitForPixelColor_withHashPrefix_parsedCorrectly() {
        when(mockRobot.getPixelColor(0, 0)).thenReturn(new Color(0xFF, 0, 0));
        assertThat(tools.waitForPixelColor(0, 0, "#FF0000", 500))
                .isEqualTo("Matched #FF0000 at (0, 0)");
    }

    @Test
    void waitForPixelColor_withoutHashPrefix_parsedCorrectly() {
        when(mockRobot.getPixelColor(0, 0)).thenReturn(new Color(0xFF, 0, 0));
        assertThat(tools.waitForPixelColor(0, 0, "FF0000", 500))
                .isEqualTo("Matched FF0000 at (0, 0)");
    }

    @Test
    void waitForPixelColor_timeout_returnsTimeoutMessage() {
        when(mockRobot.getPixelColor(0, 0)).thenReturn(Color.BLACK);
        String result = tools.waitForPixelColor(0, 0, "#FF0000", 50);
        assertThat(result).startsWith("Timeout:");
        assertThat(result).contains("expected #FF0000");
    }

    // -------------------------------------------------------------------------
    // Screen — findPixelInRegion
    // -------------------------------------------------------------------------

    @Test
    void findPixelInRegion_found_returnsAbsoluteCoordinates() {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        img.setRGB(3, 7, Color.RED.getRGB());
        when(mockRobot.createScreenCapture(any(Rectangle.class))).thenReturn(img);

        // Region starts at (100, 200), pixel is at local (3, 7) → absolute (103, 207)
        assertThat(tools.findPixelInRegion(100, 200, 10, 10, "#FF0000"))
                .isEqualTo("Found #FF0000 at (103, 207)");
    }

    @Test
    void findPixelInRegion_notFound_returnsNotFoundMessage() {
        BufferedImage img = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
        // all pixels black by default
        when(mockRobot.createScreenCapture(any(Rectangle.class))).thenReturn(img);

        assertThat(tools.findPixelInRegion(0, 0, 5, 5, "#FF0000"))
                .isEqualTo("Color #FF0000 not found in region (0, 0, 5x5)");
    }

    @Test
    void findPixelInRegion_invalidHex_returnsError() {
        assertThat(tools.findPixelInRegion(0, 0, 10, 10, "ZZZZZZ"))
                .isEqualTo("Invalid color: ZZZZZZ");
        verifyNoInteractions(mockRobot);
    }

    @Test
    void findPixelInRegion_withHashPrefix_parsedCorrectly() {
        BufferedImage img = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, Color.RED.getRGB());
        when(mockRobot.createScreenCapture(any(Rectangle.class))).thenReturn(img);

        assertThat(tools.findPixelInRegion(0, 0, 5, 5, "#FF0000"))
                .isEqualTo("Found #FF0000 at (0, 0)");
    }

    // -------------------------------------------------------------------------
    // Screen — waitForScreenStable
    // -------------------------------------------------------------------------

    @Test
    void waitForScreenStable_stableImmediately_returnsStableMessage() {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        // same image returned every time → stable after first comparison
        when(mockRobot.createScreenCapture(any(Rectangle.class))).thenReturn(img);

        assertThat(tools.waitForScreenStable(0, 0, 10, 10, 500))
                .isEqualTo("Screen stable in region (0, 0, 10x10)");
    }

    @Test
    void waitForScreenStable_alwaysChanging_returnsTimeoutMessage() {
        // return a different image object with different content each call
        when(mockRobot.createScreenCapture(any(Rectangle.class))).thenAnswer(inv -> {
            BufferedImage img = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, (int) (Math.random() * Integer.MAX_VALUE));
            return img;
        });

        String result = tools.waitForScreenStable(0, 0, 5, 5, 50);
        assertThat(result).startsWith("Timeout:");
        assertThat(result).contains("did not stabilize");
    }

    // -------------------------------------------------------------------------
    // Mouse — scrollUntilPixelColor
    // -------------------------------------------------------------------------

    @Test
    void scrollUntilPixelColor_foundImmediately_doesNotScroll() {
        when(mockRobot.getPixelColor(50, 50)).thenReturn(Color.RED);

        String result = tools.scrollUntilPixelColor(100, 200, 3, 50, 50, "#FF0000", 0, 500);
        assertThat(result).isEqualTo("Found #FF0000 at (50, 50) after 0 scroll step(s)");
        verify(mockRobot, never()).mouseWheel(anyInt());
    }

    @Test
    void scrollUntilPixelColor_foundAfterScrolling_returnsStepCount() {
        when(mockRobot.getPixelColor(50, 50))
                .thenReturn(Color.BLACK)
                .thenReturn(Color.RED);

        String result = tools.scrollUntilPixelColor(100, 200, 3, 50, 50, "#FF0000", 0, 500);
        assertThat(result).isEqualTo("Found #FF0000 at (50, 50) after 1 scroll step(s)");
        verify(mockRobot, times(1)).mouseWheel(3);
    }

    @Test
    void scrollUntilPixelColor_timeout_returnsTimeoutMessage() {
        when(mockRobot.getPixelColor(anyInt(), anyInt())).thenReturn(Color.BLACK);

        String result = tools.scrollUntilPixelColor(0, 0, 3, 0, 0, "#FF0000", 0, 50);
        assertThat(result).startsWith("Timeout:");
        assertThat(result).contains("expected #FF0000");
    }

    @Test
    void scrollUntilPixelColor_invalidHex_returnsError() {
        assertThat(tools.scrollUntilPixelColor(0, 0, 3, 0, 0, "ZZZZZZ", 0, 500))
                .isEqualTo("Invalid color: ZZZZZZ");
        verifyNoInteractions(mockRobot);
    }

    @Test
    void scrollUntilPixelColor_negativeScrollAmount_scrollsUp() {
        when(mockRobot.getPixelColor(0, 0))
                .thenReturn(Color.BLACK)
                .thenReturn(Color.RED);

        tools.scrollUntilPixelColor(0, 0, -3, 0, 0, "#FF0000", 0, 500);
        verify(mockRobot).mouseWheel(-3);
    }
}
