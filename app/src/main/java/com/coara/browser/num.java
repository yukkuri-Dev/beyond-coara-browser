package com.coara.browser;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class num extends AppCompatActivity {

  
    private static final long[] O = new long[2];
    static {
        long a = 0L;
        for (int i = 1; i < 64; i += 2) a |= 1L << i;
        O[0] = a;
        a = 0L;
        for (int i = 65; i < 100; i += 2) a |= 1L << (i - 64);
        O[1] = a;
    }

    
    private interface F { boolean m(int n); }
    private static final F X = new F() {
        @Override
        public boolean m(int n) {
            
            return n >= 1 && n <= 99 && (((O[n >>> 6]) >>> (n & 0x3F)) & 1L) == 1;
        }
    };

    private EditText i;
    private Button j;
    private TextView k;

    @Override
    protected void onCreate(Bundle l) {
        super.onCreate(l);
        setContentView(R.layout.num);
        i = findViewById(R.id.editTextNumber);
        j = findViewById(R.id.buttonCheck);
        k = findViewById(R.id.textViewResult);

        j.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String s = i.getText().toString().trim();
                try {
                    int n = Integer.parseInt(s);
                    k.setText(X.m(n)
                        ? String.format("%d は奇数です。", n)
                        : String.format("%d は奇数ではありません。", n));
                } catch (Exception e) {
                    k.setText("無効な入力");
                }
            }
        });
    }
}
