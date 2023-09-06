package commandcenter.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

public class NtApi {
    public interface Lib extends Library {
        Lib INSTANCE = Native.load("NtDll", Lib.class, W32APIOptions.DEFAULT_OPTIONS);

        public int NtResumeProcess(WinNT.HANDLE handle);

        public int NtSuspendProcess(WinNT.HANDLE handle);
    }
}
