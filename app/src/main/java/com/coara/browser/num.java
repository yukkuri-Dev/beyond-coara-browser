package com.coara.browser;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

public class MainActivity extends AppCompatActivity {

  
    private EditText editText;
    private Button calcButton;
    private TextView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.num);


        editText = findViewById(R.id.editTextNumber);
        calcButton = findViewById(R.id.buttonCheck);
        resultView = findViewById(R.id.textViewResult);

        // ボタン押下時に入力された式を計算して結果を表示
        calcButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = editText.getText().toString().trim();
                try {
                    // 式の解析と計算
                    BigDecimal result = new ExpressionEvaluator().parse(input);
                    String output;
                    // 小数点以下がない（整数）場合は奇数／偶数を判定
                    BigDecimal stripped = result.stripTrailingZeros();
                    if (stripped.scale() <= 0) {
                        BigInteger intResult = stripped.toBigIntegerExact();
                        String parity = intResult.mod(BigInteger.valueOf(2)).equals(BigInteger.ONE) ? "奇数" : "偶数";
                        output = "結果: " + result.toPlainString() + " (" + parity + ")";
                    } else {
                        output = "結果: " + result.toPlainString() + " (小数)";
                    }
                    resultView.setText(output);
                } catch (Exception e) {
                    resultView.setText("計算エラー: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 内部クラス ExpressionEvaluato
     * 四則演算および括弧を含む式を解析・計算します。
     * BigDecimal（MathContext.DECIMAL128）を利用して無限に近い精度で動作します。
     */
    private class ExpressionEvaluator {
        private String str;
        private int pos = -1;
        private int ch;

        // 文字列全体を解析し、計算結果を返します
        public BigDecimal parse(String s) {
            this.str = s;
            pos = -1;
            nextChar();
            BigDecimal x = parseExpression();
            if (pos < str.length()) {
                throw new RuntimeException("不正な文字: " + (char) ch);
            }
            return x;
        }

        // 次の文字を読み込みます
        private void nextChar() {
            pos++;
            ch = pos < str.length() ? str.charAt(pos) : -1;
        }

  
        private boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        // expression = term { ('+' | '-') term }
        private BigDecimal parseExpression() {
            BigDecimal x = parseTerm();
            while (true) {
                if (eat('+')) {
                    x = x.add(parseTerm(), MathContext.DECIMAL128);
                } else if (eat('-')) {
                    x = x.subtract(parseTerm(), MathContext.DECIMAL128);
                } else {
                    return x;
                }
            }
        }

        // term = factor { ('*' | '/') factor }
        private BigDecimal parseTerm() {
            BigDecimal x = parseFactor();
            while (true) {
                if (eat('*')) {
                    x = x.multiply(parseFactor(), MathContext.DECIMAL128);
                } else if (eat('/')) {
                    BigDecimal denominator = parseFactor();
                    if (denominator.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException("ゼロ除算");
                    }
                    x = x.divide(denominator, MathContext.DECIMAL128);
                } else {
                    return x;
                }
            }
        }

        // factor = ['+' | '-'] ( number | '(' expression ')' )
        private BigDecimal parseFactor() {
            if (eat('+')) return parseFactor(); 
            if (eat('-')) return parseFactor().negate();

            BigDecimal x;
            int startPos = pos;
            if (eat('(')) {
                x = parseExpression();
                if (!eat(')')) {
                    throw new RuntimeException("括弧が閉じられていません");
                }
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                String numStr = str.substring(startPos, pos);
                x = new BigDecimal(numStr, MathContext.DECIMAL128);
            } else {
                throw new RuntimeException("予期しない文字: " + (char) ch);
            }
            return x;
        }
    }
}
