package me.blankm.idcardlib.utils;

import android.graphics.Bitmap;

import java.util.HashSet;
import java.util.Set;

/**
 * Bitmap 复用池
 * <p>
 * 用于频繁解码场景（如相机预览帧、连续拍照），通过复用 Bitmap 内存减少 GC 压力。
 * 本库 minSdk 为 21，恒满足 Android 4.4+ 的宽松复用条件：只要候选 bitmap 的分配字节数
 * 不小于目标所需，即可复用，无需尺寸完全匹配。
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
     * <p>
     * minSdk 为 21，恒满足 Android 4.4+ 的宽松条件：只要目标字节数不超过候选的分配大小即可。
     */
    private static boolean canUseForInBitmap(Bitmap candidate, android.graphics.BitmapFactory.Options targetOptions, int width, int height) {
        int byteCount = width * height * getBytesPerPixel(targetOptions.inPreferredConfig);
        return candidate.getAllocationByteCount() >= byteCount;
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
