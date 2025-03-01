package com.coara.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class SearchResultsOverlay extends View {
    private List<Float> markers = new ArrayList<>();
    private Paint paint;

    public SearchResultsOverlay(Context context) {
        super(context);
        init();
    }
    public SearchResultsOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth(4);
    }
    public void setMarkers(List<Float> markers) {
        this.markers = markers;
        invalidate();
    }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int height = getHeight();
        for (Float marker : markers) {
            float y = marker * height;
            canvas.drawLine(0, y, getWidth(), y, paint);
        }
    }
}
