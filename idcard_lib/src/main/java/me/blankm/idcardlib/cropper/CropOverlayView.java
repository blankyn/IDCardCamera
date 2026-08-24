package me.blankm.idcardlib.cropper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.blankm.idcardlib.utils.LogUtils;

/**
 * 可拖拽四点裁剪框视图，支持透视变换矫正。
 * <p>用户通过拖动四个角点调整裁剪区域，确认后在后台线程执行透视变换和裁剪运算。
 * 主要用于 {@link me.blankm.idcardlib.camera.CameraActivity} 的拍照路径手动裁剪。
 *
 * <p>裁剪运算使用全局单线程池 {@code CROP_EXECUTOR}，避免多实例并发时的内存峰值。
 */
public class CropOverlayView extends View {

    private static final String TAG = "CropOverlayView";

    //裁剪是一次性的短任务，全局共享单线程即可，避免每个 View 各自持有线程池
    private static final ExecutorService CROP_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private int defaultMargin = 100;
    private int minDistance = 100;
    private int vertexSize = 30;
    private int gridSize = 3;

    private Bitmap bitmap;
    private Point topLeft, topRight, bottomLeft, bottomRight;

    private float touchDownX, touchDownY;
    private CropPosition cropPosition;

    private int currentWidth = 0;
    private int currentHeight = 0;

    private int minX, maxX, minY, maxY;

    /**
     * 单参数构造。
     */
    public CropOverlayView(Context context) {
        super(context);
    }

