package dev.bulwark.sentinel.window;

/** Called once per closed window, on the thread that closed it. */
public interface WindowListener {

    void onWindowClosed(ClosedWindow window);
}
