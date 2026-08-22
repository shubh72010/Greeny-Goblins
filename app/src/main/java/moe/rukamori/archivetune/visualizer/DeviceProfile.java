package moe.rukamori.archivetune.visualizer;

import com.nothing.ketchum.Common;

/**
 * Device detection and display helpers used by the visualizer runtime.
 * Legacy hardcoded frequency maps live in zones.config now and no longer belong here.
 */
public final class DeviceProfile {

    public static final int DEVICE_UNKNOWN = 0;
    public static final int DEVICE_NP1 = 1;
    public static final int DEVICE_NP2 = 2;
    public static final int DEVICE_NP2A = 3;
    public static final int DEVICE_NP3A = 4;
    public static final int DEVICE_NP4A = 5;
    public static final int DEVICE_NP4APRO = 6;
    public static final int DEVICE_NP3 = 7;
    public static final int DEVICE_NP4B = 8;

    private DeviceProfile() {
    }

    public static int detectDevice() {
        if (android.os.Build.VERSION.SDK_INT < 31) {
            return DEVICE_UNKNOWN;
        }
        if (Common.is20111()) {
            return DEVICE_NP1;
        } else if (Common.is22111()) {
            return DEVICE_NP2;
        } else if (Common.is23111() || Common.is23113()) {
            return DEVICE_NP2A;
        } else if (Common.is24111()) {
            return DEVICE_NP3A;
        }  else if (Common.is25111()) {
            return DEVICE_NP4A;
        }  else if (Common.is25111p()){
            return DEVICE_NP4APRO;
        } else if (Common.is23112()) {
            return DEVICE_NP3;
        } else if (android.os.Build.MODEL.contains("26111") || android.os.Build.MODEL.toLowerCase().contains("phone 4b")) {
            return DEVICE_NP4B;
        } else {
            // Fallback for Nothing devices where Common checks fail (e.g., AIN065 india variant)
            String model = android.os.Build.MODEL;
            String lower = model.toLowerCase();
            String manufacturer = android.os.Build.MANUFACTURER;
            if (manufacturer != null && manufacturer.equalsIgnoreCase("Nothing")) {
                if (lower.contains("a063") || lower.contains("20111")) return DEVICE_NP1;
                if (lower.contains("a065") || lower.contains("ain065") || lower.contains("065") || lower.contains("22111")) return DEVICE_NP2;
                if (lower.contains("a142") || lower.contains("a142p") || lower.contains("23111") || lower.contains("23113")) return DEVICE_NP2A;
                if (lower.contains("a059") || lower.contains("24111")) return DEVICE_NP3A;
                if (lower.contains("a024") || lower.contains("23112")) return DEVICE_NP3;
                if (lower.contains("a015") || lower.contains("25111p")) return DEVICE_NP4APRO;
                if (lower.contains("a012") || lower.contains("25111")) return DEVICE_NP4A;
                // Generic Nothing fallback to NP2 (33 glyphs) so glyphs at least try
                return DEVICE_NP2;
            }
            return DEVICE_UNKNOWN;
        }
    }

    public static String deviceName(int device) {
        return switch (device) {
            case DEVICE_NP1 -> "Phone (1)";
            case DEVICE_NP2 -> "Phone (2)";
            case DEVICE_NP2A -> "Phone (2a)";
            case DEVICE_NP3A -> "Phone (3a)";
            case DEVICE_NP4A -> "Phone (4a)";
            case DEVICE_NP4APRO -> "Phone (4a) Pro";
            case DEVICE_NP3 -> "Phone (3)";
            case DEVICE_NP4B -> "Phone (4b)";
            default -> "Unknown";
        };
    }

    public static String shortdeviceName(int device) {
        return switch (device) {
            case DEVICE_NP1 -> "np1";
            case DEVICE_NP2 -> "np2";
            case DEVICE_NP2A -> "np2a";
            case DEVICE_NP3A -> "np3a";
            case DEVICE_NP4A -> "np4a";
            case DEVICE_NP4APRO -> "np4ap";
            case DEVICE_NP3 -> "np3";
            case DEVICE_NP4B -> "np4b";
            default -> "Other";
        };
    }

    public static int getLedCount(int device) {
        return switch (device) {
            case DEVICE_NP1 -> 15;
            case DEVICE_NP2 -> 33;
            case DEVICE_NP2A -> 26;
            case DEVICE_NP3A -> 36;
            case DEVICE_NP4A -> 7;
            case DEVICE_NP4APRO -> 137;
            case DEVICE_NP3 -> 625;
            case DEVICE_NP4B -> 5;
            default -> 0;
        };
    }

    public static int getMatrixWidth(int device) {
        return switch (device) {
            case DEVICE_NP3 -> 25;
            case DEVICE_NP4APRO -> 13;
            default -> 0;
        };
    }

    public static int getMatrixHeight(int device) {
        return switch (device) {
            case DEVICE_NP3 -> 25;
            case DEVICE_NP4APRO -> 13;
            default -> 0;
        };
    }
}
