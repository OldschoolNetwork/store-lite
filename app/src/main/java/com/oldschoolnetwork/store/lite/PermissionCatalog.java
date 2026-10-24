package com.oldschoolnetwork.store.lite;

import java.util.Locale;

/** Display families cover both platform permissions and unknown application-defined permissions. */
public final class PermissionCatalog {
    private PermissionCatalog() {}
    public static final class Entry {
        public final String title, description;
        public final int icon;
        Entry(String title, String description, int icon) { this.title=title;this.description=description;this.icon=icon; }
    }
    public static java.util.LinkedHashMap<String, java.util.LinkedHashSet<String>> group(Iterable<String> permissions) {
        java.util.LinkedHashMap<String, java.util.LinkedHashSet<String>> groups = new java.util.LinkedHashMap<>();
        for (String raw : permissions) {
            if (raw == null || raw.trim().length()==0) continue;
            String name = raw.trim(), title = describe(name).title;
            if (!groups.containsKey(title)) groups.put(title, new java.util.LinkedHashSet<String>());
            groups.get(title).add(name);
        }
        return groups;
    }
    public static Entry describe(String name) {
        String key = name.toUpperCase(Locale.US);
        if (key.contains("LOCATION")) return new Entry("Location", "Access device location. The exact access level depends on the permission.", 0);
        if (key.contains("CAMERA")) return new Entry("Camera", "Take photos or record video using the camera.", 0);
        if (key.contains("RECORD_AUDIO") || key.contains("CAPTURE_AUDIO") || key.contains("MICROPHONE")) return new Entry("Microphone", "Capture audio from a microphone or audio source.", 0);
        if (key.contains("CONTACT") || key.contains("CALL_LOG")) return new Entry("Contacts and call history", "Access contact records or call history, as specified by this permission.", 0);
        if (key.contains("SMS") || key.contains("MMS") || key.contains("WAP_PUSH")) return new Entry("Messages", "Read, receive or send messages, as specified by this permission.", 0);
        if (key.contains("PHONE") || key.contains("CALL") || key.contains("VOICEMAIL")) return new Entry("Phone", "Use phone state, calling or telephony features.", 0);
        if (key.contains("CALENDAR")) return new Entry("Calendar", "Read or update calendar information.", 0);
        if (key.contains("BLUETOOTH") || key.contains("NEARBY") || key.contains("NFC") || key.contains("UWB")) return new Entry("Nearby devices", "Discover or communicate with nearby devices.", 0);
        if (key.contains("STORAGE") || key.contains("MEDIA_") || key.contains("DOWNLOAD") || key.contains("DOCUMENT")) return new Entry("Files and media", "Access files or media. The file types and access level depend on this permission.", 0);
        if (key.contains("ACCOUNT") || key.contains("CREDENTIAL") || key.contains("AUTHENTICATE")) return new Entry("Accounts", "Use account or credential features provided by Android or another app.", 0);
        if (key.contains("NOTIFICATION") || key.contains("C2D") || key.contains("PUSH") || key.contains("VIBRATE")) return new Entry("Notifications", "Receive events, show notifications or provide alerts.", 0);
        if (key.contains("INTERNET") || key.contains("NETWORK") || key.contains("WIFI") || key.contains("MULTICAST")) return new Entry("Network and Internet", "Connect to a network or inspect connectivity.", 0);
        if (key.contains("SENSOR") || key.contains("ACTIVITY_RECOGNITION") || key.contains("HEALTH") || key.contains("FITNESS")) return new Entry("Sensors and activity", "Use sensor, physical activity or health information.", 0);
        if (key.contains("WAKE_LOCK") || key.contains("BATTERY") || key.contains("FOREGROUND_SERVICE") || key.contains("BOOT_COMPLETED") || key.contains("ALARM")) return new Entry("Background operation", "Run tasks, handle device startup or keep the device awake when needed.", 0);
        if (key.contains("INSTALL") || key.contains("PACKAGE") || key.contains("DELETE_PACKAGES")) return new Entry("Applications", "Inspect or manage application packages, as specified by this permission.", 0);
        if (key.contains("BILLING")) return new Entry("In-app purchases", "Connect to the app's purchase service. This permission alone does not make a payment.", 0);
        if (key.contains("BIOMETRIC") || key.contains("FINGERPRINT")) return new Entry("Biometric authentication", "Use Android's biometric authentication prompt.", 0);
        if (key.contains("SETTINGS") || key.contains("OVERLAY") || key.contains("SYSTEM_ALERT") || key.contains("ACCESSIBILITY") || key.contains("DEVICE_ADMIN")) return new Entry("Device settings", "Use device settings or system integration. Some features require special approval.", 0);
        String token = name.substring(name.lastIndexOf('.')+1).replace('_',' ').toLowerCase(Locale.US);
        String title = token.isEmpty() ? "Additional permission" : Character.toUpperCase(token.charAt(0))+token.substring(1);
        return new Entry(title, name.startsWith("android.permission.") ? "Additional Android capability. Tap to inspect the exact permission." : "Permission defined by an application or service. Tap to inspect its identifier.", 0);
    }
}
