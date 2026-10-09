package com.maxold.editorandroidc;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

public class CodeEditText extends EditText {
    private Runnable onEditorScroll;
    public CodeEditText(Context context) { super(context); }
    public CodeEditText(Context context, AttributeSet attrs) { super(context, attrs); }
    public void setOnEditorScroll(Runnable callback) { onEditorScroll = callback; }
    @Override
    protected void onScrollChanged(int horizontal, int vertical, int oldHorizontal, int oldVertical) {
        super.onScrollChanged(horizontal, vertical, oldHorizontal, oldVertical);
        if (onEditorScroll != null) onEditorScroll.run();
    }
}
