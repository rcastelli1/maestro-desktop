package br.com.agenteslocal.window;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.util.Locale;

/** Applies the Maestro palette to the native Windows caption without replacing its window controls. */
public final class WindowsTitleBar {
    private static final int DARK_MODE = 20;
    private static final int BORDER_COLOR = 34;
    private static final int CAPTION_COLOR = 35;
    private static final int TEXT_COLOR = 36;
    private static final int GWL_EXSTYLE = -20;
    private static final long WS_EX_TOOLWINDOW = 0x00000080L;
    private static final long WS_EX_APPWINDOW = 0x00040000L;
    private static final long WS_EX_NOACTIVATE = 0x08000000L;

    private WindowsTitleBar() { }

    public static boolean apply(String windowTitle) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return false;
        try {
            User32 user32 = Native.load("user32", User32.class);
            Dwmapi dwmapi = Native.load("dwmapi", Dwmapi.class);
            long currentPid = ProcessHandle.current().pid();
            Pointer[] target = {null};
            user32.EnumWindows((window, unused) -> {
                IntByReference ownerPid = new IntByReference();
                user32.GetWindowThreadProcessId(window, ownerPid);
                if (Integer.toUnsignedLong(ownerPid.getValue()) != currentPid) return true;
                Memory titleBuffer = new Memory(512L * Native.WCHAR_SIZE);
                int length = user32.GetWindowTextW(window, titleBuffer, 512);
                if (length <= 0 || !windowTitle.equals(titleBuffer.getWideString(0))) return true;
                target[0] = window;
                return false;
            }, null);
            if (target[0] == null) return false;

            // COLORREF stores red in the lowest byte and blue in the highest byte.
            boolean dark = set(dwmapi, target[0], DARK_MODE, 1);
            boolean caption = set(dwmapi, target[0], CAPTION_COLOR, colorRef(0x202020));
            boolean text = set(dwmapi, target[0], TEXT_COLOR, colorRef(0xEDEDEB));
            set(dwmapi, target[0], BORDER_COLOR, colorRef(0x303030));
            return dark || caption || text;
        } catch (LinkageError | RuntimeException error) {
            System.err.println("Maestro: não foi possível aplicar a cor da barra do Windows: " + error.getMessage());
            return false;
        }
    }

    /** Keep the player above other apps without taking keyboard focus from them. */
    public static boolean makeOverlayNonActivating(String windowTitle) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return false;
        try {
            User32 user32 = Native.load("user32", User32.class);
            long currentPid = ProcessHandle.current().pid();
            Pointer[] target = {null};
            user32.EnumWindows((window, unused) -> {
                IntByReference ownerPid = new IntByReference();
                user32.GetWindowThreadProcessId(window, ownerPid);
                if (Integer.toUnsignedLong(ownerPid.getValue()) != currentPid) return true;
                Memory titleBuffer = new Memory(512L * Native.WCHAR_SIZE);
                if (user32.GetWindowTextW(window, titleBuffer, 512) <= 0
                        || !windowTitle.equals(titleBuffer.getWideString(0))) return true;
                target[0] = window;
                return false;
            }, null);
            if (target[0] == null) return false;
            Pointer previous = user32.GetWindowLongPtrW(target[0], GWL_EXSTYLE);
            long style = Pointer.nativeValue(previous);
            long overlayStyle = (style | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE) & ~WS_EX_APPWINDOW;
            user32.SetWindowLongPtrW(target[0], GWL_EXSTYLE, Pointer.createConstant(overlayStyle));
            user32.SetWindowPos(target[0], Pointer.createConstant(-1L), 0, 0, 0, 0,
                    0x0001 | 0x0002 | 0x0010 | 0x0020);
            return (Pointer.nativeValue(user32.GetWindowLongPtrW(target[0], GWL_EXSTYLE))
                    & WS_EX_NOACTIVATE) != 0;
        } catch (LinkageError | RuntimeException error) {
            System.err.println("Maestro: não foi possível configurar o mini player: " + error.getMessage());
            return false;
        }
    }

    private static boolean set(Dwmapi dwmapi, Pointer window, int attribute, int value) {
        return dwmapi.DwmSetWindowAttribute(window, attribute, new IntByReference(value), Integer.BYTES) == 0;
    }

    private static int colorRef(int rgb) {
        return (rgb & 0x00ff00) | ((rgb & 0xff0000) >>> 16) | ((rgb & 0x0000ff) << 16);
    }

    private interface WindowVisitor extends StdCallLibrary.StdCallCallback {
        boolean callback(Pointer window, Pointer data);
    }

    private interface User32 extends StdCallLibrary {
        boolean EnumWindows(WindowVisitor visitor, Pointer data);
        int GetWindowThreadProcessId(Pointer window, IntByReference processId);
        int GetWindowTextW(Pointer window, Pointer buffer, int capacity);
        Pointer GetWindowLongPtrW(Pointer window, int index);
        Pointer SetWindowLongPtrW(Pointer window, int index, Pointer value);
        boolean SetWindowPos(Pointer window, Pointer insertAfter, int x, int y, int width, int height, int flags);
    }

    private interface Dwmapi extends StdCallLibrary {
        int DwmSetWindowAttribute(Pointer window, int attribute, IntByReference value, int size);
    }
}
