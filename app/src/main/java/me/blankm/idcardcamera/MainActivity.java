package me.blankm.idcardcamera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Bundle;
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


/**
 * Author by Mr.Meng
 * created 2021/11/11
 *
 * @desc
 */
public class MainActivity extends AppCompatActivity {
    private ImageView mIv;
    private TextView mShowPathTv;

    private int type = IDCardCameraSelect.TYPE_IDCARD_FRONT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mIv = findViewById(R.id.iv_front);
        mShowPathTv = findViewById(R.id.show_path_tv);


        System.out.println("w:" + ScreenUtils.getScreenWidth(this)
                + "\nh:" + ScreenUtils.getScreenHeight(this)
                + "\nbh:" + ScreenUtils.getStatusBarHeight(this)
                + "\nnh:" + ScreenUtils.getNavBarHeight(this));
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
        if (resultCode == IDCardCameraSelect.RESULT_CODE) {
            List<String> path = IDCardCameraSelect.getImagePath(data);
            if (path != null && path.size() > 0) {
                if (path.size() > 1) {
                    mShowPathTv.setText(("1、" + path.get(0) + "\n" + "2、" + path.get(1)));
                } else {
                    mShowPathTv.setText(("1、" + path.get(0)));
                }
                mIv.setImageBitmap(BitmapFactory.decodeFile(path.get(0)));
                Log.e("wld_____", "======== size:" + path.size());
            } else {
                Log.e("wld_____", "path" + (path == null ? "null" : "notNull") + "======== size" + (path == null ? "0" : path.size()));
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
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
