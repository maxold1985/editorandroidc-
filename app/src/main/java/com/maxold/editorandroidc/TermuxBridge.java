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
        command.putExtra(PREFIX + "_BACKGROUND", !"Executar".equals(label));
        command.putExtra(PREFIX + "_COMMAND_LABEL", label);
        Intent receiver = new Intent(context, CommandResultReceiver.class);
        receiver.putExtra("label", label);
        int flags = PendingIntent.FLAG_ONE_SHOT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        PendingIntent result = PendingIntent.getBroadcast(context, REQUEST_IDS.incrementAndGet(), receiver, flags);
        if (!"Executar".equals(label)) command.putExtra(PREFIX + "_PENDING_INTENT", result);
        context.startService(command);
    }

    public static String shellQuote(String raw) {
        return "'" + raw.replace("'", "'\\''") + "'";
    }

    /**
     * The compiler runs inside Termux. gcc/g++ can be LLVM aliases on Termux.
     */
    public static String command(String mode, String sharedPath, String target,
                                 String currentFile, String toolchain, int renderer) {
        if (!"gcc".equals(toolchain) && !"clang".equals(toolchain)) {
            throw new IllegalArgumentException("Compilador desconhecido");
        }
        StringBuilder script = new StringBuilder();
        script.append("set -eu\n");
        script.append("export PATH=\"/data/data/com.termux/files/usr/bin:$PATH\"\n");
        script.append("SRC=\"$HOME/editorandroidc-/source\"\n");
        script.append("BIN=\"$HOME/editorandroidc-/bin\"\n");
        script.append("TARGET=").append(shellQuote(target)).append("\n");
        script.append("TOOLCHAIN=").append(shellQuote(toolchain)).append("\n");
        script.append("BUILD=\"$HOME/editorandroidc-/build-$TOOLCHAIN\"\n");
        script.append("mkdir -p \"$SRC\" \"$BIN\"\n");
        if (!mode.equals("run") && !mode.equals("diagnose")) {
            script.append("PROJECT_PATH=").append(shellQuote(sharedPath)).append("\n");
            script.append("PROJECT_MARKER=\"$HOME/editorandroidc-/.active-project\"\n");
            script.append("OLD_PROJECT=\"\"\n");
            script.append("if [ -f \"$PROJECT_MARKER\" ]; then IFS= read -r OLD_PROJECT < \"$PROJECT_MARKER\" || true; fi\n");
            script.append("if [ \"$OLD_PROJECT\" != \"$PROJECT_PATH\" ]; then\n");
            script.append("  echo '[EditorAndroidC] Projeto alterado: limpando fontes, builds e binarios anteriores'\n");
            script.append("  rm -rf -- \"$SRC\" \"$HOME/editorandroidc-/build-gcc\" \"$HOME/editorandroidc-/build-clang\" \"$BIN\"\n");
            script.append("  mkdir -p \"$SRC\" \"$BIN\"\n");
            script.append("  printf '%s\\n' \"$PROJECT_PATH\" > \"$PROJECT_MARKER\"\n");
            script.append("fi\n");
        }

        if (mode.equals("apk")) {
            script.append("if ! command -v java >/dev/null 2>&1; then echo 'Java/JDK ausente no Termux' >&2; exit 127; fi\n");
            script.append("if ! command -v git >/dev/null 2>&1; then echo 'Git ausente: pkg install git' >&2; exit 127; fi\n");
            script.append("if [ -z \"${ANDROID_HOME:-}\" ] && [ -z \"${ANDROID_SDK_ROOT:-}\" ]; then echo 'Configure ANDROID_HOME para o Android SDK' >&2; exit 2; fi\n");
            script.append("SDK=\"${ANDROID_HOME:-$ANDROID_SDK_ROOT}\"\n");
            script.append("if [ ! -d \"$SDK/platforms\" ]; then echo 'Android SDK sem plataformas instaladas' >&2; exit 2; fi\n");
            script.append("if [ -z \"${ANDROID_NDK_HOME:-}\" ] && [ ! -d \"$SDK/ndk\" ]; then echo 'Android NDK ausente; configure ANDROID_NDK_HOME ou instale no SDK' >&2; exit 2; fi\n");
            script.append("cp -R ").append(shellQuote(sharedPath)).append("/. \"$SRC/\"\n");
            script.append("if [ ! -f \"$SRC/settings.gradle\" ] && [ ! -f \"$SRC/settings.gradle.kts\" ]; then echo 'Projeto Android Gradle necessario (settings.gradle)' >&2; exit 2; fi\n");
            script.append("cd \"$SRC\"\n");
            script.append("if [ -f ./gradlew ]; then chmod +x ./gradlew; GRADLE=./gradlew; elif command -v gradle >/dev/null 2>&1; then GRADLE=gradle; else echo 'Gradle ausente: inclua gradlew ou instale gradle no Termux' >&2; exit 127; fi\n");
            script.append("echo '[EditorAndroidC] Iniciando assembleDebug com Gradle/NDK'\n");
            script.append("\"$GRADLE\" --no-daemon assembleDebug\n");
            script.append("echo '[EditorAndroidC] APKs gerados:'\n");
            script.append("find \"$SRC\" -type f -path '*/build/outputs/apk/*' -name '*.apk' -print\n");
            return script.toString();
        }
        if (mode.equals("diagnose")) {
            script.append("echo 'Compiladores presentes no Termux:'\n");
            script.append("for cc in gcc g++ clang clang++; do\n");
            script.append("  if command -v \"$cc\" >/dev/null 2>&1; then\n");
            script.append("    printf '%s: ' \"$cc\"; command -v \"$cc\"\n");
            script.append("    \"$cc\" --version | sed -n '1p'\n");
            script.append("  else echo \"$cc: não instalado\"; fi\n");
            script.append("done\n");
            script.append("echo 'Nota: gcc/g++ podem apontar para Clang no Termux.'\n");
            return script.toString();
        }
        if (mode.equals("run")) {
            script.append("PROJECT_PATH=").append(shellQuote(sharedPath)).append("\n");
            script.append("PROJECT_MARKER=\"$HOME/editorandroidc-/.active-project\"\n");
            script.append("ACTIVE_PROJECT=\"\"\n");
            script.append("if [ -f \"$PROJECT_MARKER\" ]; then IFS= read -r ACTIVE_PROJECT < \"$PROJECT_MARKER\" || true; fi\n");
            script.append("if [ \"$ACTIVE_PROJECT\" != \"$PROJECT_PATH\" ]; then echo 'Projeto diferente do ultimo compilado. Compile este projeto primeiro.' >&2; exit 2; fi\n");
            script.append("test -x \"$BIN/$TARGET\" || { echo 'Executável ausente: compile primeiro.' >&2; exit 1; }\n");
            script.append("echo \"Executando: $BIN/$TARGET\"\n");
            script.append("if [ -f \"$HOME/.config/editorandroidc/graphics.env\" ]; then\n");
            script.append("  set -a; . \"$HOME/.config/editorandroidc/graphics.env\"; set +a\n");
            script.append("fi\n");
            script.append("echo \"DISPLAY=\u0024{DISPLAY:-unset} GALLIUM_DRIVER=\u0024{GALLIUM_DRIVER:-unset} MESA_LOADER_DRIVER_OVERRIDE=\u0024{MESA_LOADER_DRIVER_OVERRIDE:-unset}\"\n");
            script.append("case ").append(renderer).append(" in\n");
            script.append("  0) echo Gallium:ambiente ;;\n");
            script.append("  1) export GALLIUM_DRIVER=softpipe; export LIBGL_ALWAYS_SOFTWARE=1; unset MESA_LOADER_DRIVER_OVERRIDE; echo Gallium:softpipe ;;\n");
            script.append("  2) export GALLIUM_DRIVER=llvmpipe; export LIBGL_ALWAYS_SOFTWARE=1; unset MESA_LOADER_DRIVER_OVERRIDE; echo Gallium:llvmpipe ;;\n");
            script.append("  3) export GALLIUM_DRIVER=zink; unset LIBGL_ALWAYS_SOFTWARE; echo Gallium:zink ;;\n");
            script.append("  4) export GALLIUM_DRIVER=virpipe; unset LIBGL_ALWAYS_SOFTWARE; echo Gallium:virpipe ;;\n");
            script.append("esac\n");
            script.append("cd \"$BIN\"\n");
            script.append("\"./$TARGET\"\n");
            return script.toString();
        }

        script.append("if [ \"$TOOLCHAIN\" = 'gcc' ]; then CC_NAME=gcc; CXX_NAME=g++; ");
        script.append("else CC_NAME=clang; CXX_NAME=clang++; fi\n");
        script.append("CC_BIN=\"$(command -v \"$CC_NAME\" || true)\"\n");
        script.append("CXX_BIN=\"$(command -v \"$CXX_NAME\" || true)\"\n");
        script.append("if [ -z \"$CC_BIN\" ] || [ -z \"$CXX_BIN\" ]; then\n");
        script.append(" echo 'Compilador não encontrado no Termux. Instale: pkg install clang cmake make' >&2\n");
        script.append(" exit 127\nfi\n");
        script.append("echo \"C: $CC_BIN\"; \"$CC_BIN\" --version | sed -n '1p'\n");
        script.append("echo \"C++: $CXX_BIN\"; \"$CXX_BIN\" --version | sed -n '1p'\n");
        script.append("mkdir -p \"$BUILD\"\n");
        script.append("echo '[EditorAndroidC] Sincronizando os arquivos com Termux'\n");
        script.append("cp -R ").append(shellQuote(sharedPath)).append("/. \"$SRC/\"\n");
        if (mode.equals("single")) {
            script.append("CURRENT=").append(shellQuote(currentFile)).append("\n");
            script.append("test -f \"$SRC/$CURRENT\" || { echo 'Arquivo não encontrado' >&2; exit 1; }\n");
            script.append("case \"$CURRENT\" in\n");
            script.append(" *.c) \"$CC_BIN\" -std=c11 -O0 -g -Wall -Wextra -I \"$SRC\" ");
            script.append("\"$SRC/$CURRENT\" -o \"$BIN/$TARGET\" ;;\n");
            script.append(" *.cpp|*.cc|*.cxx) \"$CXX_BIN\" -std=c++17 -O0 -g -Wall -Wextra -I \"$SRC\" ");
            script.append("\"$SRC/$CURRENT\" -o \"$BIN/$TARGET\" ;;\n");
            script.append(" *) echo 'Arquivo incompatível com a compilação direta' >&2; exit 2 ;;\nesac\n");
            script.append("echo \"Compilado: $BIN/$TARGET\"\n");
            return script.toString();
        }
        if (mode.equals("configure") || mode.equals("build")) {
            script.append("if ! command -v git >/dev/null 2>&1; then\n");
            script.append("  echo '[EditorAndroidC] Git ausente: necessario para baixar dependencias CMake, como Luau.' >&2\n");
            script.append("  echo 'Instale no Termux: pkg install git' >&2\n");
            script.append("  exit 127\n");
            script.append("fi\n");
            script.append("echo \"Git: $(command -v git)\"\n");
            script.append("cmake -S \"$SRC\" -B \"$BUILD\" -G 'Unix Makefiles' ");
            script.append("-DCMAKE_C_COMPILER=\"$CC_BIN\" -DCMAKE_CXX_COMPILER=\"$CXX_BIN\" ");
            script.append("-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=\"$BIN\"\n");
            if (mode.equals("build")) {
                script.append("make -C \"$BUILD\" -j2\n");
                script.append("echo \"[EditorAndroidC] Binários em: $BIN\"\n");
                script.append("ls -lh \"$BIN\"\n");
            }
            return script.toString();
        }
        throw new IllegalArgumentException("Comando não suportado");
    }
}
