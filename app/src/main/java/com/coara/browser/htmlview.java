package com.coara.browser;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.Spannable;
import android.text.SpannableString;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class htmlview extends AppCompatActivity {

    private EditText urlInput;
    private Button loadButton;
    private Button editButton;
    private Button saveButton;
    private EditText htmlEditText; 
    private FloatingActionButton revertFab;
  
    private String originalHtml = "";   
    private String preEditCache = "";   
    private boolean isEditing = false; 

    private static final int REQUEST_PERMISSION_WRITE = 100;

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

                String urlString = urlInput.getText().toString().trim();
                if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
                    new FetchHtmlTask().execute(urlString);
                } else {
                    Toast.makeText(htmlview.this, "正しいURLを入力してください", Toast.LENGTH_SHORT).show();
                }
            }
        });

        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!isEditing) {
                    preEditCache = htmlEditText.getText().toString();
                    htmlEditText.setKeyListener(new EditText(MainActivity.this).getKeyListener());
                    isEditing = true;
                    Toast.makeText(htmlview.this, "編集モードに入りました", Toast.LENGTH_SHORT).show();
                }
            }
        });

      
        revertFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (isEditing) {
                    if (!preEditCache.isEmpty()) {
                        htmlEditText.setText(preEditCache);
                        Toast.makeText(htmlview.this, "変更を元に戻しました", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(htmlview.this, "これ以上前はありません", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

      
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQUEST_PERMISSION_WRITE);
                } else {
                    saveHtmlToFile();
                }
            }
        });
    }

    
    private class FetchHtmlTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String urlString = params[0];
            StringBuilder result = new StringBuilder();
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }
                reader.close();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            return result.toString();
        }

        @Override
        protected void onPostExecute(String html) {
            if (html != null) {
                originalHtml = html;
                isEditing = false;
                preEditCache = "";
            
                Spannable highlighted = highlightHtml(html);
                htmlEditText.setText(highlighted);
          
                htmlEditText.setKeyListener(null);
            } else {
                Toast.makeText(htmlview.this, "HTMLの取得に失敗しました", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Spannable highlightHtml(String html) {
        SpannableString spannable = new SpannableString(html);
    
        int tagColor = 0xFF0000FF;    
        int attributeColor = 0xFF008000; 
        int valueColor = 0xFFB22222;     

    
        Pattern tagPattern = Pattern.compile("<[^>]+>");
        Matcher tagMatcher = tagPattern.matcher(html);
        while (tagMatcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(tagColor),
                    tagMatcher.start(), tagMatcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            
            String tagText = html.substring(tagMatcher.start(), tagMatcher.end());
            Pattern attrPattern = Pattern.compile("(\\w+)=\\\"([^\\\"]*)\\\"");
            Matcher attrMatcher = attrPattern.matcher(tagText);
            while (attrMatcher.find()) {
                int attrNameStart = tagMatcher.start() + attrMatcher.start(1);
                int attrNameEnd = tagMatcher.start() + attrMatcher.end(1);
                spannable.setSpan(new ForegroundColorSpan(attributeColor),
                        attrNameStart, attrNameEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                int attrValueStart = tagMatcher.start() + attrMatcher.start(2);
                int attrValueEnd = tagMatcher.start() + attrMatcher.end(2);
                spannable.setSpan(new ForegroundColorSpan(valueColor),
                        attrValueStart, attrValueEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return spannable;
    }

    
    private void saveHtmlToFile() {
        String currentText = htmlEditText.getText().toString();
      
        boolean isEdited = !currentText.equals(originalHtml);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = timeStamp + (isEdited ? "Edit.html" : ".html");
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(downloadDir, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(currentText.getBytes("UTF-8"));
            fos.close();
            Toast.makeText(htmlview.this, "保存しました: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(htmlview.this, "保存に失敗しました", Toast.LENGTH_SHORT).show();
        }
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
