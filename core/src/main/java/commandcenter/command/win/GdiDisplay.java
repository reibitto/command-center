package commandcenter.command.win;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.win32.W32APIOptions;

import java.util.Arrays;
import java.util.List;

/**
 * Bindings for the older GDI display-settings API in user32.dll ({@code EnumDisplayDevices},
 * {@code EnumDisplaySettingsEx}, {@code ChangeDisplaySettingsEx}) - used only to set a display's refresh rate.
 *
 * <p>The CCD API in {@link CCD} has no way to request a specific mode (resolution/refresh rate) without
 * hand-computing exact pixel-clock and sync-frequency timings for a {@code DISPLAYCONFIG_TARGET_MODE} - fragile
 * and unnecessary here, since this older API accepts a plain integer Hz value in {@code DEVMODE.dmDisplayFrequency}
 * and lets Windows resolve the actual timing itself, the same way the Display Settings control panel does.
 *
 * <p>Written in Java for the same reason as {@link CCD}: JNA {@link Structure} fields must be real public Java
 * fields to be visible to its native-layout reflection, which Scala's compiled {@code var} fields aren't.
 */
public final class GdiDisplay {

    private GdiDisplay() {}

    public static final int EDD_GET_DEVICE_INTERFACE_NAME = 0x00000001;

    // DWORD -1, meaning "the mode currently in use" for EnumDisplaySettingsEx's iModeNum parameter.
    public static final int ENUM_CURRENT_SETTINGS = -1;

    public static final int DM_DISPLAYFREQUENCY = 0x00400000;

    public static final int CDS_UPDATEREGISTRY = 0x00000001;

    public static final int DISP_CHANGE_SUCCESSFUL = 0;

    public static class DISPLAY_DEVICE extends Structure {
        public int cb;
        public char[] DeviceName = new char[32];
        public char[] DeviceString = new char[128];
        public int StateFlags;
        public char[] DeviceID = new char[128];
        public char[] DeviceKey = new char[128];

        public DISPLAY_DEVICE() {
            cb = size();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("cb", "DeviceName", "DeviceString", "StateFlags", "DeviceID", "DeviceKey");
        }
    }

    // The leading union (POINTL dmPosition + dmDisplayOrientation + dmDisplayFixedOutput in the "display device"
    // interpretation, vs. several printer-only SHORTs in the other) is left as an opaque 16-byte blob - it's
    // unused either way here, and both interpretations occupy exactly 16 bytes so the fields after it still land
    // at the correct offsets.
    public static class DEVMODE extends Structure {
        public char[] dmDeviceName = new char[32];
        public short dmSpecVersion;
        public short dmDriverVersion;
        public short dmSize;
        public short dmDriverExtra;
        public int dmFields;
        public byte[] union1 = new byte[16];
        public short dmColor;
        public short dmDuplex;
        public short dmYResolution;
        public short dmTTOption;
        public short dmCollate;
        public char[] dmFormName = new char[32];
        public short dmLogPixels;
        public int dmBitsPerPel;
        public int dmPelsWidth;
        public int dmPelsHeight;
        public int dmDisplayFlags;
        public int dmDisplayFrequency;
        public int dmICMMethod;
        public int dmICMIntent;
        public int dmMediaType;
        public int dmDitherType;
        public int dmReserved1;
        public int dmReserved2;
        public int dmPanningWidth;
        public int dmPanningHeight;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "dmDeviceName",
                    "dmSpecVersion",
                    "dmDriverVersion",
                    "dmSize",
                    "dmDriverExtra",
                    "dmFields",
                    "union1",
                    "dmColor",
                    "dmDuplex",
                    "dmYResolution",
                    "dmTTOption",
                    "dmCollate",
                    "dmFormName",
                    "dmLogPixels",
                    "dmBitsPerPel",
                    "dmPelsWidth",
                    "dmPelsHeight",
                    "dmDisplayFlags",
                    "dmDisplayFrequency",
                    "dmICMMethod",
                    "dmICMIntent",
                    "dmMediaType",
                    "dmDitherType",
                    "dmReserved1",
                    "dmReserved2",
                    "dmPanningWidth",
                    "dmPanningHeight");
        }
    }

    public interface User32Gdi extends Library {

        boolean EnumDisplayDevicesW(WString lpDevice, int iDevNum, DISPLAY_DEVICE lpDisplayDevice, int dwFlags);

        boolean EnumDisplaySettingsExW(WString lpszDeviceName, int iModeNum, DEVMODE lpDevMode, int dwFlags);

        int ChangeDisplaySettingsExW(
                WString lpszDeviceName, DEVMODE lpDevMode, Pointer hwnd, int dwflags, Pointer lParam);
    }

    public static final User32Gdi INSTANCE = Native.load("user32", User32Gdi.class, W32APIOptions.DEFAULT_OPTIONS);
}
