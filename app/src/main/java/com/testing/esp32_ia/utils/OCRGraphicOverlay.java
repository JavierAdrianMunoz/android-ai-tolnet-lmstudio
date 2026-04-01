package com.testing.esp32_ia.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.List;

public class OCRGraphicOverlay extends View {

    private final Paint boxPaint;
    private final Paint textPaint;

    private List<Text.TextBlock> blocks = new ArrayList<>();

    public OCRGraphicOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setColor(0xFFFFC107); // amarillo tipo Lens
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);

        textPaint = new Paint();
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(40f);
    }

    public void setBlocks(List<Text.TextBlock> blocks) {
        this.blocks = blocks;
        postInvalidate(); // refresca UI
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (Text.TextBlock block : blocks) {
            Rect rect = block.getBoundingBox();
            if (rect == null) continue;

            canvas.drawRect(rect, boxPaint);
            canvas.drawText(block.getText(), rect.left, rect.top - 10, textPaint);
        }
    }
}