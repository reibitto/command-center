package commandcenter.command.win;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinNT.LUID;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

import java.util.Arrays;
import java.util.List;

/**
 * Raw bindings for the Windows "Connecting and Configuring Displays" (CCD) API in user32.dll, used to enumerate
 * GPU display outputs and switch which one is active - independent of whatever monitor is currently plugged
 * into it and regardless of whether it's currently powered on.
 *
 * <p>Written in Java (not Scala) deliberately: JNA's {@link Structure} discovers its native field layout via
 * reflection over public Java fields, and Scala compiles {@code var} class members to private fields with
 * synthetic accessors, which JNA can't see - it silently finds zero fields, and {@code getFieldOrder()} then
 * fails a consistency check at construction time.
 *
 * <p>Struct layouts are hand-transcribed from the Windows SDK (wingdi.h) since JNA doesn't ship them. The
 * {@code DISPLAYCONFIG_MODE_INFO} union (targetMode/sourceMode/desktopImageInfo) is intentionally left undecoded
 * as a raw 48-byte blob - callers only need to round-trip it unmodified from {@code QueryDisplayConfig} back
 * into {@code SetDisplayConfig}, never to construct or interpret one.
 */
public final class CCD {

    private CCD() {}

    public static final int QDC_ALL_PATHS = 0x00000001;
    public static final int QDC_ONLY_ACTIVE_PATHS = 0x00000002;

    public static final int SDC_USE_SUPPLIED_DISPLAY_CONFIG = 0x00000020;
    public static final int SDC_VALIDATE = 0x00000040;
    public static final int SDC_APPLY = 0x00000080;
    public static final int SDC_SAVE_TO_DATABASE = 0x00000200;
    public static final int SDC_ALLOW_CHANGES = 0x00000400;

    public static final int DISPLAYCONFIG_PATH_ACTIVE = 0x00000001;

    // Marks a path's source/target modeInfoIdx as "no associated mode" - must be set on every path being
    // deactivated, not just its ACTIVE flag cleared, or a stale mode index from a previous activation can be
    // left dangling and trip up validation on a later SetDisplayConfig call.
    public static final int DISPLAYCONFIG_PATH_MODE_IDX_INVALID = 0xFFFFFFFF;

    public static final int DISPLAYCONFIG_DEVICE_INFO_GET_TARGET_NAME = 2;

    public static final int ERROR_SUCCESS = 0;
    public static final int ERROR_INSUFFICIENT_BUFFER = 122;

    public static class DISPLAYCONFIG_RATIONAL extends Structure {
        public int Numerator;
        public int Denominator;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("Numerator", "Denominator");
        }
    }

    public static class DISPLAYCONFIG_PATH_SOURCE_INFO extends Structure {
        public LUID adapterId = new LUID();
        public int id;
        // Real type is a union of a plain UINT32 index and a bitfield split (used only when virtual-mode-aware
        // querying is requested, which we never do) - a plain int covers our usage.
        public int modeInfoIdx;
        public int statusFlags;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("adapterId", "id", "modeInfoIdx", "statusFlags");
        }
    }

    public static class DISPLAYCONFIG_PATH_TARGET_INFO extends Structure {
        public LUID adapterId = new LUID();
        public int id;
        public int modeInfoIdx;
        public int outputTechnology;
        public int rotation;
        public int scaling;
        public DISPLAYCONFIG_RATIONAL refreshRate = new DISPLAYCONFIG_RATIONAL();
        public int scanLineOrdering;
        public int targetAvailable;
        public int statusFlags;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "adapterId",
                    "id",
                    "modeInfoIdx",
                    "outputTechnology",
                    "rotation",
                    "scaling",
                    "refreshRate",
                    "scanLineOrdering",
                    "targetAvailable",
                    "statusFlags");
        }
    }

    public static class DISPLAYCONFIG_PATH_INFO extends Structure {
        public DISPLAYCONFIG_PATH_SOURCE_INFO sourceInfo = new DISPLAYCONFIG_PATH_SOURCE_INFO();
        public DISPLAYCONFIG_PATH_TARGET_INFO targetInfo = new DISPLAYCONFIG_PATH_TARGET_INFO();
        public int flags;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("sourceInfo", "targetInfo", "flags");
        }

        public static DISPLAYCONFIG_PATH_INFO[] array(int n) {
            if (n == 0) return new DISPLAYCONFIG_PATH_INFO[0];
            return (DISPLAYCONFIG_PATH_INFO[]) new DISPLAYCONFIG_PATH_INFO().toArray(n);
        }
    }

    public static class DISPLAYCONFIG_MODE_INFO extends Structure {
        public int infoType;
        public int id;
        public LUID adapterId = new LUID();
        public byte[] union = new byte[48];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("infoType", "id", "adapterId", "union");
        }

        public static DISPLAYCONFIG_MODE_INFO[] array(int n) {
            if (n == 0) return new DISPLAYCONFIG_MODE_INFO[0];
            return (DISPLAYCONFIG_MODE_INFO[]) new DISPLAYCONFIG_MODE_INFO().toArray(n);
        }
    }

    public static class DISPLAYCONFIG_DEVICE_INFO_HEADER extends Structure {
        public int type;
        public int infoSize;
        public LUID adapterId = new LUID();
        public int id;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("type", "infoSize", "adapterId", "id");
        }
    }

    public static class DISPLAYCONFIG_TARGET_DEVICE_NAME extends Structure {
        public DISPLAYCONFIG_DEVICE_INFO_HEADER header = new DISPLAYCONFIG_DEVICE_INFO_HEADER();
        public int flags;
        public int outputTechnology;
        public short edidManufactureId;
        public short edidProductCodeId;
        public int connectorInstance;
        public char[] monitorFriendlyDeviceName = new char[64];
        public char[] monitorDevicePath = new char[128];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "header",
                    "flags",
                    "outputTechnology",
                    "edidManufactureId",
                    "edidProductCodeId",
                    "connectorInstance",
                    "monitorFriendlyDeviceName",
                    "monitorDevicePath");
        }
    }

    public interface User32CCD extends Library {

        int GetDisplayConfigBufferSizes(
                int flags, IntByReference numPathArrayElements, IntByReference numModeInfoArrayElements);

        int QueryDisplayConfig(
                int flags,
                IntByReference numPathArrayElements,
                DISPLAYCONFIG_PATH_INFO[] pathArray,
                IntByReference numModeInfoArrayElements,
                DISPLAYCONFIG_MODE_INFO[] modeInfoArray,
                Pointer currentTopologyId);

        int SetDisplayConfig(
                int numPathArrayElements,
                DISPLAYCONFIG_PATH_INFO[] pathArray,
                int numModeInfoArrayElements,
                DISPLAYCONFIG_MODE_INFO[] modeInfoArray,
                int flags);

        int DisplayConfigGetDeviceInfo(DISPLAYCONFIG_TARGET_DEVICE_NAME requestPacket);
    }

    public static final User32CCD INSTANCE = Native.load("user32", User32CCD.class, W32APIOptions.DEFAULT_OPTIONS);
}
