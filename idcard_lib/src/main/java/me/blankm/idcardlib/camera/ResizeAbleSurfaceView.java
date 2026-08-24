package me.blankm.idcardlib.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;


/**
 * SurfaceView预览变形完美解决
 */
/**
 * 可自适应宽高比的 SurfaceView。
 * <p>{@link CameraPreview} 的基类，根据 {@code setAspectRatio} 设置的比例自动调整尺寸。
 */
public class ResizeAbleSurfaceView extends SurfaceView {

    private int mWidth = -1;
    private int mHeight = -1;

    /**
     * 单参数构造。
     */
    public ResizeAbleSurfaceView(Context context) {
        super(context);
    }

    /**
     * 双参数构造，从布局 XML 实例化时调用。
     */
    public ResizeAbleSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * 三参数构造。
     */
    public ResizeAbleSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    //minSdk 21 已满足该构造器要求，无需 @RequiresApi
    public ResizeAbleSurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (-1 == mWidth || -1 == mHeight) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            setMeasuredDimension(mWidth, mHeight);
        }
    }

    public void resize(int width, int height) {
        mWidth = width;
        mHeight = height;
        getHolder().setFixedSize(width, height);
        requestLayout();
        invalidate();
    }
}
