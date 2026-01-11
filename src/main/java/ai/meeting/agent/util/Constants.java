package ai.meeting.agent.util;

public final class Constants {
    public static final String[] ENHANCED_BROWSER_ARGS = {
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

}
