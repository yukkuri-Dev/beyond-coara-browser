package com.coara.browser;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class htmlview extends AppCompatActivity {

    static {
        System.loadLibrary("highlight_native");
    }
    
    
    public static native int[][] diffHighlightNative(String oldText, String newText);
    
    private EditText urlInput;
    private Button loadButton, editButton, saveButton;
    private EditText htmlEditText;
    private FloatingActionButton revertFab;
    
    private String originalHtml = "";
    
    private final Stack<String> editHistory = new Stack<>();
    
    private boolean isEditing = false;
    
    private volatile boolean isUpdating = false;
    private volatile boolean isLoading = false;
    
    private static final int REQUEST_PERMISSION_WRITE = 100;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler();

    private Runnable highlightRunnable;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.htmlview);

        urlInput = findViewById(R.id.urlInput);
        loadButton = findViewById(R.id.loadButton);
        editButton = findViewById(R.id.editButton);
        saveButton = findViewById(R.id.saveButton);
        htmlEditText = findViewById(R.id.htmlEditText);
        revertFab = findViewById(R.id.revertFab);

    
        htmlEditText.setKeyListener(null);

        loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {                
                String urlStr = urlInput.getText().toString().trim();
                if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                    if (!isLoading) {
                        fetchHtml(urlStr);
                    } else {
                        Toast.makeText(htmlview.this, "読み込み中です。", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(htmlview.this, "正しいURLを入力してください", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isEditing) {
                    
                    editHistory.clear();
                    editHistory.push(htmlEditText.getText().toString());
                    
                    htmlEditText.setKeyListener(new EditText(htmlview.this).getKeyListener());
                    htmlEditText.setFocusableInTouchMode(true);
                    isEditing = true;
                    Toast.makeText(htmlview.this, "編集モードに入りました", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        
        htmlEditText.addTextChangedListener(new TextWatcher() {
            private String beforeChange;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!isUpdating && isEditing) {
                    beforeChange = s.toString();
                }
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (highlightRunnable != null) {
                    uiHandler.removeCallbacks(highlightRunnable);
                }
            }
            @Override
            public void afterTextChanged(final Editable s) {
                if (!isUpdating && isEditing) {
                    final String newText = s.toString();
                
                    if (!newText.equals(beforeChange)) {
                        editHistory.push(beforeChange);
                    }
                    
                    highlightRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (!isUpdating) {
                                isUpdating = true;
                            
                                executor.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        final int[][] spans = diffHighlightNative(newText, newText);
                                        uiHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                applyHighlight(htmlEditText.getText(), spans);
                                                isUpdating = false;
                                            }
                                        });
                                    }
                                });
                            }
                        }
                    };
                    uiHandler.postDelayed(highlightRunnable, 150);
                }
            }
        });
        
        
        revertFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {                
                if (isEditing && !isUpdating) {
                    if (!editHistory.isEmpty()) {
                        final String previousText = editHistory.pop();
                        isUpdating = true;
            
                        Editable editable = htmlEditText.getText();
                        int curPos = htmlEditText.getSelectionStart();
                        editable.replace(0, editable.length(), previousText);
                    
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                final int[][] spans = diffHighlightNative(previousText, previousText);
                                uiHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        applyHighlight(htmlEditText.getText(), spans);
                                        int pos = Math.min(previousText.length(), curPos);
                                        htmlEditText.setSelection(pos);
                                        isUpdating = false;
                                        Toast.makeText(htmlview.this, "変更を元に戻しました", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                    } else {
                        Toast.makeText(htmlview.this, "これ以上前はありません", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {                
                if (ContextCompat.checkSelfPermission(htmlview.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(htmlview.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQUEST_PERMISSION_WRITE);
                } else {
                    saveHtmlToFile();
                }
            }
        });
    }
    
    
    private void fetchHtml(final String urlString) {
        isLoading = true;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final StringBuilder result = new StringBuilder();
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setUseCaches(false);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line).append('\n');
                    }
                    reader.close();
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            originalHtml = result.toString();
                            isEditing = false;
                            editHistory.clear();
                        
                            Editable editable = htmlEditText.getText();
                            editable.clear();
                            editable.append(originalHtml);
                        
                            executor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    final int[][] spans = diffHighlightNative("", originalHtml);
                                    uiHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            applyHighlight(htmlEditText.getText(), spans);
                                            
                                            htmlEditText.setKeyListener(null);
                                            isLoading = false;
                                        }
                                    });
                                }
                            });
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(htmlview.this, "HTMLの取得に失敗しました: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            isLoading = false;
                        }
                    });
                }
            }
        });
    }
    
    
    private void applyHighlight(Editable editable, int[][] spans) {
        if (spans != null) {
        
            Object[] oldSpans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
            for (Object span : oldSpans) {
                editable.removeSpan(span);
            }
            
            for (int[] span : spans) {
                if (span.length == 3) {
                    int start = span[0];
                    int end = span[1];
                    int color = span[2];
                    if (start >= 0 && end <= editable.length() && start < end) {
                        editable.setSpan(new ForegroundColorSpan(color),
                                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }
    }
    

    private void saveHtmlToFile() {
        final String currentText = htmlEditText.getText().toString();
        final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        final String fileName = timeStamp + (!currentText.equals(originalHtml) ? "Edit.html" : ".html");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File file = new File(downloadDir, fileName);
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
                    bos.write(currentText.getBytes(StandardCharsets.UTF_8));
                    bos.flush();
                    bos.close();
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(htmlview.this, "保存しました: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(htmlview.this, "保存に失敗しました: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_WRITE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveHtmlToFile();
            } else {
                Toast.makeText(this, "書き込み権限が必要です", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
