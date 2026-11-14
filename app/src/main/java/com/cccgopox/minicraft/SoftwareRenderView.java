package com.cccgopox.minicraft;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/**
 * Android presentation surface for MiniCraft's original software renderer.
 *
 * The desktop renderer writes packed RGB pixels into a row-major int[] buffer.
 * This view accepts that same buffer and presents it through Android Bitmap
 * and Canvas without changing the renderer's pixel colours or world logic.
 */
public final class SoftwareRenderView extends View {
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Rect destination = new Rect();
    private Bitmap frame;
    private int frameWidth;
    private int frameHeight;
    private boolean smooth = true;

    public SoftwareRenderView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
    }

    /**
     * Copies one complete RGB frame into the Android bitmap.
     * The input must contain at least width * height pixels.
     */
    public synchronized void present(int[] rgb, int width, int height) {
        if (rgb == null || width <= 0 || height <= 0 ||
                (long) width * height > rgb.length) {
            return;
        }
        if (frame == null || frameWidth != width || frameHeight != height ||
                frame.isRecycled()) {
            if (frame != null && !frame.isRecycled()) {
                frame.recycle();
            }
            frame = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            frameWidth = width;
            frameHeight = height;
        }
        frame.setPixels(rgb, 0, width, 0, 0, width, height);
        postInvalidateOnAnimation();
    }

    public synchronized void setSmoothScaling(boolean enabled) {
        smooth = enabled;
        imagePaint.setFilterBitmap(enabled);
        postInvalidateOnAnimation();
    }

    public synchronized int getFrameWidth() {
        return frameWidth;
    }

    public synchronized int getFrameHeight() {
        return frameHeight;
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (frame == null || frame.isRecycled()) {
            canvas.drawColor(Color.BLACK);
            return;
        }
        imagePaint.setFilterBitmap(smooth);
        destination.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(frame, null, destination, imagePaint);
    }

    @Override
    protected synchronized void onDetachedFromWindow() {
        if (frame != null && !frame.isRecycled()) {
            frame.recycle();
        }
        frame = null;
        frameWidth = 0;
        frameHeight = 0;
        super.onDetachedFromWindow();
    }
}
