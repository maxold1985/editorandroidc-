package com.maxold.editorandroidc;

import android.graphics.Color;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SyntaxHighlighter {
    private static final Pattern WORDS = Pattern.compile(
        "\\b(?:auto|bool|break|case|catch|char|class|const|constexpr|continue|default|delete|do|double|else|enum|explicit|extern|false|float|for|friend|if|inline|int|long|namespace|new|nullptr|operator|private|protected|public|return|short|signed|sizeof|static|std|struct|switch|template|this|throw|true|try|typedef|typename|union|unsigned|using|virtual|void|volatile|while)\\b"
    );
    private static final Pattern NUMBERS = Pattern.compile("\\b(?:0[xX][0-9a-fA-F]+|\\d+(?:\\.\\d+)?)\\b");
    private static final Pattern PREPROCESSOR = Pattern.compile("(?m)^\\s*#\\s*\\w+[^\\n]*");
    private static final Pattern LITERALS = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");
    private static final Pattern COMMENTS = Pattern.compile("//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private SyntaxHighlighter() { }
    public static void apply(Editable editable, String filename) {
        if (editable == null) return;
        ForegroundColorSpan[] spans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) editable.removeSpan(span);
        String value = editable.toString();
        boolean isCpp = filename.endsWith(".cpp") || filename.endsWith(".c") || filename.endsWith(".h") || filename.endsWith(".hpp");
        if (isCpp) {
            paint(editable, value, WORDS, Color.rgb(130, 192, 246));
            paint(editable, value, NUMBERS, Color.rgb(203, 164, 250));
            paint(editable, value, PREPROCESSOR, Color.rgb(203, 164, 250));
            paint(editable, value, LITERALS, Color.rgb(240, 198, 116));
            paint(editable, value, COMMENTS, Color.rgb(128, 151, 164));
        } else if (filename.equalsIgnoreCase("CMakeLists.txt") || filename.endsWith(".cmake")) {
            paint(editable, value, Pattern.compile("(?m)^\\s*(cmake_minimum_required|project|add_executable|add_library|target_link_libraries|set|include|find_package|if|endif|message|install)\\b"), Color.rgb(130, 192, 246));
            paint(editable, value, Pattern.compile("#[^\\n]*"), Color.rgb(128, 151, 164));
            paint(editable, value, LITERALS, Color.rgb(145, 205, 132));
        }
    }
    private static void paint(Editable editable, String value, Pattern regex, int color) {
        Matcher matcher = regex.matcher(value);
        while (matcher.find()) {
            editable.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
