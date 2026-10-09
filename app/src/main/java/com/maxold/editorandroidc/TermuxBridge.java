package com.maxold.editorandroidc;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.concurrent.atomic.AtomicInteger;

public final class TermuxBridge {
    public static final String RESULT_BUNDLE = "result";
    private static final AtomicInteger REQUEST_IDS = new AtomicInteger(10000);
    private static final String PREFIX = "com.termux.RUN_COMMAND";
    private TermuxBridge() { }

    public static void execute(Context context, String label, String shellCode) {
        Intent command = new Intent(PREFIX);
        command.setClassName("com.termux", "com.termux.app.RunCommandService");
        command.putExtra(PREFIX + "_PATH", "/data/data/com.termux/files/usr/bin/bash");
        command.putExtra(PREFIX + "_ARGUMENTS", new String[]{"-lc", shellCode});
        command.putExtra(PREFIX + "_WORKDIR", "/data/data/com.termux/files/home");
        command.putExtra(PREFIX + "_BACKGROUND", true);
        command.putExtra(PREFIX + "_COMMAND_LABEL", label);
        Intent receiver = new Intent(context, CommandResultReceiver.class);
        receiver.putExtra("label", label);
        int flags = PendingIntent.FLAG_ONE_SHOT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        PendingIntent result = PendingIntent.getBroadcast(context, REQUEST_IDS.incrementAndGet(), receiver, flags);
        command.putExtra(PREFIX + "_PENDING_INTENT", result);
        context.startService(command);
    }

    public static String shellQuote(String raw) {
        return "'" + raw.replace("'", "'\\''") + "'";
    }

    public static String command(String mode, String sharedPath, String target) {
        String prefix = "set -e\n" +
            "SRC=\"$HOME/editorandroidc-/source\"\n" +
            "BUILD=\"$HOME/editorandroidc-/build\"\n" +
            "BIN=\"$HOME/editorandroidc-/bin\"\n" +
            "mkdir -p \"$SRC\" \"$BUILD\" \"$BIN\"\n";
        if (mode.equals("configure") || mode.equals("build")) {
            prefix += "echo '[EditorAndroidC] Importando projeto'\n" +
                "cp -R " + shellQuote(sharedPath) + "/. \"$SRC/\"\n" +
                "cmake -S \"$SRC\" -B \"$BUILD\" -G 'Unix Makefiles' -DCMAKE_RUNTIME_OUTPUT_DIRECTORY=\"$BIN\"\n";
        }
        if (mode.equals("build")) {
            return prefix + "make -C \"$BUILD\" -j2\n" +
                "echo '[EditorAndroidC] Binarios:'\nls -l \"$BIN\"\n";
        }
        if (mode.equals("run")) {
            return prefix + "test -x \"$BIN/" + target + "\" || { echo 'Executavel ausente: compile primeiro.'; exit 1; }\n" +
                "cd \"$BIN\"\n\"./" + target + "\"\n";
        }
        return prefix;
    }
}
