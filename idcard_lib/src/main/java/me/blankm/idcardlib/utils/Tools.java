package me.blankm.idcardlib.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import androidx.exifinterface.media.ExifInterface;
import android.os.Build;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

/**
 * 相机与图片处理的工具类，提供屏幕尺寸查询、相机能力检测、图片裁剪压缩、权限检查等静态方法。
 */
public final class Tools {

    private static final String TAG = "Tools";

    /** 工具类，禁止实例化 */
    private Tools() {
        throw new AssertionError();
    }


    /** 获取屏幕显示度量 */
    private static DisplayMetrics getDisplayMetrics(Context mContext) {
        return mContext.getResources().getDisplayMetrics();
    }

    /** 获取屏幕宽度（像素） */
    public static int getScreenwidth(Context mContext) {
        return getDisplayMetrics(mContext).widthPixels;
    }

    /** 获取屏幕高度（像素） */
    public static int getScreenHeight(Context mContext) {
        return getDisplayMetrics(mContext).heightPixels;
    }

    /**
     * 检查设备是否有后置摄像头（CameraX）。
     * @return 有后置摄像头返回 true；查询异常或不支持时返回 false
     */
    public static boolean hasBackCamera(ProcessCameraProvider cameraProvider) {
        try {
            return cameraProvider == null ? false : cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
        } catch (CameraInfoUnavailableException e) {
            LogUtils.w(TAG, "查询后置摄像头失败: " + e);
        }
        return false;
    }

