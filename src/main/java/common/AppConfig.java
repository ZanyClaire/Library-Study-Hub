package common;

import java.awt.Color;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/**
 * Loads application settings and eye-comfort theme colors from config.properties
 * (inside the jar/classpath) and optionally from an external studyhub.properties
 * file in the working folder. This keeps ports, limits, defaults, window sizes,
 * and colors out of the Java code.
 */
public final class AppConfig {

    private static final String RESOURCE = "/librarystudyhub/config.properties";
    private static final String OVERRIDE_FILE = "studyhub.properties";
    private static final Properties PROPS = load();

    private AppConfig() {
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // fall back to defaults given by callers
        }
        Path override = Path.of(OVERRIDE_FILE);
        if (Files.isRegularFile(override)) {
            try (InputStream in = new FileInputStream(override.toFile())) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // keep bundled values
            }
        }
        return p;
    }

    public static String get(String key, String fallback) {
        String v = PROPS.getProperty(key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    public static int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static Color color(String key, String fallbackHex) {
        try {
            return Color.decode(get(key, fallbackHex));
        } catch (NumberFormatException e) {
            return Color.decode(fallbackHex);
        }
    }

    /** Configures Nimbus Look & Feel with calm eye-comfort colors from config.properties. */
    public static void applyLookAndFeel() {
        try {
            UIManager.put("control", color("theme.surfaceBg", "#EBF1F8"));
            UIManager.put("nimbusBase", color("theme.primaryBtnBg", "#2B6CB0"));
            UIManager.put("nimbusBlueGrey", color("theme.secondaryBtnBg", "#DDE7F3"));
            UIManager.put("nimbusFocus", color("theme.accentBlue", "#2B6CB0"));
            UIManager.put("nimbusOrange", color("theme.accentBlue", "#2B6CB0"));
            UIManager.put("nimbusLightBackground", color("theme.cardBg", "#F9FBFD"));
            UIManager.put("nimbusSelectionBackground", color("theme.selectionBg", "#D4E4F7"));
            UIManager.put("nimbusSelectedText", color("theme.navyText", "#1B365D"));
            UIManager.put("nimbusDisabledText", color("theme.mutedText", "#64748B"));
            UIManager.put("text", color("theme.bodyText", "#24374F"));
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // keep default look and feel if Nimbus is unavailable
        }
    }

    /** Keeps primary buttons clearly legible in both enabled and disabled states under Nimbus. */
    public static void bindPrimaryButton(javax.swing.JButton button) {
        Runnable refresh = () -> {
            if (button.isEnabled()) {
                button.setBackground(color("theme.primaryBtnBg", "#2B6CB0"));
                button.setForeground(color("theme.primaryBtnFg", "#F8FAFC"));
            } else {
                button.setBackground(color("theme.secondaryBtnBg", "#DDE7F3"));
                button.setForeground(color("theme.mutedText", "#64748B"));
            }
        };
        refresh.run();
        button.addPropertyChangeListener("enabled", evt -> refresh.run());
    }

    /** Creates a clean, soft-bordered titled panel border using theme colors. */
    public static TitledBorder titledBorder(String title) {
        Border line = BorderFactory.createLineBorder(color("theme.borderColor", "#B9CBE0"), 1);
        Border padded = BorderFactory.createCompoundBorder(line, BorderFactory.createEmptyBorder(4, 6, 6, 6));
        return BorderFactory.createTitledBorder(
                padded,
                " " + title + " ",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12),
                color("theme.navyText", "#1B365D"));
    }

    public static int serverPort()          { return getInt("server.port", 5050); }
    public static int defaultSeats()        { return getInt("server.seats", 8); }
    public static int maxSeats()            { return getInt("server.maxSeats", 30); }
    public static int serverWidth()         { return getInt("server.width", 1120); }
    public static int serverHeight()        { return getInt("server.height", 660); }
    public static int maxMessageLength()    { return getInt("chat.maxLength", 500); }
    public static String nicknamePattern()  { return get("nickname.pattern", "[A-Za-z0-9_]{2,16}"); }
    public static String nicknameRule()     { return get("nickname.rule", "letters, digits or underscore"); }
    public static int defaultTimerMinutes() { return getInt("timer.defaultMinutes", 25); }
    public static int maxTimerMinutes()     { return getInt("timer.maxMinutes", 180); }
    public static int maxTimerLabelLength() { return get("timer.maxLabelLength", "40").isEmpty() ? 40 : getInt("timer.maxLabelLength", 40); }
    public static String defaultHost()      { return get("client.defaultHost", "localhost"); }
    public static int connectTimeoutMs()    { return getInt("client.connectTimeoutMs", 5000); }
    public static int clientWidth()         { return getInt("client.width", 1280); }
    public static int clientHeight()        { return getInt("client.height", 760); }
}
