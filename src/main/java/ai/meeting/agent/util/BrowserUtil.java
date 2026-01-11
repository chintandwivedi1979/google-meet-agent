package ai.meeting.agent.util;

import com.microsoft.playwright.*;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BrowserUtil {

    private static final String botName = "Agent Bot";

    public static Browser createStealthBrowser(Playwright playwright) {
        String[] enhancedBrowserArgs = {
                "--no-sandbox",
                "--disable-blink-features=AutomationControlled",
                "--exclude-switches=enable-automation",
                "--disable-extensions",
                "--disable-default-apps",
                "--use-fake-ui-for-media-stream=1",
                "--use-fake-device-for-media-stream=1",
                "--allow-running-insecure-content",
                "--disable-web-security",
                "--ignore-certificate-errors",
                "--ignore-ssl-errors",
                "--disable-dev-shm-usage",
                "--disable-background-timer-throttling",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--disable-field-trial-config",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-sync",
                "--disable-translate",
                "--hide-scrollbars",
                "--mute-audio"
        };

        return playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false) // Set to true to run headlessly
                .setSlowMo(100)
                .setArgs(Arrays.asList(enhancedBrowserArgs))
        );
    }

    public static BrowserContext createStealthContext(Browser browser) {
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("Accept-Language", "en-US,en;q=0.9");
        extraHeaders.put("Accept-Encoding", "gzip, deflate, br");
        extraHeaders.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        extraHeaders.put("Sec-Fetch-Site", "none");
        extraHeaders.put("Sec-Fetch-Mode", "navigate");
        extraHeaders.put("Sec-Fetch-User", "?1");
        extraHeaders.put("Sec-Fetch-Dest", "document");
        extraHeaders.put("Cache-Control", "max-age=0");

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setViewportSize(1420, 780)
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .setExtraHTTPHeaders(extraHeaders)
                .setJavaScriptEnabled(true)
                .setIgnoreHTTPSErrors(true)
                .setPermissions(Arrays.asList("camera", "microphone", "notifications"))
                .setTimezoneId("America/New_York")
                .setLocale("en-US");

        return browser.newContext(contextOptions);
    }

    /**
     * Add comprehensive stealth scripts to avoid bot detection
     */
    public static void addEnhancedStealthScript(Page page) {
        String stealthScript = """
            () => {
                // Remove webdriver property completely
                delete navigator.__proto__.webdriver;
                delete navigator.webdriver;

                // Override webdriver property
                Object.defineProperty(navigator, 'webdriver', {
                    get: () => undefined,
                    configurable: true
                });

                // Mock comprehensive navigator properties
                Object.defineProperty(navigator, 'plugins', {
                    get: () => [
                        {
                            name: 'Chrome PDF Plugin',
                            description: 'Portable Document Format',
                            filename: 'internal-pdf-viewer',
                            length: 1,
                            item: () => null,
                            namedItem: () => null
                        },
                        {
                            name: 'Chrome PDF Viewer',
                            description: 'PDF Viewer',
                            filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai',
                            length: 1,
                            item: () => null,
                            namedItem: () => null
                        }
                    ]
                });

                // Enhanced properties
                Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
                Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });
                Object.defineProperty(navigator, 'platform', { get: () => 'MacIntel' });
                Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 0 });

                // Remove automation indicators
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;
                delete window.__playwright;
                delete window._playwright;

                // Enhanced media devices mock
                if (navigator.mediaDevices) {
                    navigator.mediaDevices.enumerateDevices = async () => {
                        return [
                            {
                                deviceId: 'default',
                                kind: 'audioinput',
                                label: 'MacBook Pro Microphone',
                                groupId: 'a8f2d4e6c1b3f7e9d2c4a6b8e1f3d7c9'
                            },
                            {
                                deviceId: 'aa8c7b4e2f1d9c6b3e7a1f5d8c2b6e9a',
                                kind: 'videoinput',
                                label: 'FaceTime HD Camera',
                                groupId: 'a8f2d4e6c1b3f7e9d2c4a6b8e1f3d7c9'
                            }
                        ];
                    };
                }

                console.info('Stealth mode activated');
            }
            """;

        page.addInitScript(stealthScript);
    }

    /**
     * Enhanced error logging with timestamps and stack traces
     */
    public static void logError(PrintWriter errorLogger, String message, Exception e) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String errorMessage = String.format("[%s] %s: %s", timestamp, message, e.getMessage());

        System.err.println("❌ " + errorMessage);

        if (errorLogger != null) {
            errorLogger.println(errorMessage);
            if (e instanceof RuntimeException) {
                e.printStackTrace(errorLogger);
            }
            errorLogger.flush();
        }
    }

    public static void disableAudioVideo(Page page) {
        try {
            System.out.println("Attempting to disable audio/video...");
            page.waitForTimeout(2000);

            // Disable microphone with enhanced selectors
            String[] micSelectors = {
                    "button[aria-label*='Turn off microphone' i]",
                    "button[aria-label*='microphone' i]",
                    "button[data-testid*='mic']",
                    "button[data-tooltip*='microphone' i]",
                    ".qOwgVe button", // Google Meet specific class
                    "div[role='button'][aria-label*='microphone' i]"
            };

            boolean micDisabled = false;
            for (String selector : micSelectors) {
                try {
                    if (page.locator(selector).count() > 0) {
                        // Check if microphone is currently enabled (button should indicate "turn off")
                        String ariaLabel = page.locator(selector).getAttribute("aria-label");
                        if (ariaLabel != null && ariaLabel.toLowerCase().contains("turn off")) {
                            page.click(selector);
                            System.out.println("✓ Microphone disabled");
                            micDisabled = true;
                            break;
                        } /*else if (ariaLabel != null && ariaLabel.toLowerCase().contains("microphone")) {
                            page.click(selector);
                            System.out.println("✓ Microphone toggled");
                            micDisabled = true;
                            break;
                        }*/
                    }
                } catch (Exception e) {
                    // Continue to next selector
                }
            }

            page.waitForTimeout(1000);

            // Disable camera with enhanced selectors
            String[] cameraSelectors = {
                    "button[aria-label*='Turn off camera' i]",
                    "button[aria-label*='camera' i]",
                    "button[data-testid*='camera']",
                    "button[data-tooltip*='camera' i]",
                    ".GOH7Zb button", // Google Meet specific class
                    "div[role='button'][aria-label*='camera' i]"
            };

            boolean cameraDisabled = false;
            for (String selector : cameraSelectors) {
                try {
                    if (page.locator(selector).count() > 0) {
                        String ariaLabel = page.locator(selector).getAttribute("aria-label");
                        if (ariaLabel != null && ariaLabel.toLowerCase().contains("turn off")) {
                            page.click(selector);
                            System.out.println("✓ Camera disabled");
                            cameraDisabled = true;
                            break;
                        } /*else if (ariaLabel != null && ariaLabel.toLowerCase().contains("camera")) {
                            page.click(selector);
                            System.out.println("✓ Camera toggled");
                            cameraDisabled = true;
                            break;
                        }*/
                    }
                } catch (Exception e) {
                    // Continue to next selector
                }
            }

            if (!micDisabled && !cameraDisabled) {
                System.out.println("⚠ Could not find audio/video controls - they may already be disabled");
            }

            page.waitForTimeout(1000);

        } catch (Exception e) {
            System.out.println("Error disabling audio/video: " + e.getMessage());
        }
    }


    public static void clickJoinButton(Page page) {
        try {
            System.out.println("Looking for join button...");
            page.waitForTimeout(3000);

            String[] joinSelectors = {
                    "button:has-text('Ask to join')",
                    "button:has-text('Join now')",
                    "button:has-text('Join')",
                    "[data-testid='join-flow-upsell-join-button']",
                    ".uArJ5e.UQuaGc.Y5sE8d.uyXBBb.xKiqt", // Google Meet join button class
                    "div[role='button']:has-text('Ask to join')",
                    "div[role='button']:has-text('Join')",
                    "span:has-text('Ask to join'):parent::button",
                    "span:has-text('Join'):parent::button"
            };

            boolean clicked = false;
            for (String selector : joinSelectors) {
                try {
                    if (page.locator(selector).count() > 0) {
                        // Make sure the button is visible and enabled
                        if (page.locator(selector).isVisible()) {
                            page.click(selector);
                            System.out.println("✓ Clicked join button: " + selector);
                            clicked = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Join button selector failed: " + selector + " - " + e.getMessage());
                }
            }

            if (!clicked) {
                System.out.println("Standard join buttons not found, trying alternative approach...");

                // Try to find any clickable element with join text
                try {
                    page.locator("text=Ask to join").click();
                    System.out.println("✓ Clicked 'Ask to join' text element");
                    clicked = true;
                } catch (Exception e) {
                    try {
                        page.locator("text=Join").click();
                        System.out.println("✓ Clicked 'Join' text element");
                        clicked = true;
                    } catch (Exception e2) {
                        System.out.println("⚠ No join button found - manual intervention may be required");
                    }
                }
            }

            if (clicked) {
                page.waitForTimeout(1000); // Wait for the join request to be processed
                System.out.println("Join request submitted, waiting for response...");
            }

        } catch (Exception e) {
            System.out.println("Error clicking join button: " + e.getMessage());
        }
    }

    public static void handleNameInput(Page page, String displayName) {
        try {
            page.waitForTimeout(3000); // Wait for page to fully load

            String[] nameSelectors = {
                    "input[aria-label*='Your name']",
                    "input[placeholder*='Your name']",
                    "input[aria-label*='Name']",
                    "input[data-initial-value]",
                    "input[type='text']"
            };

            boolean nameFieldFound = false;
            for (String selector : nameSelectors) {
                try {
                    if (page.locator(selector).count() > 0) {
                        // Clear existing text and enter name
                        page.fill(selector, "");
                        page.waitForTimeout(500);
                        page.fill(selector, displayName != null ? displayName : botName);
                        page.waitForTimeout(500);

                        // Optionally press Tab or Enter to confirm
                        page.press(selector, "Tab");

                        System.out.println("✓ Entered name: " + (displayName != null ? displayName : botName));
                        nameFieldFound = true;
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("Selector failed: " + selector + " - " + e.getMessage());
                }
            }

            if (!nameFieldFound) {
                System.out.println("⚠ Name input field not found, continuing anyway...");
            }

            page.waitForTimeout(1000); // Wait for UI to update

        } catch (Exception e) {
            System.out.println("Error handling name input: " + e.getMessage());
        }
    }
}
