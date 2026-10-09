package com.maxold.editorandroidc;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.documentfile.provider.DocumentFile;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends ComponentActivity {
    private static final String PREFS = "editorandroidc";
    private static final String TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final int BACKGROUND = Color.rgb(18, 24, 33);
    private static final int PANEL = Color.rgb(30, 39, 52);
    private static WeakReference<MainActivity> active = new WeakReference<>(null);

    private ActivityResultLauncher<Uri> selectFolder;
    private DocumentFile folder;
    private DocumentFile currentFile;
    private String sharedFolderPath;
    private String currentName = "main.cpp";
    private boolean dirty;
    private boolean initializing;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable highlightTask;
    private CodeEditText editor;
    private LineGutterView gutter;
    private TextView filename;
    private TextView output;
    private ScrollView console;
    private EditText target;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        selectFolder = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri == null) return;
            if (resolveSharedFolder(uri) == null) {
                toast("Selecione uma subpasta em Documents ou Download no armazenamento interno.");
                return;
            }
            try {
                getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getPreferences(0).edit().putString("folder", uri.toString()).apply();
                attachFolder(uri);
            } catch (Exception e) { error("Pasta: " + e.getMessage()); }
        });
        createUi();
        String saved = getPreferences(0).getString("folder", null);
        if (saved != null) attachFolder(Uri.parse(saved));
        output.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("lastResult", "Console pronto."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = new WeakReference<>(this);
        if (output != null) output.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("lastResult", "Console pronto."));
    }

    @Override
    protected void onPause() {
        if (active.get() == this) active.clear();
        super.onPause();
    }

    public static void deliverLog(String text) {
        MainActivity activity = active.get();
        if (activity != null) activity.runOnUiThread(() -> {
            activity.output.setText(text);
            activity.console.setVisibility(View.VISIBLE);
        });
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private Button button(String label, LinearLayout bar, Runnable action) {
        Button result = new Button(this);
        result.setText(label);
        result.setTextColor(Color.WHITE);
        result.setAllCaps(false);
        result.setTextSize(12);
        result.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        lp.setMargins(dp(2), 0, dp(2), 0);
        bar.addView(result, lp);
        result.setOnClickListener(v -> action.run());
        return result;
    }

    private void createUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        setContentView(root);

        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        bar.setOrientation(LinearLayout.HORIZONTAL);
        toolbarScroll.addView(bar);
        root.addView(toolbarScroll);
        button("Pasta", bar, () -> selectFolder.launch(null));
        button("Arquivos", bar, this::openFileDialog);
        button("Novo", bar, this::newFileDialog);
        button("Salvar", bar, this::saveCurrentFile);
        button("CMake", bar, () -> executeTermux("configure"));
        button("Make", bar, () -> executeTermux("build"));
        button("Executar", bar, () -> executeTermux("run"));
        button("Perm Termux", bar, this::askTermuxPermission);
        button("Log", bar, () -> console.setVisibility(console.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        LinearLayout title = new LinearLayout(this);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(12), dp(3), dp(10), dp(3));
        filename = new TextView(this);
        filename.setTextColor(Color.rgb(156, 207, 255));
        filename.setTextSize(14);
        filename.setText("Selecione a pasta do projeto");
        title.addView(filename, new LinearLayout.LayoutParams(0, dp(36), 1));
        target = new EditText(this);
        target.setSingleLine(true);
        target.setHint("Executável");
        target.setText("editor_sample");
        target.setTextSize(12);
        target.setTextColor(Color.WHITE);
        target.setHintTextColor(Color.LTGRAY);
        target.setPadding(dp(5), 0, dp(5), 0);
        title.addView(target, new LinearLayout.LayoutParams(dp(122), dp(36)));
        root.addView(title);

        LinearLayout codeRow = new LinearLayout(this);
        codeRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(codeRow, new LinearLayout.LayoutParams(-1, 0, 1));
        gutter = new LineGutterView(this);
        codeRow.addView(gutter, new LinearLayout.LayoutParams(dp(50), -1));
        editor = new CodeEditText(this);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(dp(8), dp(12), dp(12), dp(12));
        editor.setBackgroundColor(BACKGROUND);
        editor.setTextColor(Color.rgb(220, 234, 244));
        editor.setTextSize(14);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(true);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextColor(Color.rgb(220, 234, 244));
        editor.setTextSize(14);
        editor.setGravity(Gravity.TOP | Gravity.START);
        codeRow.addView(editor, new LinearLayout.LayoutParams(0, -1, 1));
        gutter.connect(editor);
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (!initializing) { dirty = true; updateTitle(); }
                gutter.invalidate();
                if (highlightTask != null) handler.removeCallbacks(highlightTask);
                highlightTask = () -> SyntaxHighlighter.apply(editor.getText(), currentName);
                handler.postDelayed(highlightTask, 130);
            }
        });

        console = new ScrollView(this);
        console.setBackgroundColor(Color.rgb(10, 16, 22));
        console.setVisibility(View.GONE);
        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(12);
        output.setTextColor(Color.rgb(170, 230, 185));
        output.setTextIsSelectable(true);
        output.setPadding(dp(12), dp(8), dp(12), dp(8));
        console.addView(output);
        root.addView(console, new LinearLayout.LayoutParams(-1, dp(180)));
    }

    private void attachFolder(Uri uri) {
        folder = DocumentFile.fromTreeUri(this, uri);
        sharedFolderPath = resolveSharedFolder(uri);
        if (folder == null || !folder.isDirectory() || sharedFolderPath == null) {
            folder = null;
            toast("Pasta inacessível ou incompatível com Termux");
            return;
        }
        try {
            if (folder.listFiles().length == 0) createSamples();
            DocumentFile startup = folder.findFile("main.cpp");
            if (startup == null) startup = folder.findFile("CMakeLists.txt");
            if (startup != null) loadFile(startup, startup.getName());
            else { currentFile = null; filename.setText(sharedFolderPath); openFileDialog(); }
        } catch (Exception e) { error("Falha ao abrir: " + e.getMessage()); }
    }

    private String resolveSharedFolder(Uri uri) {
        String id = DocumentsContract.getTreeDocumentId(uri);
        if (id == null) return null;
        if (!(id.startsWith("primary:Documents/") || id.startsWith("primary:Download/"))) return null;
        String relative = id.substring("primary:".length());
        for (String component : relative.split("/")) {
            if (component.isEmpty() || component.equals(".") || component.equals("..")) return null;
        }
        return "/storage/emulated/0/" + relative;
    }

    private void createSamples() throws Exception {
        createAndWrite("main.cpp", "#include <iostream>\n\nint main() {\n    std::cout << \"Ola do Termux!\\n\";\n    return 0;\n}\n");
        createAndWrite("CMakeLists.txt", "cmake_minimum_required(VERSION 3.16)\nproject(EditorAndroidC LANGUAGES CXX)\nset(CMAKE_CXX_STANDARD 17)\nadd_executable(editor_sample main.cpp)\n");
    }

    private void createAndWrite(String name, String content) throws Exception {
        if (folder == null) throw new IllegalStateException("Selecione a pasta primeiro");
        DocumentFile file = folder.createFile("text/plain", name);
        if (file == null) throw new IllegalStateException("Nao foi possivel criar " + name);
        try (OutputStream out = getContentResolver().openOutputStream(file.getUri(), "wt")) {
            if (out == null) throw new IllegalStateException("Sem permissao de escrita");
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void loadFile(DocumentFile selected, String name) throws Exception {
        if (dirty && currentFile != null) {
            new AlertDialog.Builder(this).setTitle("Descartar alterações?")
                .setMessage("O arquivo atual possui modificações não salvas.")
                .setNegativeButton("Cancelar", (d,w) -> { })
                .setPositiveButton("Abrir", (d,w) -> readFile(selected, name)).show();
        } else readFile(selected, name);
    }

    private void readFile(DocumentFile selected, String name) {
        try (InputStream in = getContentResolver().openInputStream(selected.getUri())) {
            if (in == null) throw new IllegalStateException("Arquivo indisponível");
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                data.write(buffer, 0, count);
                if (data.size() > 2_000_000) throw new IllegalStateException("Limite: 2 MB por arquivo");
            }
            initializing = true;
            currentFile = selected;
            currentName = name;
            editor.setText(data.toString(StandardCharsets.UTF_8.name()));
            editor.setSelection(0);
            dirty = false;
            initializing = false;
            updateTitle();
            SyntaxHighlighter.apply(editor.getText(), currentName);
        } catch (Exception e) { initializing = false; error(e.getMessage()); }
    }

    private void updateTitle() {
        filename.setText((dirty ? "● " : "") + currentName);
    }

    private boolean saveCurrentFile() {
        if (currentFile == null) { toast("Abra ou crie um arquivo"); return false; }
        try (OutputStream out = getContentResolver().openOutputStream(currentFile.getUri(), "wt")) {
            if (out == null) throw new IllegalStateException("Arquivo somente leitura");
            out.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
            dirty = false;
            updateTitle();
            toast("Salvo: " + currentName);
            return true;
        } catch (Exception e) { error("Salvar: " + e.getMessage()); return false; }
    }

    private void openFileDialog() {
        if (folder == null) { toast("Selecione a pasta do projeto"); return; }
        List<DocumentFile> items = new ArrayList<>();
        List<String> names = new ArrayList<>();
        collectFiles(folder, "", 0, items, names);
        if (items.isEmpty()) { toast("Pasta sem arquivos de texto"); return; }
        String[] options = names.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Arquivos do projeto")
            .setItems(options, (d, which) -> {
                try { loadFile(items.get(which), options[which]); }
                catch (Exception e) { error(e.getMessage()); }
            }).show();
    }

    private void collectFiles(DocumentFile directory, String prefix, int depth,
                              List<DocumentFile> files, List<String> names) {
        if (depth > 5 || files.size() > 250) return;
        for (DocumentFile item : directory.listFiles()) {
            String name = item.getName();
            if (name == null || name.startsWith(".")) continue;
            if (item.isDirectory()) {
                if (!name.equals("build") && !name.equals(".git"))
                    collectFiles(item, prefix + name + "/", depth + 1, files, names);
            } else if (item.isFile() && isText(name)) {
                files.add(item);
                names.add(prefix + name);
            }
        }
    }

    private boolean isText(String name) {
        String value = name.toLowerCase(Locale.ROOT);
        return value.equals("cmakelists.txt") || value.endsWith(".c") || value.endsWith(".h")
            || value.endsWith(".cpp") || value.endsWith(".hpp") || value.endsWith(".txt")
            || value.endsWith(".cmake") || value.endsWith(".md");
    }

    private void newFileDialog() {
        if (folder == null) { toast("Selecione a pasta do projeto"); return; }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("src/arquivo.cpp");
        new AlertDialog.Builder(this).setTitle("Novo arquivo")
            .setView(input).setNegativeButton("Cancelar", null)
            .setPositiveButton("Criar", (d,w) -> {
                String path = input.getText().toString().trim();
                if (!path.matches("[A-Za-z0-9_./+\\-]{1,120}") || path.startsWith("/")
                    || path.contains("..") || path.endsWith("/")) {
                    error("Nome inválido: use letras, números, /, _, . e -"); return;
                }
                try {
                    String[] parts = path.split("/");
                    DocumentFile parent = folder;
                    for (int i = 0; i < parts.length - 1; i++) {
                        DocumentFile child = parent.findFile(parts[i]);
                        if (child == null) child = parent.createDirectory(parts[i]);
                        if (child == null || !child.isDirectory()) throw new IllegalStateException("Pasta inválida");
                        parent = child;
                    }
                    if (parent.findFile(parts[parts.length - 1]) != null)
                        throw new IllegalStateException("Arquivo já existe");
                    DocumentFile f = parent.createFile("text/plain", parts[parts.length - 1]);
                    if (f == null) throw new IllegalStateException("Sem permissão");
                    loadFile(f, path);
                } catch (Exception e) { error(e.getMessage()); }
            }).show();
    }

    private void askTermuxPermission() {
        if (checkSelfPermission(TERMUX_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            toast("Permissão Termux concedida");
        } else {
            requestPermissions(new String[]{TERMUX_PERMISSION}, 212);
        }
    }

    private void executeTermux(String mode) {
        if (folder == null || sharedFolderPath == null) { toast("Selecione a pasta primeiro"); return; }
        if (dirty && !saveCurrentFile()) return;
        String name = target.getText().toString().trim();
        if (!name.matches("[A-Za-z0-9_+.-]{1,90}") || name.equals(".") || name.equals("..")) {
            error("Executável inválido (ex.: editor_sample)"); return;
        }
        if (checkSelfPermission(TERMUX_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            askTermuxPermission();
            toast("Conceda a permissão e toque novamente no comando");
            return;
        }
        try {
            String command = TermuxBridge.command(mode, sharedFolderPath, name);
            String label = mode.equals("configure") ? "CMake" : mode.equals("build") ? "Make" : "Executar";
            output.setText("Executando " + label + " via Termux...\nProjeto: " + sharedFolderPath);
            console.setVisibility(View.VISIBLE);
            TermuxBridge.execute(this, label, command);
        } catch (Exception e) { error("Termux: " + e.getMessage()); }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void error(String message) {
        toast(message);
        output.setText("ERRO: " + message);
        console.setVisibility(View.VISIBLE);
    }
}
