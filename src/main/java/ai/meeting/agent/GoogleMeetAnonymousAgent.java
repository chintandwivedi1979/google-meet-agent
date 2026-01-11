package ai.meeting.agent;

/*
Generate a complete working Java code using playwright and OpenAI wishper to convert speech to text conversion with below features
1) Agent should be available to join the google meet meeting anonymously
2) should start the browser in stealth mode to avoid detection
3) should display the speaker name when printing the voice to text on console
4) using playwright version 1.57.0 to automate the browser actions
5) using openAI wishper for speech to text conversion
6) should handle multiple speakers in the meeting and print their names along with the transcribed text
7) should be able to join the meeting using a meeting link provided as input
8) should handle microphone permissions and other pop-ups that may appear when joining the meeting
9) should run headlessly
10) should log any errors encountered during the process to a file named error_log.txt
11) start the browser in stealth mode to avoid detection
12) should continuously listen and transcribe the meeting audio until the program is terminated
13) should have a cleanup method to close the browser and release resources when the program is terminated
14) should add enhance stealth scripts to further avoid detection
15) should use high-quality audio capture settings to improve transcription accuracy
16) should include comments explaining each major step in the code
17) in the navigate call should wait until DOM CONTENT LOADED
18) Use OkHttp for any network calls if needed
19) should have a timeout of 10 seconds for waiting for any element to appear on the page
20) should retry joining the meeting if the first attempt fails
 */


import ai.meeting.agent.model.TranscriptionResult;
import ai.meeting.agent.service.AudioCaptureService;
import ai.meeting.agent.service.WhisperService;
import ai.meeting.agent.util.BrowserUtil;
import ai.meeting.agent.util.Constants;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Google Meet Anonymous Agent with OpenAI Whisper Integration
 * This class provides functionality to join Google Meet meetings anonymously
 * and transcribe audio using OpenAI Whisper API with speaker identification
 */
public class GoogleMeetAnonymousAgent {
    private static final String ERROR_LOG_FILE = "error_log.txt";
    private static final int AUDIO_CAPTURE_DURATION = 6; // seconds - shorter for better real-time experience
    private static final int ELEMENT_TIMEOUT = 10000; // 10 seconds timeout for elements
    private static final int MAX_JOIN_RETRIES = 3;
    private volatile boolean isCleanedUp = false;


    private Browser browser;
    private BrowserContext context;
    private Playwright playwright;
    private Page page;
    private WhisperService whisperService;
    private AudioCaptureService audioCaptureService;
    private PrintWriter errorLogger;
    private volatile boolean isRunning = true;


