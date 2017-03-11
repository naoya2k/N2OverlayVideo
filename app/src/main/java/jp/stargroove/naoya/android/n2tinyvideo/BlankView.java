package jp.stargroove.naoya.android.n2tinyvideo;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

public class BlankView extends View {
    int mColor = 0x01000000;

    public BlankView(Context context) {
        super(context);
    }
    public BlankView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public BlankView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    public void setColor(int color) {
        mColor = color;
    }
    
    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(mColor);
    }

}
