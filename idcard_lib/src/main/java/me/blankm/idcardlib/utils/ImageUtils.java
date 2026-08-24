package me.blankm.idcardlib.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;


/**
 * Copyright (C), 1999-2023
 * <p>
 *
 * @author qfmeng6@163.com
 * @date 2025/11/8 22:10
 * <p>
 * @description: 图片相关工具类
 * <p>
 * @version:
 * @revise:
 */
public class ImageUtils {

    /**
     * 按目标尺寸采样解码图片，避免大图整图解码导致 OOM
     *
     * @param filePath  图片路径
     * @param reqWidth  目标宽度
     * @param reqHeight 目标高度
     * @return 解码后的位图，失败返回 null
     */
    public static Bitmap decodeSampledBitmap(String filePath, int reqWidth, int reqHeight) {
        return decodeSampledBitmap(filePath, reqWidth, reqHeight, false);
    }

    /**
     * 按目标尺寸采样解码图片，支持 Bitmap 复用
     *
     * @param filePath  图片路径
     * @param reqWidth  目标宽度
     * @param reqHeight 目标高度
     * @param reuse     是否启用 Bitmap 复用池（连拍/预览场景推荐开启）
     * @return 解码后的位图，失败返回 null
     */
    public static Bitmap decodeSampledBitmap(String filePath, int reqWidth, int reqHeight, boolean reuse) {
        if (filePath == null || filePath.length() == 0) {
            return null;
        }
        //先只读边界，不真正分配内存
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;

        //启用复用时从池中取可复用的 bitmap
        if (reuse) {
            int finalWidth = options.outWidth / options.inSampleSize;
            int finalHeight = options.outHeight / options.inSampleSize;
            BitmapPool.get().addInBitmapOptions(options, finalWidth, finalHeight);
        }

        try {
            return BitmapFactory.decodeFile(filePath, options);
        } catch (OutOfMemoryError e) {
            LogUtils.e("ImageUtils", "解码图片内存不足: " + filePath);
            return null;
        }
    }

    /**
     * 计算采样率，取不小于目标尺寸的最大 2 的幂
     */
    /**
     * 计算图片采样率（{@code inSampleSize}），使解码后的尺寸不超过目标尺寸。
     * @return 2 的幂次（1, 2, 4, 8...），值越大图片越小
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (reqWidth <= 0 || reqHeight <= 0) {
            return inSampleSize;
        }
        while (height / inSampleSize > reqHeight || width / inSampleSize > reqWidth) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    /**
     * 保存图片
     *
     * @param src      源图片
     * @param filePath 要保存到的文件路径
     * @param format   格式
     * @return {@code true}: 成功<br>{@code false}: 失败
     */
    public static boolean save(Bitmap src, String filePath, CompressFormat format) {
        return save(src, FileUtils.getFileByPath(filePath), format, false);
    }

    /**
     * 保存图片
     *
     * @param src    源图片
     * @param file   要保存到的文件
     * @param format 格式
     * @return {@code true}: 成功<br>{@code false}: 失败
     */
    public static boolean save(Bitmap src, File file, CompressFormat format) {
        return save(src, file, format, false, 90);
    }

    /**
     * 保存图片
     *
     * @param src      源图片
     * @param filePath 要保存到的文件路径
     * @param format   格式
     * @param recycle  是否回收
     * @return {@code true}: 成功<br>{@code false}: 失败
     */
    public static boolean save(Bitmap src, String filePath, CompressFormat format, boolean recycle) {
        return save(src, FileUtils.getFileByPath(filePath), format, recycle, 90);
    }

    /**
     * 保存图片
     *
     * @param src     源图片
     * @param file    要保存到的文件
     * @param format  格式
     * @param recycle 是否回收
     * @return {@code true}: 成功<br>{@code false}: 失败
     */
    public static boolean save(Bitmap src, File file, CompressFormat format, boolean recycle) {
        return save(src, file, format, recycle, 90);
    }

    /**
     * 保存图片（带质量控制）
     *
     * @param src     源图片
     * @param file    要保存到的文件
     * @param format  格式
     * @param recycle 是否回收
     * @param quality 压缩质量 0-100，身份证推荐 85-90
     * @return {@code true}: 成功<br>{@code false}: 失败
     */
    public static boolean save(Bitmap src, File file, CompressFormat format, boolean recycle, int quality) {
        if (isEmptyBitmap(src) || !FileUtils.createOrExistsFile(file)) {
            return false;
        }
        LogUtils.d("ImageUtils", "save " + src.getWidth() + "x" + src.getHeight() + " quality=" + quality);
        OutputStream os = null;
        boolean ret = false;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file));
            ret = src.compress(format, quality, os);
            if (recycle && !src.isRecycled()) {
                src.recycle();
            }
        } catch (IOException e) {
            LogUtils.e("ImageUtils", "保存图片失败: " + e);
        } finally {
            FileUtils.closeIO(os);
        }
        return ret;
    }

    /**
     * 旋转位图
     *
     * @param bitmap 源图
     * @return 旋转后的
     */
    public static Bitmap roteBitmap(Bitmap bitmap) {
        Matrix mx = new Matrix();
        mx.postRotate(90);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth(), bitmap.getHeight(), true);
        Bitmap rotatedBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), mx, true);//旋转后的位图
        return rotatedBitmap;
    }

    /**
     * 判断bitmap对象是否为空
     *
     * @param src 源图片
     * @return {@code true}: 是<br>{@code false}: 否
     */
    private static boolean isEmptyBitmap(Bitmap src) {
        return src == null || src.getWidth() == 0 || src.getHeight() == 0;
    }

    /**
     * 将byte[]转换成Bitmap
     *
     * @param bytes
     * @param width
     * @param height
     * @return
     */
    public static Bitmap getBitmapFromByte(byte[] bytes, int width, int height) {
        final YuvImage image = new YuvImage(bytes, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream(bytes.length);
        if (!image.compressToJpeg(new Rect(0, 0, width, height), 100, os)) {
            return null;
        }
        byte[] tmp = os.toByteArray();
        Bitmap bmp = BitmapFactory.decodeByteArray(tmp, 0, tmp.length);
        return bmp;
    }


    //最重要的就是加上这两个  主要作用 把获取到的图片地址转为url格式然后再转bitmap格式
    // 通过uri加载图片
    public static Bitmap getBitmapFromUri(Context context, Uri uri) {
        try {
            ParcelFileDescriptor parcelFileDescriptor =
                    context.getContentResolver().openFileDescriptor(uri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
            parcelFileDescriptor.close();
            return image;
        } catch (Exception e) {
            LogUtils.e("ImageUtils", "通过Uri加载图片失败: " + e);
        }
        return null;
    }

    //将图片地址转换成Uri
    public static Uri getImageContentUri(Context context, String path) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media._ID}, MediaStore.Images.Media.DATA + "=? ",
                    new String[]{path}, null);
            if (cursor != null && cursor.moveToFirst()) {
                //列不存在时 getColumnIndex 返回 -1，取值会抛越界异常
                int idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID);
                if (idIndex >= 0) {
                    int id = cursor.getInt(idIndex);
                    Uri baseUri = Uri.parse("content://media/external/images/media");
                    return Uri.withAppendedPath(baseUri, "" + id);
                }
            }
            // 如果图片不在手机的共享图片数据库，就先把它插入。
            if (new File(path).exists()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DATA, path);
                return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            }
            return null;
        } finally {
            //Cursor 必须关闭，原实现所有 return 路径都会泄漏
            if (cursor != null) {
                cursor.close();
            }
        }
    }


}
