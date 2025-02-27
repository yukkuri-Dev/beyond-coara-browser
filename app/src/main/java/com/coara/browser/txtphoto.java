package com.coara.browser;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class txtphoto extends Activity {

    private static final int REQUEST_CODE_SELECT_FILE = 1;

    private TextView filePathView;
    private String selectedFilePath;
    private Button convertButton, revertButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.txtphoto_main);

        Button selectFileButton = findViewById(R.id.selectFileButton);
        convertButton = findViewById(R.id.convertButton);
        revertButton = findViewById(R.id.revertButton);
        filePathView = findViewById(R.id.filePathView);

        selectFileButton.setOnClickListener(v -> selectFile());

        convertButton.setOnClickListener(v -> {
            if (selectedFilePath != null) {
                convertAsciiToImage(Uri.parse(selectedFilePath));
            }
        });

        revertButton.setOnClickListener(v -> {
            if (selectedFilePath != null) {
                convertDatToImage(Uri.parse(selectedFilePath));
            }
        });
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimeTypes = {"text/plain", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, REQUEST_CODE_SELECT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_FILE && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                selectedFilePath = uri.toString();
                filePathView.setText(selectedFilePath);

                String fileType = getContentResolver().getType(uri);
                if ("text/plain".equals(fileType)) {
                    convertButton.setEnabled(true);
                    revertButton.setEnabled(false);
                } else if ("application/octet-stream".equals(fileType)) {
                    convertButton.setEnabled(false);
                    revertButton.setEnabled(true);
                }
            }
        }
    }

    private void convertAsciiToImage(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder asciiArt = new StringBuilder();
            String line;
            int maxWidth = 0;
            int lineCount = 0;

            Paint paint = new Paint();
            paint.setTextSize(40);
            paint.setTypeface(Typeface.MONOSPACE);

            while ((line = reader.readLine()) != null) {
                asciiArt.append(line).append("\n");
                maxWidth = Math.max(maxWidth, (int) paint.measureText(line));
                lineCount++;
            }

            int charHeight = (int) (paint.getTextSize() + 10);
            int width = maxWidth + 20;
            int height = lineCount * charHeight + 20;

            Bitmap bitmap = createBitmapFromAscii(asciiArt.toString(), width, height, paint, charHeight);
            saveBitmapAsPng(bitmap);

        } catch (IOException e) {
            Toast.makeText(this, "変換中にエラーが発生しました", Toast.LENGTH_SHORT).show();
        }
    }

    private void convertDatToImage(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            List<int[]> pixelData = new ArrayList<>();
            int width = 0, height = 0;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                String[] position = parts[0].split(",");
                String[] rgb = parts[1].split(",");
                int x = Integer.parseInt(position[0]);
                int y = Integer.parseInt(position[1]);
                int r = Integer.parseInt(rgb[0]);
                int g = Integer.parseInt(rgb[1]);
                int b = Integer.parseInt(rgb[2]);
                pixelData.add(new int[]{x, y, r, g, b});
                width = Math.max(width, x);
                height = Math.max(height, y);
            }

            width += 1;
            height += 1;

            Bitmap bitmap = createBitmapFromDat(pixelData, width, height);
            saveBitmapAsPng(bitmap);

        } catch (IOException e) {
            Toast.makeText(this, "変換中にエラーが発生しました", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap createBitmapFromAscii(String asciiArt, int width, int height, Paint paint, int charHeight) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        String[] lines = asciiArt.split("\n");
        int y = charHeight;
        for (String line : lines) {
            canvas.drawText(line, 10, y, paint);
            y += charHeight;
        }

        return bitmap;
    }

    private Bitmap createBitmapFromDat(List<int[]> pixelData, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int[] pixel : pixelData) {
            int x = pixel[0];
            int y = pixel[1];
            int r = pixel[2];
            int g = pixel[3];
            int b = pixel[4];
            bitmap.setPixel(x, y, Color.rgb(r, g, b));
        }
        return bitmap;
    }

    private void saveBitmapAsPng(Bitmap bitmap) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, generateFileName() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TxtPhoto");

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                Toast.makeText(this, "画像が保存されました", Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(this, "保存中にエラーが発生しました", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String generateFileName() {
        return new SimpleDateFormat("MMdd_HHmmss", Locale.getDefault()).format(new Date());
    }
}
