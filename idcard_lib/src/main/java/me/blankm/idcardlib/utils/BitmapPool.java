package me.blankm.idcardlib.utils;

import android.graphics.Bitmap;
import android.os.Build;

import java.util.HashSet;
import java.util.Set;

/**
 * Bitmap 复用池
 * <p>
 * 用于频繁解码场景（如相机预览帧、连续拍照），通过复用 Bitmap 内存减少 GC 压力。
 * Android 3.0+ 支持 inBitmap，要求复用的 bitmap 和新解码的尺寸/配置匹配。
 * Android 4.4+ 放宽限制，只要字节数足够即可复用。
 * <p>
 * 使用方式：
 * <pre>
 * BitmapFactory.Options options = new BitmapFactory.Options();
 * options.inMutable = true;
 * BitmapPool.get().addInBitmapOptions(options, width, height);
 * Bitmap decoded = BitmapFactory.decodeFile(path, options);
 * // 使用完后归还
 * BitmapPool.get().put(decoded);
 * </pre>
 */
public class BitmapPool {

    private static final int MAX_POOL_SIZE = 3; //最多缓存 3 个 bitmap（相机场景够用）
    private final Set<Bitmap> reusableBitmaps = new HashSet<>();

    private static class Holder {
        static final BitmapPool INSTANCE = new BitmapPool();
    }

    public static BitmapPool get() {
        return Holder.INSTANCE;
    }

    private BitmapPool() {
    }

    /**
     * 向 Options 添加可复用的 bitmap
     * <p>
     * 调用此方法后，解码时会尝试复用池中的 bitmap，减少内存分配。
     *
     * @param options Options 对象
     * @param width   目标宽度（用于匹配合适的复用 bitmap）
     * @param height  目标高度
     */
    public synchronized void addInBitmapOptions(android.graphics.BitmapFactory.Options options, int width, int height) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            //Android 3.0 以下不支持 inBitmap
            return;
        }

        options.inMutable = true;

        Bitmap inBitmap = getBitmapFromReusableSet(options, width, height);
        if (inBitmap != null) {
            options.inBitmap = inBitmap;
            //从池中取出后移除，解码成功后会生成新的 bitmap（复用了这块内存）
            reusableBitmaps.remove(inBitmap);
        }
    }

    /**
     * 归还不再使用的 bitmap 到池中
     *
     * @param bitmap 要归还的 bitmap（必须是 mutable）
     */
    public synchronized void put(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || !bitmap.isMutable()) {
            return;
        }

        if (reusableBitmaps.size() >= MAX_POOL_SIZE) {
            //池满时回收最老的一个
            Bitmap oldest = reusableBitmaps.iterator().next();
            reusableBitmaps.remove(oldest);
            if (!oldest.isRecycled()) {
                oldest.recycle();
            }
        }

        reusableBitmaps.add(bitmap);
    }

    /**
     * 清空池中所有 bitmap
     */
    public synchronized void clear() {
        for (Bitmap bitmap : reusableBitmaps) {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        reusableBitmaps.clear();
    }

    /**
     * 从池中找到合适的 bitmap 用于复用
     */
    private Bitmap getBitmapFromReusableSet(android.graphics.BitmapFactory.Options options, int width, int height) {
        if (reusableBitmaps.isEmpty()) {
            return null;
        }

        for (Bitmap candidate : reusableBitmaps) {
            if (candidate != null && !candidate.isRecycled()) {
                if (canUseForInBitmap(candidate, options, width, height)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * 判断候选 bitmap 是否可以用于 inBitmap 复用
     */
    private static boolean canUseForInBitmap(Bitmap candidate, android.graphics.BitmapFactory.Options targetOptions, int width, int height) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Android 4.4+ 只要字节数足够即可
            int byteCount = width * height * getBytesPerPixel(targetOptions.inPreferredConfig);
            return candidate.getAllocationByteCount() >= byteCount;
        }

        // Android 3.0-4.3 要求尺寸和配置完全匹配
        return candidate.getWidth() == width
                && candidate.getHeight() == height
                && targetOptions.inSampleSize == 1;
    }

    private static int getBytesPerPixel(Bitmap.Config config) {
        if (config == null) {
            config = Bitmap.Config.ARGB_8888;
        }
        switch (config) {
            case ALPHA_8:
                return 1;
            case RGB_565:
            case ARGB_4444:
                return 2;
            case ARGB_8888:
            default:
                return 4;
        }
    }
}
