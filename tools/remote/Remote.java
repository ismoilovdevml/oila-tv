import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Root input bridge for the phone remote, run with app_process as root (via a vendor init service).
 * It listens on 127.0.0.1 and injects key events system-wide, the same way the "input" command does,
 * so the phone can drive YouTube, Netflix and every other app, not just our launcher.
 *
 * Protocol: newline-terminated commands on the socket.
 *   K <keycode>   inject a down+up of that Android keycode (a tap)
 *   D <keycode>   inject key down only
 *   U <keycode>   inject key up only
 * It only touches input injection; it reads nothing and holds no other state.
 */
public final class Remote {
    private static final int PORT = 8766;
    private static final int MODE_ASYNC = 0; // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC

    public static void main(String[] args) throws Exception {
        // At boot the "input" service may not be registered yet; wait for it.
        Object input = null;
        for (int i = 0; input == null && i < 120; i++) {
            try {
                input = Class.forName("android.hardware.input.InputManager")
                        .getMethod("getInstance").invoke(null);
            } catch (Exception e) {
                Thread.sleep(1000);
            }
        }
        if (input == null) {
            System.err.println("remote: InputManager never came up");
            return;
        }
        Method inject = input.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);

        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress("127.0.0.1", PORT));
        System.out.println("remote: listening on 127.0.0.1:" + PORT);

        while (true) {
            Socket s = server.accept();
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()));
                for (String line = r.readLine(); line != null; line = r.readLine()) {
                    handle(line.trim(), inject, input);
                }
            } catch (Exception e) {
                System.err.println("remote: " + e);
            } finally {
                try { s.close(); } catch (Exception ignored) { }
            }
        }
    }

    private static void handle(String line, Method inject, Object input) throws Exception {
        if (line.isEmpty()) return;
        String[] p = line.split("\\s+");
        int code = Integer.parseInt(p[1]);
        char op = p[0].charAt(0);
        if (op == 'K') {
            send(inject, input, key(KeyEvent.ACTION_DOWN, code));
            send(inject, input, key(KeyEvent.ACTION_UP, code));
        } else if (op == 'D') {
            send(inject, input, key(KeyEvent.ACTION_DOWN, code));
        } else if (op == 'U') {
            send(inject, input, key(KeyEvent.ACTION_UP, code));
        }
    }

    private static KeyEvent key(int action, int code) {
        long now = SystemClock.uptimeMillis();
        return new KeyEvent(now, now, action, code, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                InputDevice.SOURCE_KEYBOARD | InputDevice.SOURCE_DPAD);
    }

    private static void send(Method inject, Object input, KeyEvent e) throws Exception {
        inject.invoke(input, e, MODE_ASYNC);
    }

    private Remote() {}
}
