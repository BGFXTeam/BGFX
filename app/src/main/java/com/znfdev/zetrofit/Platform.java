package com.znfdev.zetrofit;

import java.util.concurrent.Executor;

/** Detects whether we're running on Android so callbacks can be posted to the main thread. */
class Platform {

    private static final Platform PLATFORM = findPlatform();

    static Platform get() {
        return PLATFORM;
    }

    private static Platform findPlatform() {
        try {
            Class.forName("android.os.Build");
            return new Android();
        } catch (ClassNotFoundException ignored) {
            return new Platform();
        }
    }

    /** Returns null to mean "run the callback on the background thread that produced it". */
    Executor defaultCallbackExecutor() {
        return null;
    }

    static class Android extends Platform {
        @Override
        Executor defaultCallbackExecutor() {
            return new MainThreadExecutor();
        }

        static final class MainThreadExecutor implements Executor {
            private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

            public void execute(Runnable command) {
                handler.post(command);
            }
        }
    }
}