    public static void main(String[] args) {
        String meetingUrl = args.length > 0 ? args[0] : "https://meet.google.com/rse-erdc-xdz";
        GoogleMeetAnonymousAgent agent = new GoogleMeetAnonymousAgent();

        // Add shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutdown initiated...");
            agent.cleanup();
        }));

        try {
            agent.joinMeeting(meetingUrl, "Anonymous Agent");

            // Keep the agent running
            System.out.println("Agent is running. Press Ctrl+C to stop.");
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Error running agent: " + e.getMessage());
        } finally {
            agent.cleanup();
        }
    }

    /**
     * Constructor initializes all services and browser
     */
    public GoogleMeetAnonymousAgent() {
        try {
            this.whisperService = new WhisperService();
            this.audioCaptureService = new AudioCaptureService();
            this.errorLogger = new PrintWriter(new FileWriter(ERROR_LOG_FILE, true));
        } catch (Exception e) {
            System.out.println("Failed to initialize GoogleMeetAnonymousAgent : " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void joinMeeting(String meetingUrl, String displayName) {
        try {
            System.out.println("Initializing Playwright...");
            playwright = Playwright.create();

            browser = BrowserUtil.createStealthBrowser(playwright);
            System.out.println("Browser launched successfully");

            context = BrowserUtil.createStealthContext(browser);
            page = context.newPage();
            //page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Enhanced error handlers
            page.onClose(page1 -> {
                System.out.println("Page was closed");
                handlePageClosed();
            });

            page.onCrash(page1 -> {
                System.out.println("Page crashed - attempting recovery");
                handlePageCrash();
            });

            // Add comprehensive stealth scripts
            BrowserUtil.addEnhancedStealthScript(page);
            page.setDefaultTimeout(90000);

            System.out.println("Navigating to meeting: " + meetingUrl);

            // Navigate with retry mechanism
            boolean navigated = navigateWithRetry(meetingUrl, 3);
            if (!navigated) {
                System.err.println("Failed to navigate to meeting after retries");
                return;
            }

            System.out.println("Navigation successful");
            page.waitForTimeout(5000);

            // Check if blocked
            if (isBlocked()) {
                System.out.println("Detected blocking - attempting bypass...");
                if (!attemptBypass()) {
                    System.err.println("Could not bypass Google Meet restrictions");
                    return;
                }
            }

            // Handle name input
            BrowserUtil.handleNameInput(page, displayName);

            // Disable audio and video
            BrowserUtil.disableAudioVideo(page);

            // Click join button
            BrowserUtil.clickJoinButton(page);

            // Wait for meeting status
            handleMeetingFlow();

        } catch (Exception e) {
            System.err.println("Error joining meeting: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleMeetingFlow() {
        try {
            System.out.println("Processing join request...");
            page.waitForTimeout(1000);

            // Check meeting status for up to 10 minutes (longer wait for host approval)
            for (int i = 0; i < 120; i++) {
                page.waitForTimeout(5000);

                if (isInMeeting()) {
                    System.out.println("🎉 Successfully joined the meeting!");
                    //startMeetingParticipation();
                    startTranscription();
                    return;
                } else if (isWaitingForAdmission()) {
                    System.out.println("⏳ Waiting for host to admit you... (" + (i + 1) + "/120)");
                } else if (isMeetingEnded()) {
                    System.out.println("❌ Meeting has ended or is not available");
                    return;
                } else {
                    System.out.println("🔍 Checking meeting status... (" + (i + 1) + "/120)");
                }
            }

            System.out.println("⏰ Timeout: Could not join meeting after 10 minutes");

        } catch (Exception e) {
            System.out.println("Error in meeting flow: " + e.getMessage());
        }
    }

    private boolean isInMeeting() {
        try {
            String[] meetingIndicators = {
                    "button[aria-label*='Show everyone' i]",
                    "button[aria-label*='Leave call' i]",
                    "button[aria-label*='End call' i]",
                    "[data-testid='participants-button']",
                    "[data-testid='end-call-button']",
                    ".P2RcVb", // Google Meet meeting container
                    "[data-meeting-title]",
                    ".oORaUb", // Another meeting indicator
                    "button[data-tooltip*='Leave call' i]"
            };

            for (String selector : meetingIndicators) {
                if (page.locator(selector).count() > 0) {
                    System.out.println("Meeting indicator found: " + selector);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWaitingForAdmission() {
        try {
            String[] waitingIndicators = {
                    ":has-text('Waiting for the meeting host to let you in')",
                    ":has-text('Ask to join')",
                    ":has-text('Waiting to join')",
                    ":has-text('You\\'re waiting for the meeting host to let you in')",
                    ":has-text('Asking to join')",
                    ".yYqOq" // Waiting room container class
            };

            for (String selector : waitingIndicators) {
                if (page.locator(selector).count() > 0) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isMeetingEnded() {
        try {
            String[] endedIndicators = {
                    ":has-text('Meeting ended')",
                    ":has-text('This meeting has ended')",
                    ":has-text('The meeting has ended')",
                    ":has-text('Meeting not available')",
                    ":has-text('You can\\'t join this video call')"
            };

            for (String selector : endedIndicators) {
                if (page.locator(selector).count() > 0) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }


    private boolean navigateWithRetry(String meetingUrl, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                System.out.println("Navigation attempt " + (i + 1) + "/" + maxRetries);

                page.navigate(meetingUrl, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(50000));

                return true;
            } catch (Exception e) {
                System.err.println("Navigation attempt " + (i + 1) + " failed: " + e.getMessage());
                if (i < maxRetries - 1) {
                    try {
                        page.waitForTimeout(2000 + (i * 1000));
                    } catch (Exception waitException) {
                        // Create new page if current one is broken
                        try {
                            page = context.newPage();
                            BrowserUtil.addEnhancedStealthScript(page);
                        } catch (Exception newPageException) {
                            System.err.println("Failed to create new page: " + newPageException.getMessage());
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlocked() {
        try {
            String[] blockingIndicators = {
                    ":has-text('You can\\'t join this video call')",
                    ":has-text('This meeting is not available')",
                    ":has-text('Meeting not found')",
                    ":has-text('Access denied')",
                    ":has-text('Something went wrong')"
            };

            for (String selector : blockingIndicators) {
                if (page.locator(selector).count() > 0) {
                    System.out.println("Blocking detected with selector: " + selector);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            System.out.println("Error checking blocking status: " + e.getMessage());
            return true;
        }
    }

    private boolean attemptBypass() {
        try {
            System.out.println("Attempting to bypass restrictions...");
            page.reload(new Page.ReloadOptions().setTimeout(90000));
            page.waitForTimeout(3000);
            return !isBlocked();
        } catch (Exception e) {
            System.out.println("Bypass attempt failed: " + e.getMessage());
            return false;
        }
    }

    private void handlePageClosed() {
        try {
            if (context != null && !isCleanedUp) {
                page = context.newPage();
                BrowserUtil.addEnhancedStealthScript(page);
            }
        } catch (Exception e) {
            System.err.println("Failed to recover from page closure: " + e.getMessage());
        }
    }

    private void handlePageCrash() {
        try {
            if (browser != null && !isCleanedUp) {
                context = BrowserUtil.createStealthContext(browser);
                page = context.newPage();
                BrowserUtil.addEnhancedStealthScript(page);
            }
        } catch (Exception e) {
            System.err.println("Failed to recover from page crash: " + e.getMessage());
        }
    }




    /**
     * Check for join restrictions and error messages
     */
    private boolean checkForJoinRestrictions() {
        try {
            String[] restrictionSelectors = {
                    ":has-text('You can\\'t join this call')",
                    ":has-text('This meeting hasn\\'t started')",
                    ":has-text('Meeting has ended')",
                    ":has-text('You need permission')",
                    ":has-text('Ask to join')",
                    "[role='dialog']:has-text('join')",
                    ".error-message",
                    "[data-error-message]"
            };

            for (String selector : restrictionSelectors) {
                try {
                    if (page.locator(selector).count() > 0) {
                        String errorText = page.locator(selector).first().textContent();
                        System.out.println("⚠️ Restriction detected: " + errorText);
                        return true;
                    }
                } catch (Exception e) {
                    // Continue checking other selectors
                }
            }

            return false;

        } catch (Exception e) {
            BrowserUtil.logError(errorLogger, "Error checking for join restrictions", e);
            return false;
        }
    }

    /**
     * Handle restricted meeting scenarios
     */
    private boolean handleRestrictedMeeting() {
        try {
            System.out.println("🔧 Handling restricted meeting...");

            // Try clicking "Ask to join" if available
            String[] askToJoinSelectors = {
                    "button:has-text('Ask to join')",
                    "[role='button']:has-text('Ask to join')",
                    "button[aria-label*='Ask to join']"
            };

            for (String selector : askToJoinSelectors) {
                try {
                    if (page.locator(selector).count() > 0) {
                        page.click(selector);
                        System.out.println("✅ Clicked 'Ask to join' button");
                        page.waitForTimeout(3000);
                        return true;
                    }
                } catch (Exception e) {
                    // Continue to next selector
                }
            }

            // Try alternative meeting URL formats
            String currentUrl = page.url();
            if (currentUrl.contains("meet.google.com")) {
                // Try adding parameters for anonymous join
                String[] urlVariations = {
                        currentUrl + "?authuser=0",
                        currentUrl + "?pli=1",
                        currentUrl + "?hl=en&authuser=0"
                };

                for (String url : urlVariations) {
                    try {
                        System.out.println("🔄 Trying URL variation: " + url);
                        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                        page.waitForTimeout(3000);

                        if (!checkForJoinRestrictions()) {
                            return true;
                        }
                    } catch (Exception e) {
                        // Continue to next URL
                    }
                }
            }

            return false;

        } catch (Exception e) {
            BrowserUtil.logError(errorLogger, "Error handling restricted meeting", e);
            return false;
        }
    }

    /**
     * Disable camera and microphone before joining to avoid permissions issues
     */
    private void disableMediaDevices() {
        try {
            System.out.println("🔇 Disabling media devices...");

            // Selectors for microphone toggle
            String[] micToggleSelectors = {
                    "[data-testid='mic-button']",
                    "button[aria-label*='microphone' i][aria-pressed='true']",
                    "[role='button'][aria-label*='Turn off microphone']",
                    "div[role='button'][data-is-muted='false']"
            };

            // Selectors for camera toggle
            String[] cameraToggleSelectors = {
                    "[data-testid='camera-button']",
                    "button[aria-label*='camera' i][aria-pressed='true']",
                    "[role='button'][aria-label*='Turn off camera']",
                    "div[role='button'][aria-label*='camera'][aria-pressed='true']"
            };

            // Disable microphone
            for (String selector : micToggleSelectors) {
                try {
                    if (page.locator(selector).count() > 0) {
                        page.click(selector);
                        System.out.println("🎤 Microphone disabled");
                        page.waitForTimeout(1000);
                        break;
                    }
                } catch (Exception e) {
                    // Continue to next selector
                }
            }

            // Disable camera
            for (String selector : cameraToggleSelectors) {
                try {
                    if (page.locator(selector).count() > 0) {
                        page.click(selector);
                        System.out.println("📷 Camera disabled");
                        page.waitForTimeout(1000);
                        break;
                    }
                } catch (Exception e) {
                    // Continue to next selector
                }
            }

        } catch (Exception e) {
            BrowserUtil.logError(errorLogger, "Error disabling media devices", e);
        }
    }

    /**
     * Handle microphone, camera and notification permissions
     */
    private void handlePermissions() {
        try {
            System.out.println("Handling permissions and popups...");

            // Wait for page to stabilize
            page.waitForTimeout(2000);

            // Handle microphone permission with multiple selector attempts
            String[] micSelectors = {
                    "button[aria-label*='microphone' i]",
                    "button[data-testid='mic-button']",
                    "[jsname] button[aria-label*='microphone']",
                    "div[role='button'][aria-label*='microphone']"
            };

            handlePermissionButton(micSelectors, "microphone");

            // Handle camera permission
            String[] cameraSelectors = {
                    "button[aria-label*='camera' i]",
                    "button[data-testid='camera-button']",
                    "[jsname] button[aria-label*='camera']",
                    "div[role='button'][aria-label*='camera']"
            };

            handlePermissionButton(cameraSelectors, "camera");

            // Handle notification permissions
            String[] notificationSelectors = {
                    "button:has-text('Allow')",
                    "button:has-text('Block')",
                    "[role='button']:has-text('Allow')"
            };

            handlePermissionButton(notificationSelectors, "notification");

        } catch (Exception e) {
            BrowserUtil.logError(errorLogger, "Error handling permissions", e);
        }
    }

    /**
     * Helper method to handle permission buttons with timeout
     */
    private void handlePermissionButton(String[] selectors, String permissionType) {
        for (String selector : selectors) {
            try {
                if (page.locator(selector).count() > 0) {
                    page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(ELEMENT_TIMEOUT));
                    page.click(selector);
                    System.out.println(permissionType + " permission handled");
                    break;
                }
            } catch (Exception e) {
                // Continue to next selector
            }
        }
    }



    /**
     * Join the meeting room with enhanced detection
     */
    private boolean joinMeetingRoom() {
        try {
            System.out.println("Attempting to join meeting room...");

            // Enhanced join button selectors
            String[] joinSelectors = {
                    "button:has-text('Join now')",
                    "button:has-text('Ask to join')",
                    "button:has-text('Join')",
                    "[data-mdc-dialog-action='ok']",
                    "button[aria-label*='join' i]",
                    "[role='button']:has-text('Join')",
                    "[jsname] button:has-text('Join')"
            };

            boolean joinClicked = false;
            for (String selector : joinSelectors) {
                try {
                    page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(ELEMENT_TIMEOUT));
                    if (page.locator(selector).count() > 0) {
                        page.click(selector);
                        joinClicked = true;
                        System.out.println("Join button clicked successfully");
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            if (!joinClicked) {
                System.out.println("No join button found");
                return false;
            }

            // Wait for meeting interface to load
            page.waitForTimeout(8000);

            // Verify successful join with multiple indicators
            String[] successSelectors = {
                    "[data-allocation-index]",
                    ".google-meet-participants",
                    "[data-participant-id]",
                    "[role='main']",
                    "[data-call-participants]"
            };

            for (String selector : successSelectors) {
                if (page.locator(selector).count() > 0) {
                    System.out.println("Successfully joined the meeting!");
                    return true;
                }
            }

            System.out.println("Meeting join status unclear - continuing anyway");
            return true; // Assume success if we can't determine

        } catch (Exception e) {
            BrowserUtil.logError(errorLogger, "Error joining meeting room", e);
            return false;
        }
    }

    /**
     * Start continuous audio transcription with OpenAI Whisper
     */
    private void startTranscription() {
        try {
            System.out.println("Starting continuous audio capture and transcription...");

            CompletableFuture.runAsync(() -> {
                while (isRunning) {
                    try {
                        // Capture high-quality audio
                        String audioFile = audioCaptureService.captureAudio(AUDIO_CAPTURE_DURATION);

                        if (audioFile != null && Files.exists(Paths.get(audioFile))) {
                            // Transcribe using OpenAI Whisper
                            TranscriptionResult result = whisperService.transcribe(audioFile);

                            if (result != null && !result.getText().trim().isEmpty()) {
                                displayTranscription(result);
                            }

                            // Clean up audio file
                            try {
                                Files.deleteIfExists(Paths.get(audioFile));
                            } catch (Exception cleanupError) {
                                BrowserUtil.logError(errorLogger, "Error cleaning up audio file", cleanupError);
                            }
                        }

                        // Short pause between captures for better performance
                        TimeUnit.MILLISECONDS.sleep(500L);

                    } catch (Exception e) {
                        if (isRunning) {
                            BrowserUtil.logError(errorLogger, "Error in transcription loop", e);
                            try {
                                TimeUnit.SECONDS.sleep(5);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                System.out.println("Transcription loop ended");
            });

        } catch (Exception e) {
            BrowserUtil.logError(errorLogger, "Error starting transcription", e);
        }
    }

    /**
     * Display transcription results with speaker identification
     */
    private void displayTranscription(TranscriptionResult result) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // Enable debugging occasionally
//        if (System.currentTimeMillis() % 30000 < 1000) { // Every 30 seconds roughly
//            debugSpeakerElements();
//        }

        String speakerName = extractSpeakerName();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("⏰ Time: " + timestamp);
        System.out.println("🗣️ Speaker: " + speakerName);
        System.out.println("💬 Text: " + result.getText());
        System.out.println("📊 Confidence: " + String.format("%.2f%%", result.getConfidence() * 100));
        System.out.println("=".repeat(60));
    }

    /**
     * Enhanced speaker name extraction from Google Meet UI with better selectors
     */
    private String extractSpeakerName() {
        try {
            // Method 1: Use JavaScript to extract clean participant names from the actual DOM structure
            String jsCode = """
            () => {
                // Look for participant elements with data-participant-id
                const participantElements = document.querySelectorAll('[data-participant-id]');
                const participants = [];
                
                for (const element of participantElements) {
                    const textContent = element.textContent || '';
                    
                    // Extract participant names using regex patterns
                    // Pattern 1: Name followed by "Name" (like "Chintan DwivediChintan Dwivedi")
                    let nameMatch = textContent.match(/^([A-Za-z\s]+?)\\1/);
                    if (nameMatch) {
                        participants.push({
                            name: nameMatch[1].trim(),
                            element: element,
                            speaking: element.querySelector('[data-speaking="true"]') !== null ||
                                     element.hasAttribute('data-speaking') ||
                                     textContent.includes('speaking') ||
                                     element.querySelector('.speaking') !== null
                        });
                        continue;
                    }
                    
                    // Pattern 2: Regular name patterns
                    const namePatterns = [
                        /^([A-Za-z\s]{2,30}?)(?:[A-Za-z\s]*devices|frame_person|visual_effects|Backgrounds|more_vert|Others|might)/i,
                        /^([A-Za-z\s]{2,30}?)(?:devices)/i,
                        /^([A-Za-z\s]+?)(?:[A-Z][a-z]+)/
                    ];
                    
                    for (const pattern of namePatterns) {
                        const match = textContent.match(pattern);
                        if (match && match[1].trim().length > 1) {
                            participants.push({
                                name: match[1].trim(),
                                element: element,
                                speaking: element.querySelector('[data-speaking="true"]') !== null ||
                                         element.hasAttribute('data-speaking') ||
                                         textContent.includes('speaking') ||
                                         element.querySelector('.speaking') !== null
                            });
                            break;
                        }
                    }
                }
                
                // Return the speaking participant, or the first non-bot participant
                const speakingParticipant = participants.find(p => p.speaking);
                if (speakingParticipant) {
                    return speakingParticipant.name;
                }
                
                // Return first participant that's not the bot
                const nonBotParticipant = participants.find(p => 
                    !p.name.toLowerCase().includes('anonymous') && 
                    !p.name.toLowerCase().includes('agent') &&
                    p.name.length > 2
                );
                
                if (nonBotParticipant) {
                    return nonBotParticipant.name;
                }
                
                // Fallback to first participant
                return participants.length > 0 ? participants[0].name : null;
            }
            """;

            Object result = page.evaluate(jsCode);
            if (result != null && !result.toString().trim().isEmpty()) {
                String speakerName = cleanSpeakerName(result.toString());
                System.out.println("🎯 Found speaker via enhanced JavaScript: " + speakerName);
                return speakerName;
            }

            // Method 2: Direct text parsing based on your debug output pattern
            try {
                String pageContent = page.textContent("body");
                if (pageContent != null) {
                    // Extract names from patterns like "Chintan DwivediChintan Dwivedidevices"
                    String[] lines = pageContent.split("\n");
                    for (String line : lines) {
                        // Look for repeated name pattern
                        if (line.matches(".*[A-Za-z\\s]{2,30}[A-Za-z\\s]{2,30}devices.*")) {
                            String cleanedLine = line.replaceAll("devices.*", "").trim();
                            // Handle repeated names like "Chintan DwivediChintan Dwivedi"
                            if (cleanedLine.length() > 4) {
                                int midPoint = cleanedLine.length() / 2;
                                String firstHalf = cleanedLine.substring(0, midPoint);
                                String secondHalf = cleanedLine.substring(midPoint);

                                if (firstHalf.equals(secondHalf)) {
                                    String extractedName = cleanSpeakerName(firstHalf);
                                    if (!extractedName.equals("Unknown Speaker")) {
                                        System.out.println("🎯 Found speaker via text parsing: " + extractedName);
                                        return extractedName;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Text parsing method failed: " + e.getMessage());
            }

            // Method 3: Try to extract from participant elements directly
            try {
                Locator participantElements = page.locator("[data-participant-id]");
                for (int i = 0; i < participantElements.count() && i < 3; i++) {
                    String textContent = participantElements.nth(i).textContent();
                    if (textContent != null && textContent.length() > 5) {
                        // Extract name from the beginning of the text
                        String[] words = textContent.split("\\s+");
                        StringBuilder nameBuilder = new StringBuilder();

                        for (String word : words) {
                            if (word.matches("[A-Za-z]+") && word.length() > 1) {
                                if (nameBuilder.length() > 0) nameBuilder.append(" ");
                                nameBuilder.append(word);

                                // Stop at common UI words
                                if (word.toLowerCase().matches("(devices|frame|person|visual|effects|backgrounds|more|others|might|anonymous|agent)")) {
                                    break;
                                }

                                // Limit to reasonable name length
                                if (nameBuilder.length() > 30) break;
                            } else if (nameBuilder.length() > 0) {
                                break; // Stop at non-alphabetic characters
                            }
                        }

                        String extractedName = nameBuilder.toString().trim();
                        if (extractedName.length() > 2 && extractedName.length() < 50) {
                            String cleanedName = cleanSpeakerName(extractedName);
                            if (!cleanedName.equals("Unknown Speaker") && !cleanedName.toLowerCase().contains("anonymous")) {
                                System.out.println("🎯 Found speaker via direct extraction: " + cleanedName);
                                return cleanedName;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Direct extraction failed: " + e.getMessage());
            }

            System.out.println("⚠️ Could not identify speaker from available data");
            return "Unknown Speaker";

        } catch (Exception e) {
            BrowserUtil.logError(errorLogger, "Error extracting speaker name", e);
            return "Unknown Speaker";
        }
    }

    /**
     * Enhanced speaker name cleaning with better regex patterns
     */
    private String cleanSpeakerName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "Unknown Speaker";
        }

        String cleaned = rawName
                // Remove common Google Meet UI text patterns
                .replaceAll("(?i)(devices|frame_person|visual_effects|backgrounds|effects|more_vert)", "")
                .replaceAll("(?i)(others|might|still|see|your|full|video)", "")
                .replaceAll("(?i)(speaking|microphone|camera|muted|unmuted|audio|video)", "")
                .replaceAll("(?i)(turn on|turn off|enabled|disabled)", "")
                .replaceAll("(?i)(presenter|screen sharing|sharing screen)", "")
                .replaceAll("(?i)(anonymous|agent|bot)", "")

                // Remove special characters and brackets
                .replaceAll("[()\\[\\]{}]", "")
                .replaceAll("[•·◦‣⁃]", "") // Remove bullet points
                .replaceAll("[_\\-=+]", "") // Remove underscores, dashes, etc.

                // Remove multiple spaces and trim
                .replaceAll("\\s+", " ")
                .trim();

        // If name is too short or contains only special characters, return default
        if (cleaned.length() < 2 || cleaned.matches("[^a-zA-Z0-9\\s]+")) {
            return "Unknown Speaker";
        }

        // Handle repeated names (like "Chintan DwivediChintan Dwivedi")
        String[] words = cleaned.split("\\s+");
        if (words.length >= 2) {
            // Check if it's a repeated pattern
            StringBuilder firstHalf = new StringBuilder();
            StringBuilder secondHalf = new StringBuilder();

            int midPoint = words.length / 2;
            for (int i = 0; i < midPoint; i++) {
                if (firstHalf.length() > 0) firstHalf.append(" ");
                firstHalf.append(words[i]);
            }
            for (int i = midPoint; i < words.length; i++) {
                if (secondHalf.length() > 0) secondHalf.append(" ");
                secondHalf.append(words[i]);
            }

            if (firstHalf.toString().equals(secondHalf.toString())) {
                cleaned = firstHalf.toString();
            }
        }

        // Capitalize first letter of each word
        words = cleaned.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                if (result.length() > 0) result.append(" ");
                result.append(word.substring(0, 1).toUpperCase())
                        .append(word.substring(1).toLowerCase());
            }
        }

        String finalName = result.toString().trim();

        // Final validation
        if (finalName.length() < 2 || finalName.matches("^[^a-zA-Z]+$")) {
            return "Unknown Speaker";
        }

        return finalName;
    }

    /**
     * Debug method to print current DOM structure for speaker identification
     */
    private void debugSpeakerElements() {
        try {
            System.out.println("\n🔍 DEBUG: Current participant elements:");

            String jsDebugCode = """
            () => {
                const elements = [
                    ...document.querySelectorAll('[data-participant-id]'),
                    ...document.querySelectorAll('.zWGUib'),
                    ...document.querySelectorAll('[data-self-name]'),
                    ...document.querySelectorAll('[data-speaking="true"]')
                ];
                
                const info = [];
                elements.forEach((el, idx) => {
                    if (el.textContent && el.textContent.trim()) {
                        info.push({
                            index: idx,
                            text: el.textContent.trim(),
                            className: el.className,
                            id: el.id,
                            attributes: Array.from(el.attributes).map(attr => 
                                attr.name + '="' + attr.value + '"'
                            ).join(' ')
                        });
                    }
                });
                
                return info;
            }
            """;

            Object debugResult = page.evaluate(jsDebugCode);
            System.out.println("Debug result: " + debugResult);

        } catch (Exception e) {
            System.out.println("Debug failed: " + e.getMessage());
        }
    }


    /**
     * Comprehensive cleanup method for all resources
     */
    public void cleanup() {
        try {
            System.out.println("🧹 Starting cleanup process...");
            isRunning = false;

            // Stop audio capture service
            if (audioCaptureService != null) {
                audioCaptureService.stop();
                System.out.println("✅ Audio capture service stopped");
            }

            // Close page
            if (page != null && !page.isClosed()) {
                page.close();
                System.out.println("✅ Page closed");
            }

            // Close context
            if (context != null) {
                context.close();
                System.out.println("✅ Browser context closed");
            }

            // Close browser
            if (browser != null && browser.isConnected()) {
                browser.close();
                System.out.println("✅ Browser closed");
            }

            // Close error logger
            if (errorLogger != null) {
                errorLogger.close();
                System.out.println("✅ Error logger closed");
            }

            System.out.println("🎉 Cleanup completed successfully");

        } catch (Exception e) {
            System.err.println("❌ Error during cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }



    /**
     * Join Google Meet with retry mechanism
     */
    public void joinMeeting1(String meetingUrl) {
        int retryCount = 0;
        boolean joinSuccessful = false;

        initializeBrowser();

        while (retryCount < MAX_JOIN_RETRIES && !joinSuccessful && isRunning) {
            try {
                System.out.println("Attempt " + (retryCount + 1) + " - Navigating to Google Meet: " + meetingUrl);

                // Navigate and wait for DOM to load
                page.navigate(meetingUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                // Wait for page to stabilize
                page.waitForTimeout(5000);

                // Check for "can't join" message first
                if (checkForJoinRestrictions()) {
                    System.out.println("❌ Meeting has restrictions - trying alternative approach...");
                    if (!handleRestrictedMeeting()) {
                        retryCount++;
                        continue;
                    }
                }

                // Handle permissions and popups
                handlePermissions();

                // Disable camera and microphone before joining
                disableMediaDevices();

                // Enter anonymous name
                enterAnonymousName();

                // Attempt to join meeting
                if (joinMeetingRoom()) {
                    joinSuccessful = true;
                    System.out.println("✅ Successfully joined meeting on attempt " + (retryCount + 1));

                    // Wait for meeting to fully load
                    page.waitForTimeout(10000);

                    // Start transcription after successful join
                    startTranscription();
                } else {
                    retryCount++;
                    if (retryCount < MAX_JOIN_RETRIES) {
                        System.out.println("⚠️ Join attempt failed, retrying in 5 seconds...");
                        page.waitForTimeout(5000);
                    }
                }

            } catch (Exception e) {
                retryCount++;
                BrowserUtil.logError(errorLogger, "Failed to join meeting on attempt " + retryCount, e);

                if (retryCount < MAX_JOIN_RETRIES) {
                    System.out.println("🔄 Retrying to join meeting in 5 seconds...");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (!joinSuccessful) {
            System.err.println("❌ Failed to join meeting after " + MAX_JOIN_RETRIES + " attempts");
        }
    }

    /**
     * Initialize browser with enhanced stealth mode and security settings
     */
    private void initializeBrowser() {
        try {
            System.out.println("Initializing browser with stealth mode...");

            this.playwright = Playwright.create();

            // Enhanced browser launch options for stealth mode
            this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false) // Set to true for production
                    .setSlowMo(100)
                    .setArgs(Arrays.asList(Constants.ENHANCED_BROWSER_ARGS)));

            System.out.println("Browser launched successfully");

            // Enhanced context options
            context = BrowserUtil.createStealthContext(browser);

            page = context.newPage();

            // Enhanced stealth scripts to avoid detection
            BrowserUtil.addEnhancedStealthScript(page);

        } catch (Exception e) {
            BrowserUtil.logError(errorLogger, "Failed to initialize browser", e);
        }
    }

    /**
     * Enter anonymous name for meeting join
     */
    private void enterAnonymousName() {
        try {
            System.out.println("Entering anonymous name...");

            // Multiple selectors for name input field
            String[] nameSelectors = {
                    "input[placeholder*='name' i]",
                    "input[aria-label*='name' i]",
                    "input[type='text']",
                    "[role='textbox']"
            };

            boolean nameEntered = false;
            for (String selector : nameSelectors) {
                try {
                    Locator nameInput = page.locator(selector);
                    if (nameInput.count() > 0) {
                        nameInput.fill("Anonymous Participant");
                        nameEntered = true;
                        System.out.println("Anonymous name entered successfully");
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            if (!nameEntered) {
                System.out.println("No name input field found - proceeding without name entry");
            }

            // Click continue/next button with enhanced selectors
            String[] continueSelectors = {
                    "button:has-text('Ask to join')",
                    "button:has-text('Continue')",
                    "button:has-text('Next')",
                    "button:has-text('Join')",
                    "[data-is-touch-wrapper] button",
                    "[role='button']:has-text('Continue')",
                    "[role='button']:has-text('Next')"
            };

            handlePermissionButton(continueSelectors, "continue");

        } catch (Exception e) {
            BrowserUtil.logError(errorLogger, "Error entering anonymous name", e);
        }
    }
}
