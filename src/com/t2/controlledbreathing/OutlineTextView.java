package com.t2.controlledbreathing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.widget.TextView;

public class OutlineTextView extends TextView {
	private int mOutlineColor = 0xFF000000;
	private int mOutlineSize = 6;
	private int mAlpha = 255;
	private int mPreviousAlpha = 255;

	public OutlineTextView(Context context) {
		super(context);
		mOutlineColor = getInverseColor(getResources().getColor(android.R.color.black));
		mOutlineSize = 2;
		init();
	}

	public OutlineTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public OutlineTextView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	private int getInverseColor(int color) {
		int red = Color.red(color);
		int green = Color.green(color);
		int blue = Color.blue(color);
		int alpha = Color.alpha(color);
		return Color.argb(alpha, 255 - red, 255 - green, 255 - blue);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	private void init() {
		setPadding(getPaddingLeft() + mOutlineSize, getPaddingTop(), getPaddingRight() + mOutlineSize, getPaddingBottom());
	}

	@Override
	protected boolean onSetAlpha(int alpha) {
		mAlpha = alpha;
		return super.onSetAlpha(alpha);
	}

	@Override
	public void draw(Canvas canvas) {
		getPaint().setColor(mOutlineColor);
		getPaint().setStyle(Style.STROKE);
		getPaint().setStrokeWidth(mOutlineSize);
		mPreviousAlpha = getPaint().getAlpha();
		getPaint().setAlpha(mAlpha);
		canvas.save();
		canvas.translate(getCompoundPaddingLeft() + mOutlineSize, getCompoundPaddingTop());
		getLayout().draw(canvas);
		canvas.restore();
		getPaint().setAlpha(mPreviousAlpha);
		getPaint().setColor(0xFFFFFFFF);
		getPaint().setStyle(Style.FILL);
		canvas.save();
		canvas.translate(mOutlineSize, 0);

		super.draw(canvas);
		canvas.restore();
	}
}