    /**
     * 检查设备是否有前置摄像头（CameraX）。
     * @return 有前置摄像头返回 true；查询异常或不支持时返回 false
     */
    public static boolean hasFrontCamera(ProcessCameraProvider cameraProvider) {
        try {
            return cameraProvider == null ? false : cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA);
        } catch (CameraInfoUnavailableException e) {
            LogUtils.w(TAG, "查询前置摄像头失败: " + e);
        }
        return false;
    }

    /**
     * 根据屏幕宽高比返回最接近的相机预览比例（{@link AspectRatio#RATIO_4_3} 或 {@link AspectRatio#RATIO_16_9}）。
     */
    public static int aspectRatio(Context mContext) {
        int width = getScreenwidth(mContext);
        int height = getScreenHeight(mContext);
        double previewRatio = Math.max(width, height) * 1.0 / Math.min(width, height);
        if (Math.abs(previewRatio - CameraConstant.RATIO_4_3_VALUE) <= Math.abs(previewRatio - CameraConstant.RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }

    /**
     * 检查外部存储是否挂载且可写。
     * @return SD 卡已挂载返回 true，否则返回 false
     */
    public static boolean checkSD() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    /**
     * 获取拍照输出路径，格式为 {@code <外部缓存目录>/Camera/IMG_yyyyMMdd_HHmmss.jpg}。
     * <p>目录不存在时会自动创建。
     * @return 拍照文件的绝对路径
     */
    public static String getPicturePath(Context context) {
//        String cameraPath = Environment.getExternalStorageDirectory().getPath() + File.separator + "DCIM" + File.separator + "Camera";

        String cameraPath = null;
        if (checkSD()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cameraPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + File.separator + "DCIM" + File.separator + "Camera";
            } else {
                cameraPath = Environment.getExternalStorageDirectory().getPath() + File.separator + "DCIM" + File.separator + "Camera";
            }
        } else {
            cameraPath = context.getFilesDir() + File.separator;
        }

        File cameraFolder = new File(cameraPath);
        if (!cameraFolder.exists()) {
            cameraFolder.mkdirs();
        }
        //必须指定 Locale.US，否则在阿拉伯语等区域会生成非 ASCII 数字的文件名
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        return cameraFolder.getAbsolutePath() + File.separator + "IMG_" + simpleDateFormat.format(new Date()) + ".jpg";
    }

    /**
     * 根据 EXIF 信息和前后置标志构建图片旋转矩阵。
     * <p>前置摄像头需要额外水平翻转。
     * @param imgPath 图片路径
     * @param front 是否为前置摄像头拍摄
     * @return 旋转/翻转矩阵
     */
    private static Matrix pictureDegree(String imgPath, boolean front) {
        Matrix matrix = new Matrix();
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(imgPath);
        } catch (IOException e) {
            LogUtils.w(TAG, "读取图片EXIF失败: " + e);
        }
        if (exif == null)
            return matrix;
        int degree = 0;
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                degree = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                degree = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                degree = 270;
                break;
            default:
                break;
        }
        matrix.postRotate(degree);
        if (front) {
            matrix.postScale(-1, 1);
        }
        return matrix;
    }

    /**
     * 按屏幕比例裁剪图片
     *
     * @return 裁剪后的位图，解码失败返回 null
     */
    /**
     * 解码并旋转矫正图片，返回 Bitmap（用于显示预览）。
     * <p>根据屏幕尺寸自动采样，避免 OOM。
     * @return 矫正后的 Bitmap，解码失败返回 null
     */
    public static Bitmap bitmapClip(Context mContext, String imgPath, boolean front) {
        Bitmap bitmap = BitmapFactory.decodeFile(imgPath);
        if (bitmap == null) {
            LogUtils.e(TAG, "图片解码失败: " + imgPath);
            return null;
        }
        LogUtils.d(TAG, "bitmap width:" + bitmap.getWidth() + " height:" + bitmap.getHeight());
        Matrix matrix = pictureDegree(imgPath, front);
        double bitmapRatio = bitmap.getHeight() * 1. / bitmap.getWidth();//基本上都是16/9
        int width = getScreenwidth(mContext);
        int height = getScreenHeight(mContext);
        double screenRatio = height * 1. / width;//屏幕的宽高比
        if (bitmapRatio > screenRatio) {//胖的手机
            int clipHeight = (int) (bitmap.getWidth() * screenRatio);
            bitmap = Bitmap.createBitmap(bitmap, 0, (bitmap.getHeight() - clipHeight) >> 1, bitmap.getWidth(), clipHeight, matrix, true);
        } else {//瘦长的手机
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        return bitmap;
    }

    /**
     * 裁剪指定区域并保存到目标路径（质量 90）。
     * <p>裁剪区域 {@code rect} 在显示坐标系中，内部会根据原图与显示尺寸的缩放比还原到原图坐标系。
     * @param rect 裁剪矩形（显示坐标系），为 null 时保存全图
     * @return 保存成功返回 true
     */
    public static boolean saveBitmap(Context mContext, String originPath, String savePath, Rect rect, boolean front) {
        Matrix matrix = pictureDegree(originPath, front);
        Bitmap clipBitmap = BitmapFactory.decodeFile(originPath);
        if (clipBitmap == null) {
            LogUtils.e(TAG, "图片解码失败: " + originPath);
            return false;
        }
        clipBitmap = Bitmap.createBitmap(clipBitmap, 0, 0, clipBitmap.getWidth(), clipBitmap.getHeight(), matrix, true);


        if (rect != null) {
            double bitmapRatio = clipBitmap.getHeight() * 1. / clipBitmap.getWidth();//基本上都是16/9
            int width = getScreenwidth(mContext);
            int height = getScreenHeight(mContext);
            double screenRatio = height * 1. / width;
            if (bitmapRatio > screenRatio) {//胖的手机
                LogUtils.d(TAG, "宽屏比例手机");
                int clipHeight = (int) (clipBitmap.getWidth() * screenRatio);
                clipBitmap = Bitmap.createBitmap(clipBitmap, 0, (clipBitmap.getHeight() - clipHeight) >> 1, clipBitmap.getWidth(), clipHeight, null, true);
                scalRect(rect, clipBitmap.getWidth() * 1. / getScreenwidth(mContext));
            } else {//瘦长的手机
                LogUtils.d(TAG, "窄长比例手机");
                int marginTop = (int) ((height - width * bitmapRatio) / 2);
                rect.top = rect.top - marginTop;
                scalRect(rect, clipBitmap.getWidth() * 1. / getScreenwidth(mContext));
            }
            clipBitmap = Bitmap.createBitmap(clipBitmap, rect.left, rect.top, rect.right, rect.bottom, null, true);
        }
        return saveBitmap(clipBitmap, savePath);
    }

    /**
     * 将 Bitmap 压缩为 JPEG 保存（质量 90）。
     * @return 保存成功返回 true
     */
    private static boolean saveBitmap(Bitmap bitmap, String savePath) {
        if (bitmap == null) return false;
        FileOutputStream fos = null;
        try {
            File file = new File(savePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            fos = new FileOutputStream(file);
            //身份证照片用 90 质量，平衡识别率和文件大小
            boolean b = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            return b;
        } catch (IOException e) {
            //原实现异常时直接返回，未关闭流，这里统一在 finally 中关闭
            LogUtils.e(TAG, "保存图片失败: " + savePath, e);
        } finally {
            FileUtils.closeIO(fos);
        }
        return false;
    }

    /**
     * 将矩形按缩放比例调整（就地修改）。
     */
    private static void scalRect(Rect rect, double scale) {
        rect.left = (int) (rect.left * scale);
        rect.top = (int) (rect.top * scale);
        rect.right = (int) (rect.right * scale);
        rect.bottom = (int) (rect.bottom * scale);
    }

    /**
     * 将 dp 转换为 px。
     */
    public static int dp2px(Context mContext, float dipValue) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, getDisplayMetrics(mContext));
    }


    /**
     * 通过反射设置 CameraX PreviewView 的宽高比。
     * <p>修改 {@code androidx.camera.view.PreviewView} 的私有字段 {@code mImplementationMode}。
     * @param view PreviewView 实例
     * @param ratio {@link AspectRatio#RATIO_4_3} 或 {@link AspectRatio#RATIO_16_9}
     */
    public static void reflectPreviewRatio(View view, @AspectRatio.Ratio int ratio) {
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) view.getLayoutParams();
        Class cls = layoutParams.getClass();
        try {
            Field dimensionRatioValue = cls.getDeclaredField("dimensionRatioValue");
            dimensionRatioValue.setAccessible(true);
            if (ratio == AspectRatio.RATIO_4_3) {
                dimensionRatioValue.set(layoutParams, (float) (4 * 1. / 3));
            } else {
                dimensionRatioValue.set(layoutParams, (float) (16 * 1. / 9));
            }

            Field dimensionRatioSide = cls.getDeclaredField("dimensionRatioSide");
            dimensionRatioSide.setAccessible(true);
            dimensionRatioSide.set(layoutParams, 1);

            Field dimensionRatio = cls.getDeclaredField("dimensionRatio");
            dimensionRatio.setAccessible(true);
            if (ratio == AspectRatio.RATIO_4_3) {
                dimensionRatio.set(layoutParams, "h,3:4");
            } else {
                dimensionRatio.set(layoutParams, "h,9:16");
            }
        } catch (Exception e) {
            LogUtils.w(TAG, "反射设置预览宽高比失败: " + e);
            layoutParams.width = Tools.getScreenwidth(view.getContext()) - 2 * Tools.dp2px(view.getContext(), layoutParams.leftMargin);
            if (ratio == AspectRatio.RATIO_4_3) {
                layoutParams.height = (int) (layoutParams.width * 4 / 3);
            } else {
                layoutParams.height = (int) (layoutParams.width * 16 / 9);
            }
        }
        view.setLayoutParams(layoutParams);
    }

    /**
     * 通过反射设置扫描框蒙层的宽高。
     * <p>修改 ConstraintLayout 子 View 的 {@code layout_width} 和 {@code layout_height} 字段。
     * @param view 蒙层 View
     * @param w 目标宽度（px）
     * @param h 目标高度（px）
     */
    public static void reflectMaskRatio(View view, int w, int h) {
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) view.getLayoutParams();
        Class cls = layoutParams.getClass();
        try {
            Field dimensionRatioValue = cls.getDeclaredField("dimensionRatioValue");
            dimensionRatioValue.setAccessible(true);
            dimensionRatioValue.set(layoutParams, (float) (h * 1. / w));

            Field dimensionRatioSide = cls.getDeclaredField("dimensionRatioSide");
            dimensionRatioSide.setAccessible(true);
            dimensionRatioSide.set(layoutParams, 1);

            Field dimensionRatio = cls.getDeclaredField("dimensionRatio");
            dimensionRatio.setAccessible(true);
            dimensionRatio.set(layoutParams, "h," + w + ":" + h);
        } catch (Exception e) {
            LogUtils.w(TAG, "反射设置遮罩宽高比失败: " + e);
            layoutParams.width = Tools.getScreenwidth(view.getContext()) - 2 * Tools.dp2px(view.getContext(), layoutParams.leftMargin);
            layoutParams.height = (int) (layoutParams.width * h / w);
        }
        view.setLayoutParams(layoutParams);
    }

    /**
     * 删除临时文件（拍照后用户取消时清理）。
     */
    public static void deletTempFile(String tempPath) {
        File file = new File(tempPath);
        file.delete();
    }

    /**
     * 获取 View 在屏幕中的绝对坐标 [x, y]。
     */
    public static int[] getViewLocal(View view) {
        int[] outLocation = new int[2];
        view.getLocationInWindow(outLocation);
        return outLocation;
    }

    /**
     * 检查相机和存储权限是否全部已授予。
     * @return 全部已授予返回 true
     */
    public static boolean checkPermission(Context context) {
        String[] permissions = cameraPermission();
        for (int i = 0; i < permissions.length; i++) {
            if (!isGranted(context, permissions[i]))
                return false;
        }
        return true;
    }

    /**
     * 返回相机功能所需的权限列表（相机 + 存储）。
     * <p>Android 11+ 不再需要 {@code WRITE_EXTERNAL_STORAGE}。
     */
    private static String[] cameraPermission() {
        return new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
        };
    }

    /**
     * 检查单个权限是否已授予。
     */
    private static boolean isGranted(Context context, String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return true;
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
}
