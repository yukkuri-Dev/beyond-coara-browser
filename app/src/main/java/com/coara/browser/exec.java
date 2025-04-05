package com.coara.browser;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class exec extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1002;

    private Process currentProcess;
    private File selectedBinary;
    private ScheduledExecutorService timeoutExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.exec);

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                                               FileChooserParams fileChooserParams) {
                exec.this.filePathCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                return true;
            }
        });
        webView.addJavascriptInterface(new JSInterface(), "Android");
        webView.loadUrl("file:///android_asset/exec.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                selectedBinary = copyFileToInternalStorage(uri);
                if (selectedBinary != null) {
            
                    boolean executableSet = selectedBinary.setExecutable(true, false);
                
                    if (!executableSet) {
                        try {
                            Process chmod = Runtime.getRuntime().exec("chmod 755 " + selectedBinary.getAbsolutePath());
                            chmod.waitFor();
                        } catch (Exception e) {
                    
                        }
                    }
                    if (selectedBinary.canExecute()) {
                        runOnUiThread(() -> webView.evaluateJavascript(
                                "javascript:showToast('バイナリが選択され、実行権限が付与されました: " 
                                + escapeForJS(selectedBinary.getAbsolutePath()) + "')", null));
                    } else {
                        runOnUiThread(() -> webView.evaluateJavascript(
                                "javascript:showToast('バイナリ選択または実行権限付与に失敗しました。')", null));
                    }
                }
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(new Uri[]{uri});
                filePathCallback = null;
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public class JSInterface {
        @JavascriptInterface
        public void executeCommand(String command) {
            if (command.trim().isEmpty() && (selectedBinary == null || !selectedBinary.exists())) {
                runOnUiThread(() -> webView.evaluateJavascript(
                        "javascript:showToast('コマンドまたはバイナリを指定してください。')", null));
                return;
            }
            if (selectedBinary != null && selectedBinary.exists()) {
                command = selectedBinary.getAbsolutePath() + (command.trim().isEmpty() ? "" : " " + command);
            }
            executeCommandInternal(command);
        }

        @JavascriptInterface
        public void clearBinary() {
            selectedBinary = null;
            runOnUiThread(() -> webView.evaluateJavascript(
                    "javascript:showToast('バイナリが解除されました。')", null));
        }

        @JavascriptInterface
        public void stopCommand() {
            if (currentProcess != null && currentProcess.isAlive()) {
                currentProcess.destroy();
                runOnUiThread(() -> webView.evaluateJavascript(
                        "javascript:appendOutput('INFO: コマンドが強制終了されました\\n')", null));
            } else {
                runOnUiThread(() -> webView.evaluateJavascript(
                        "javascript:showToast('実行中のプロセスはありません。')", null));
            }
        }

        @JavascriptInterface
        public void toggleKeyboard() {
            runOnUiThread(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                }
            });
        }
    }

    private void executeCommandInternal(String command) {
        runOnUiThread(() -> webView.evaluateJavascript("javascript:clearOutput()", null));
        try {
            currentProcess = Runtime.getRuntime().exec(command);
            StringBuilder outputBuilder = new StringBuilder();

            timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
            timeoutExecutor.schedule(() -> {
                if (currentProcess.isAlive()) {
                    currentProcess.destroy();
                    runOnUiThread(() -> webView.evaluateJavascript(
                            "javascript:appendOutput('INFO: タイムアウトにより強制終了されました\\n')", null));
                }
            }, 30, TimeUnit.SECONDS);

            Executors.newSingleThreadExecutor().submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(currentProcess.getErrorStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputBuilder.append(line).append("\n");
                        final String finalLine = line;
                        runOnUiThread(() -> webView.evaluateJavascript(
                                "javascript:appendOutput('" + escapeForJS(finalLine) + "\\n')", null));
                    }
                    while ((line = errorReader.readLine()) != null) {
                        outputBuilder.append("ERROR: ").append(line).append("\n");
                        final String finalErrorLine = line;
                        runOnUiThread(() -> webView.evaluateJavascript(
                                "javascript:appendOutput('ERROR: " + escapeForJS(finalErrorLine) + "\\n')", null));
                    }
                    saveLogToFile(command, outputBuilder.toString());
                } catch (IOException e) {
                    runOnUiThread(() -> webView.evaluateJavascript(
                            "javascript:appendOutput('ERROR: " + escapeForJS(e.getMessage()) + "\\n')", null));
                }
            });
        } catch (IOException e) {
            runOnUiThread(() -> webView.evaluateJavascript(
                    "javascript:appendOutput('ERROR: " + escapeForJS(e.getMessage()) + "')", null));
        }
    }

    private String escapeForJS(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "");
    }

    @Nullable
    private File copyFileToInternalStorage(Uri uri) {
        File directory = new File(getFilesDir(), "binaries");
        if (!directory.exists() && !directory.mkdirs()) {
            runOnUiThread(() -> webView.evaluateJavascript(
                    "javascript:showToast('ディレクトリ作成に失敗しました。')", null));
            return null;
        }
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;
            String fileName = getFileName(uri);
            File destFile = new File(directory, fileName);
            try (OutputStream outputStream = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }
            return destFile;
        } catch (IOException e) {
            runOnUiThread(() -> webView.evaluateJavascript(
                    "javascript:showToast('ファイルのコピーに失敗しました: " + escapeForJS(e.getMessage()) + "')", null));
            return null;
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void saveLogToFile(String command, String logContent) {
        File directory = new File(getExternalFilesDir(null), "command_logs");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = command.replaceAll("[^a-zA-Z0-9]", "_") + "_" + timeStamp + ".txt";
        File logFile = new File(directory, fileName);
        try (FileOutputStream fos = new FileOutputStream(logFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos)) {
            writer.write(logContent);
        } catch (Exception e) {
            runOnUiThread(() -> webView.evaluateJavascript(
                    "javascript:showToast('ログ保存中にエラー: " + escapeForJS(e.getMessage()) + "')", null));
        }
    }
}
