package com.maxold.editorandroidc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.view.View;

public class LineGutterView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private CodeEditText editor;

    public LineGutterView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(29, 38, 51));
        paint.setColor(Color.rgb(130, 148, 166));
        paint.setTextAlign(Paint.Align.RIGHT);
    }
    public void connect(CodeEditText text) {
        editor = text;
        text.setOnEditorScroll(this::invalidate);
        invalidate();
    }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (editor == null || editor.getLayout() == null) return;
        Layout layout = editor.getLayout();
        paint.setTextSize(editor.getTextSize() * 0.8f);
        int offsetY = editor.getScrollY();
        int first = layout.getLineForVertical(Math.max(0, offsetY));
        int last = layout.getLineForVertical(Math.max(0, offsetY + getHeight()));
        for (int index = first; index <= last; index++) {
            float baseline = editor.getTotalPaddingTop() + layout.getLineBaseline(index) - offsetY;
            canvas.drawText(Integer.toString(index + 1), getWidth() - 10, baseline, paint);
        }
    }
}
