package com.coara.browser;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class txtphoto extends Fragment {

    private static final int REQUEST_CODE_SELECT_FILE = 1;

    private TextView filePathView;
    private String selectedFilePath;
    private Button convertButton, revertButton;

    public txtphoto() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, 
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.txtphoto_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button selectFileButton = view.findViewById(R.id.selectFileButton);
        convertButton = view.findViewById(R.id.convertButton);
        revertButton = view.findViewById(R.id.revertButton);
        filePathView = view.findViewById(R.id.filePathView);

        selectFileButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQUEST_CODE_SELECT_FILE);
        });

        convertButton.setOnClickListener(v -> {
            if (selectedFilePath != null && selectedFilePath.endsWith(".txt")) {
                convertAsciiToImage(selectedFilePath);
            }
        });

        revertButton.setOnClickListener(v -> {
            if (selectedFilePath != null && selectedFilePath.endsWith(".dat")) {
                convertDatToImage(selectedFilePath);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_FILE && resultCode == getActivity().RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                selectedFilePath = uri.toString();
                filePathView.setText(selectedFilePath);

                if (selectedFilePath.endsWith(".txt")) {
                    convertButton.setEnabled(true);
                    revertButton.setEnabled(false);
                } else if (selectedFilePath.endsWith(".dat")) {
                    convertButton.setEnabled(false);
                    revertButton.setEnabled(true);
                }
            }
        }
    }

    private void convertAsciiToImage(String filePath) {
        try {
            Uri uri = Uri.parse(filePath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    requireActivity().getContentResolver().openInputStream(uri)));
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
            reader.close();

            int charHeight = (int) (paint.getTextSize() + 10);
            int width = maxWidth + 20;
            int height = lineCount * charHeight + 20;

            Bitmap bitmap = createBitmapFromAscii(asciiArt.toString(), width, height, paint, charHeight);
            saveBitmapAsPng(bitmap);

            Toast.makeText(requireContext(), "変換と保存が完了しました", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "変換中にエラーが発生しました", Toast.LENGTH_SHORT).show();
        }
    }

    private void convertDatToImage(String filePath) {
        try {
            Uri uri = Uri.parse(filePath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    requireActivity().getContentResolver().openInputStream(uri)));
            String line;
            List<int[]> pixelData = new ArrayList<>();
            int width = 0;
            int height = 0;

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
            reader.close();

            width += 1;
            height += 1;

            Bitmap bitmap = createBitmapFromDat(pixelData, width, height);
            saveBitmapAsPng(bitmap);

            Toast.makeText(requireContext(), "変換と保存が完了しました", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "変換中にエラーが発生しました", Toast.LENGTH_SHORT).show();
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
            int color = Color.rgb(r, g, b);
            bitmap.setPixel(x, y, color);
        }
        return bitmap;
    }

    private void saveBitmapAsPng(Bitmap bitmap) {
        try {
            String fileName = generateFileName();
            File outputDir = requireActivity().getExternalFilesDir(null);
            if (outputDir != null) {
                File outputFile = new File(outputDir, fileName + ".png");
                FileOutputStream out = new FileOutputStream(outputFile);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.close();
                Toast.makeText(requireContext(), "画像が保存されました: " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "画像の保存中にエラーが発生しました", Toast.LENGTH_SHORT).show();
        }
    }

    private String generateFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMdd_HHmmss");
        Date now = new Date();
        return sdf.format(now);
    }
}