    /**
     * 双参数构造，从布局 XML 实例化时调用。
     */
    public CropOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * 设置待裁剪的原始图片，并重置裁剪框到默认位置。
     *
     * @param bitmap 原始图片，为 {@code null} 时清空裁剪状态
     */
    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        resetPoints();
        invalidate();
    }

    /**
     * 绘制裁剪框和半透明蒙层。
     * <p>绘制顺序：蒙层（裁剪区域外变暗）→ 四条边框线 → 四个角点控制柄。
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getWidth() != currentWidth || getHeight() != currentHeight) {
            currentWidth = getWidth();
            currentHeight = getHeight();
            resetPoints();
        }

        drawBackground(canvas);
        //drawVertex(canvas);
        drawEdge(canvas);
        //drawGrid(canvas);//裁剪框内部宫格线条
    }

    /**
     * 根据当前 View 尺寸和图片尺寸计算裁剪框的默认位置（四个角点）。
     * <p>裁剪框初始化为图片在 View 中的可见区域，减去 {@code defaultMargin} 边距。
     */
    private void resetPoints() {
        LogUtils.d(TAG, "resetPoints, bitmap=" + bitmap);
        // 1. calculate bitmap size in new canvas
        float scaleX = bitmap.getWidth() * 1.0f / getWidth();
        float scaleY = bitmap.getHeight() * 1.0f / getHeight();
        float maxScale = Math.max(scaleX, scaleY);
        // 2. determine minX , maxX if maxScale = scaleY | minY, maxY if maxScale = scaleX
        int minX = 0;
        int maxX = getWidth();
        int minY = 0;
        int maxY = getHeight();

        if (maxScale == scaleY) { // image very tall
            int bitmapInCanvasWidth = (int) (bitmap.getWidth() / maxScale);
            minX = (getWidth() - bitmapInCanvasWidth) / 2;
            maxX = getWidth() - minX;
        } else { // image very wide
            int bitmapInCanvasHeight = (int) (bitmap.getHeight() / maxScale);
            minY = (getHeight() - bitmapInCanvasHeight) / 2;
            maxY = getHeight() - minY;
        }

        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;

//        if (maxX - minX < defaultMargin || maxY - minY < defaultMargin)
//            defaultMargin = 0; // remove min
//        else
//            defaultMargin = 30;
        //默认距边框的Margin
        defaultMargin = 0;


        LogUtils.d(TAG, "cropArea=" + (maxX - minX) + "x" + (maxY - minY));

        topLeft = new Point(minX + defaultMargin, minY + defaultMargin);
        topRight = new Point(maxX - defaultMargin, minY + defaultMargin);
        bottomLeft = new Point(minX + defaultMargin, maxY - defaultMargin);
        bottomRight = new Point(maxX - defaultMargin, maxY - defaultMargin);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * 绘制裁剪区域外的半透明黑色蒙层（透明度 40%）。
     * <p>使用 {@link PorterDuffXfermode#CLEAR} 将裁剪框内区域擦除为透明。
     */
    private void drawBackground(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#66000000"));
        paint.setStyle(Paint.Style.FILL);

        Path path = new Path();
        path.moveTo(topLeft.x, topLeft.y);
        path.lineTo(topRight.x, topRight.y);
        path.lineTo(bottomRight.x, bottomRight.y);
        path.lineTo(bottomLeft.x, bottomLeft.y);
        path.close();

        canvas.save();
        canvas.clipPath(path, Region.Op.DIFFERENCE);
        canvas.drawColor(Color.parseColor("#66000000"));
        canvas.restore();
    }

    /**
     * 绘制四个角点控制柄（白色圆点，半径 20px）。已被 {@link #onDraw} 注释掉，保留备用。
     */
    private void drawVertex(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(topLeft.x, topLeft.y, vertexSize, paint);
        canvas.drawCircle(topRight.x, topRight.y, vertexSize, paint);
        canvas.drawCircle(bottomLeft.x, bottomLeft.y, vertexSize, paint);
        canvas.drawCircle(bottomRight.x, bottomRight.y, vertexSize, paint);
    }

    /**
     * 绘制裁剪框的四条边线（白色实线，宽度 5px）。
     */
    private void drawEdge(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(3);
        paint.setAntiAlias(true);

        canvas.drawLine(topLeft.x, topLeft.y, topRight.x, topRight.y, paint);
        canvas.drawLine(topLeft.x, topLeft.y, bottomLeft.x, bottomLeft.y, paint);
        canvas.drawLine(bottomRight.x, bottomRight.y, topRight.x, topRight.y, paint);
        canvas.drawLine(bottomRight.x, bottomRight.y, bottomLeft.x, bottomLeft.y, paint);
    }

    /**
     * 绘制裁剪框内的九宫格辅助线（白色虚线，宽度 2px）。已被 {@link #onDraw} 注释掉，保留备用。
     */
    private void drawGrid(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2);
        paint.setAntiAlias(true);

        for (int i = 1; i <= gridSize; i++) {
            int topDistanceX = Math.abs(topLeft.x - topRight.x) / (gridSize + 1) * i;
            int topDistanceY = Math.abs((topLeft.y - topRight.y) / (gridSize + 1) * i);

            Point top = new Point(
                    topLeft.x < topRight.x ? topLeft.x + topDistanceX : topLeft.x - topDistanceX,
                    topLeft.y < topRight.y ? topLeft.y + topDistanceY : topLeft.y - topDistanceY);

            int bottomDistanceX = Math.abs((bottomLeft.x - bottomRight.x) / (gridSize + 1) * i);
            int bottomDistanceY = Math.abs((bottomLeft.y - bottomRight.y) / (gridSize + 1) * i);
            Point bottom = new Point(
                    bottomLeft.x < bottomRight.x ? bottomLeft.x + bottomDistanceX : bottomLeft.x - bottomDistanceX,
                    bottomLeft.y < bottomRight.y ? bottomLeft.y + bottomDistanceY : bottomLeft.y - bottomDistanceY);

            canvas.drawLine(top.x, top.y, bottom.x, bottom.y, paint);

            int leftDistanceX = Math.abs((topLeft.x - bottomLeft.x) / (gridSize + 1) * i);
            int leftDistanceY = Math.abs((topLeft.y - bottomLeft.y) / (gridSize + 1) * i);

            Point left = new Point(
                    topLeft.x < bottomLeft.x ? topLeft.x + leftDistanceX : topLeft.x - leftDistanceX,
                    topLeft.y < bottomLeft.y ? topLeft.y + leftDistanceY : topLeft.y - leftDistanceY);

            int rightDistanceX = Math.abs((topRight.x - bottomRight.x) / (gridSize + 1) * i);
            int rightDistanceY = Math.abs((topRight.y - bottomRight.y) / (gridSize + 1) * i);

            Point right = new Point(
                    topRight.x < bottomRight.x ? topRight.x + rightDistanceX : topRight.x - rightDistanceX,
                    topRight.y < bottomRight.y ? topRight.y + rightDistanceY : topRight.y - rightDistanceY);

            canvas.drawLine(left.x, left.y, right.x, right.y, paint);
        }

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                //无障碍服务要求：重写了 onTouchEvent 必须在适当时机调用 performClick
                performClick();
                break;
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(false);
                onActionDown(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                onActionMove(event);
                return true;
        }
        return false;
    }

    @Override
    public boolean performClick() {
        //无障碍框架通过此方法触发点击，即使这里没有实际点击逻辑也必须实现
        super.performClick();
        return true;
    }

    /**
     * 处理 {@link MotionEvent#ACTION_DOWN} 事件，记录触摸起点并判断是否命中角点。
     */
    private void onActionDown(MotionEvent event) {
        touchDownX = event.getX();
        touchDownY = event.getY();
        Point touchPoint = new Point((int) event.getX(), (int) event.getY());
        int minDistance = distance(touchPoint, topLeft);
        cropPosition = CropPosition.TOP_LEFT;
        if (minDistance > distance(touchPoint, topRight)) {
            minDistance = distance(touchPoint, topRight);
            cropPosition = CropPosition.TOP_RIGHT;
        }
        if (minDistance > distance(touchPoint, bottomLeft)) {
            minDistance = distance(touchPoint, bottomLeft);
            cropPosition = CropPosition.BOTTOM_LEFT;
        }
        if (minDistance > distance(touchPoint, bottomRight)) {
            minDistance = distance(touchPoint, bottomRight);
            cropPosition = CropPosition.BOTTOM_RIGHT;
        }
    }

    /**
     * 计算两点间的欧几里得距离。
     *
     * @return 距离的整数部分
     */
    private int distance(Point src, Point dst) {
        return (int) Math.sqrt(Math.pow(src.x - dst.x, 2) + Math.pow(src.y - dst.y, 2));
    }

    private void onActionMove(MotionEvent event) {
        int deltaX = (int) (event.getX() - touchDownX);
        int deltaY = (int) (event.getY() - touchDownY);

        switch (cropPosition) {
            case TOP_LEFT:
                adjustTopLeft(deltaX, deltaY);
                invalidate();
                break;
            case TOP_RIGHT:
                adjustTopRight(deltaX, deltaY);
                invalidate();
                break;
            case BOTTOM_LEFT:
                adjustBottomLeft(deltaX, deltaY);
                invalidate();
                break;
            case BOTTOM_RIGHT:
                adjustBottomRight(deltaX, deltaY);
                invalidate();
                break;
        }
        touchDownX = event.getX();
        touchDownY = event.getY();
    }

    /**
     * 调整左上角点坐标，限制在有效范围内。
     */
    private void adjustTopLeft(int deltaX, int deltaY) {
        int newX = topLeft.x + deltaX;
        if (newX < minX) newX = minX;
        if (newX > maxX) newX = maxX;

        int newY = topLeft.y + deltaY;
        if (newY < minY) newY = minY;
        if (newY > maxY) newY = maxY;

        topLeft.set(newX, newY);
    }

    /**
     * 调整右上角点坐标，限制在有效范围内。
     */
    private void adjustTopRight(int deltaX, int deltaY) {
        int newX = topRight.x + deltaX;
        if (newX > maxX) newX = maxX;
        if (newX < minX) newX = minX;

        int newY = topRight.y + deltaY;
        if (newY < minY) newY = minY;
        if (newY > maxY) newY = maxY;

        topRight.set(newX, newY);
    }

    /**
     * 调整左下角点坐标，限制在有效范围内。
     */
    private void adjustBottomLeft(int deltaX, int deltaY) {
        int newX = bottomLeft.x + deltaX;
        if (newX < minX) newX = minX;
        if (newX > maxX) newX = maxX;

        int newY = bottomLeft.y + deltaY;
        if (newY > maxY) newY = maxY;
        if (newY < minY) newY = minY;

        bottomLeft.set(newX, newY);
    }

    /**
     * 调整右下角点坐标，限制在有效范围内。
     */
    private void adjustBottomRight(int deltaX, int deltaY) {
        int newX = bottomRight.x + deltaX;
        if (newX > maxX) newX = maxX;
        if (newX < minX) newX = minX;

        int newY = bottomRight.y + deltaY;
        if (newY > maxY) newY = maxY;
        if (newY < minY) newY = minY;

        bottomRight.set(newX, newY);
    }

    /**
     * 裁剪
     * <p>
     * 顶点坐标读取和整图 Bitmap 运算分开：坐标必须在主线程读，位图运算放到后台线程，
     * 回调统一切回主线程。
     */
    public void crop(CropListener cropListener, boolean needStretch) {
        if (topLeft == null || bitmap == null) {
            if (cropListener != null) cropListener.onFinish(null);
            return;
        }

        // calculate bitmap size in new canvas
        float scaleX = bitmap.getWidth() * 1.0f / getWidth();
        float scaleY = bitmap.getHeight() * 1.0f / getHeight();
        final float maxScale = Math.max(scaleX, scaleY);

        // re-calculate coordinate in original bitmap，主线程读取顶点，避免与触摸事件竞态
        final Point bitmapTopLeft = new Point((int) ((topLeft.x - minX) * maxScale), (int) ((topLeft.y - minY) * maxScale));
        final Point bitmapTopRight = new Point((int) ((topRight.x - minX) * maxScale), (int) ((topRight.y - minY) * maxScale));
        final Point bitmapBottomLeft = new Point((int) ((bottomLeft.x - minX) * maxScale), (int) ((bottomLeft.y - minY) * maxScale));
        final Point bitmapBottomRight = new Point((int) ((bottomRight.x - minX) * maxScale), (int) ((bottomRight.y - minY) * maxScale));

        final Bitmap source = bitmap;
        CROP_EXECUTOR.execute(() -> {
            Bitmap result = null;
            try {
                result = doCrop(source, bitmapTopLeft, bitmapTopRight,
                        bitmapBottomLeft, bitmapBottomRight, needStretch);
            } catch (Exception | OutOfMemoryError e) {
                LogUtils.e(TAG, "裁剪失败: " + e);
            }
            final Bitmap finalResult = result;
            MAIN_HANDLER.post(() -> cropListener.onFinish(finalResult));
        });
    }

    /**
     * 实际的位图裁剪运算，运行在后台线程
     */
    private Bitmap doCrop(Bitmap bitmap, Point bitmapTopLeft, Point bitmapTopRight,
                          Point bitmapBottomLeft, Point bitmapBottomRight, boolean needStretch) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth() + 1, bitmap.getHeight() + 1, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint();
        // 1. draw path
        Path path = new Path();
        path.moveTo(bitmapTopLeft.x, bitmapTopLeft.y);
        path.lineTo(bitmapTopRight.x, bitmapTopRight.y);
        path.lineTo(bitmapBottomRight.x, bitmapBottomRight.y);
        path.lineTo(bitmapBottomLeft.x, bitmapBottomLeft.y);
        path.close();
        canvas.drawPath(path, paint);

        // 2. draw original bitmap
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, 0, 0, paint);

        // 3. cut
        Rect cropRect = new Rect(
                Math.min(bitmapTopLeft.x, bitmapBottomLeft.x),
                Math.min(bitmapTopLeft.y, bitmapTopRight.y),
                Math.max(bitmapBottomRight.x, bitmapTopRight.x),
                Math.max(bitmapBottomRight.y, bitmapBottomLeft.y));

        if (cropRect.width() <= 0 || cropRect.height() <= 0) { //用户裁剪的宽或高为0
            return null;
        }
        Bitmap cut = Bitmap.createBitmap(
                output,
                cropRect.left,
                cropRect.top,
                cropRect.width(),
                cropRect.height()
        );

        if (!needStretch) {
            return cut;
        } else {
            // 4. re-calculate coordinate in cropRect
            Point cutTopLeft = new Point();
            Point cutTopRight = new Point();
            Point cutBottomLeft = new Point();
            Point cutBottomRight = new Point();

            cutTopLeft.x = bitmapTopLeft.x > bitmapBottomLeft.x ? bitmapTopLeft.x - bitmapBottomLeft.x : 0;
            cutTopLeft.y = bitmapTopLeft.y > bitmapTopRight.y ? bitmapTopLeft.y - bitmapTopRight.y : 0;

            cutTopRight.x = bitmapTopRight.x > bitmapBottomRight.x ? cropRect.width() : cropRect.width() - Math.abs(bitmapBottomRight.x - bitmapTopRight.x);
            cutTopRight.y = bitmapTopLeft.y > bitmapTopRight.y ? 0 : Math.abs(bitmapTopLeft.y - bitmapTopRight.y);

            cutBottomLeft.x = bitmapTopLeft.x > bitmapBottomLeft.x ? 0 : Math.abs(bitmapTopLeft.x - bitmapBottomLeft.x);
            cutBottomLeft.y = bitmapBottomLeft.y > bitmapBottomRight.y ? cropRect.height() : cropRect.height() - Math.abs(bitmapBottomRight.y - bitmapBottomLeft.y);

            cutBottomRight.x = bitmapTopRight.x > bitmapBottomRight.x ? cropRect.width() - Math.abs(bitmapBottomRight.x - bitmapTopRight.x) : cropRect.width();
            cutBottomRight.y = bitmapBottomLeft.y > bitmapBottomRight.y ? cropRect.height() - Math.abs(bitmapBottomRight.y - bitmapBottomLeft.y) : cropRect.height();

            float width = cut.getWidth();
            float height = cut.getHeight();

            float[] src = new float[]{cutTopLeft.x, cutTopLeft.y, cutTopRight.x, cutTopRight.y, cutBottomRight.x, cutBottomRight.y, cutBottomLeft.x, cutBottomLeft.y};
            float[] dst = new float[]{0, 0, width, 0, width, height, 0, height};

            Matrix matrix = new Matrix();
            matrix.setPolyToPoly(src, 0, dst, 0, 4);
            Bitmap stretch = Bitmap.createBitmap(cut.getWidth(), cut.getHeight(), Bitmap.Config.ARGB_8888);

            Canvas stretchCanvas = new Canvas(stretch);
//            stretchCanvas.drawBitmap(cut, matrix, null);
            stretchCanvas.concat(matrix);
            stretchCanvas.drawBitmapMesh(cut, WIDTH_BLOCK, HEIGHT_BLOCK, generateVertices(cut.getWidth(), cut.getHeight()), 0, null, 0, null);

            return stretch;
        }
    }

    private int WIDTH_BLOCK = 40;
    private int HEIGHT_BLOCK = 40;

    /**
     * 为 {@link Canvas#drawBitmapMesh} 生成网格顶点数组。
     * <p>将图片均匀分割为 {@code WIDTH_BLOCK × HEIGHT_BLOCK} 个小块，
     * 返回的顶点数组供透视变换时使用，可减少变形边缘锯齿。
     *
     * @return 长度为 {@code (WIDTH_BLOCK+1) × (HEIGHT_BLOCK+1) × 2} 的顶点坐标数组
     */
    private float[] generateVertices(int widthBitmap, int heightBitmap) {

        float[] vertices = new float[(WIDTH_BLOCK + 1) * (HEIGHT_BLOCK + 1) * 2];

        float widthBlock = (float) widthBitmap / WIDTH_BLOCK;
        float heightBlock = (float) heightBitmap / HEIGHT_BLOCK;

        for (int i = 0; i <= HEIGHT_BLOCK; i++)
            for (int j = 0; j <= WIDTH_BLOCK; j++) {
                vertices[i * ((HEIGHT_BLOCK + 1) * 2) + (j * 2)] = j * widthBlock;
                vertices[i * ((HEIGHT_BLOCK + 1) * 2) + (j * 2) + 1] = i * heightBlock;
            }
        return vertices;
    }


}
