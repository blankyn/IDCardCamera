package me.blankm.idcardcamera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import me.blankm.idcardlib.camera.IDCardCameraSelect;
import me.blankm.idcardlib.utils.FileUtils;
import me.blankm.idcardlib.utils.ScreenUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Author by Mr.Meng
 * created 2021/11/11
 *
 * @desc
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ImageView mIv;
    private TextView mShowPathTv;

    private int type = IDCardCameraSelect.TYPE_IDCARD_FRONT;

    //后台解码图片，避免阻塞主线程
    private final ExecutorService mIoExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mIv = findViewById(R.id.iv_front);
        mShowPathTv = findViewById(R.id.show_path_tv);


        if (BuildConfig.DEBUG) {
            Log.d(TAG, "screen w:" + ScreenUtils.getScreenWidth(this)
                    + " h:" + ScreenUtils.getScreenHeight(this)
                    + " statusBar:" + ScreenUtils.getStatusBarHeight(this)
                    + " navBar:" + ScreenUtils.getNavBarHeight(this));
        }
    }

    public void shootingClick(View view) {
        type = IDCardCameraSelect.TYPE_IDCARD_FRONT;
        // 1. 检查权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 2. 请求权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    100);
        } else {
            // 已授权，执行相关操作
            IDCardCameraSelect.create(this).openCamera(IDCardCameraSelect.TYPE_IDCARD_FRONT);
        }
    }

    public void backClick(View view) {
        type = IDCardCameraSelect.TYPE_IDCARD_BACK;
        // 1. 检查权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
        ) {
            // 2. 请求权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    100);
        } else {
            // 已授权，执行相关操作
            IDCardCameraSelect.create(this).openCamera(IDCardCameraSelect.TYPE_IDCARD_BACK);
        }
    }

    public void commonIDCardClick(View view) {
        type = IDCardCameraSelect.TYPE_IDCARD_All;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
        ) {
            // 2. 请求权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    100);
        } else {
            // 已授权，执行相关操作
            IDCardCameraSelect.create(this).openCamera(IDCardCameraSelect.TYPE_IDCARD_All);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != IDCardCameraSelect.RESULT_CODE) return;
        List<String> path = IDCardCameraSelect.getImagePath(data);
        if (path == null || path.isEmpty()) {
            Log.w(TAG, "未取到图片路径");
            return;
        }
        if (path.size() > 1) {
            mShowPathTv.setText(("1、" + path.get(0) + "\n" + "2、" + path.get(1)));
        } else {
            mShowPathTv.setText(("1、" + path.get(0)));
        }
        //解码放到后台线程，避免大图在主线程解码造成卡顿/OOM
        final String imagePath = path.get(0);
        mIoExecutor.execute(() -> {
            final Bitmap bitmap = decodeSampled(imagePath, mIv.getWidth(), mIv.getHeight());
            mMainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (bitmap != null) {
                    mIv.setImageBitmap(bitmap);
                }
            });
        });
    }

    /**
     * 按控件尺寸采样解码，避免整图加载
     */
    private static Bitmap decodeSampled(String filePath, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;

        int sampleSize = 1;
        if (reqWidth > 0 && reqHeight > 0) {
            while (options.outHeight / sampleSize > reqHeight
                    || options.outWidth / sampleSize > reqWidth) {
                sampleSize *= 2;
            }
        }
        options.inSampleSize = sampleSize;
        options.inJustDecodeBounds = false;
        try {
            return BitmapFactory.decodeFile(filePath, options);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "解码图片内存不足: " + filePath);
            return null;
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        //移除未执行的回调并停止后台线程，避免页面销毁后仍持有 Activity 引用
        mMainHandler.removeCallbacksAndMessages(null);
        mIoExecutor.shutdownNow();
        FileUtils.clearCache(getApplicationContext());
    }

    // 3. 处理回调
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予
                switch (type) {
                    case IDCardCameraSelect.TYPE_IDCARD_FRONT:
                        IDCardCameraSelect.create(this).openCamera(IDCardCameraSelect.TYPE_IDCARD_FRONT);
                        break;
                    case IDCardCameraSelect.TYPE_IDCARD_BACK:
                        IDCardCameraSelect.create(this).openCamera(IDCardCameraSelect.TYPE_IDCARD_BACK);
                        break;
                    case IDCardCameraSelect.TYPE_IDCARD_All:
                        IDCardCameraSelect.create(this).openCamera(IDCardCameraSelect.TYPE_IDCARD_All);
                        break;
                }
            } else {
                // 权限被拒绝
            }
        }
    }
}
