package me.blankm.idcardcamera;

import android.Manifest;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.permissionx.guolindev.PermissionX;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
        PermissionX.init(this)
                .permissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
                .request((boolean allGranted, List<String> grantedList, List<String> deniedList) -> {
                    if (allGranted) {
//                        IDCardCameraSelect.create(this).takePhoto(IDCardCameraSelect.TYPE_IDCARD_FRONT);
                        IDCardCameraSelect.create(this).openCamera(IDCardCameraSelect.TYPE_IDCARD_FRONT);
                    } else {
                        Toast.makeText(getApplicationContext(), "These permissions are denied: $deniedList", Toast.LENGTH_LONG).show();
                    }
                });
    }

    public void backClick(View view) {
        PermissionX.init(this)
                .permissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
                .request((boolean allGranted, List<String> grantedList, List<String> deniedList) -> {
                    if (allGranted) {
//                        IDCardCameraSelect.create(this).takePhoto(IDCardCameraSelect.TYPE_IDCARD_BACK);
                        IDCardCameraSelect.create(this).openCamera(IDCardCameraSelect.TYPE_IDCARD_BACK);
                    } else {
                        Toast.makeText(getApplicationContext(), "These permissions are denied: $deniedList", Toast.LENGTH_LONG).show();
                    }
                });

    }

    public void commonIDCardClick(View view) {
        PermissionX.init(this)
                .permissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
                .request((boolean allGranted, List<String> grantedList, List<String> deniedList) -> {
                    if (allGranted) {
//                        IDCardCameraSelect.create(this).takePhoto(IDCardCameraSelect.TYPE_IDCARD_All);
                        IDCardCameraSelect.create(this).openCamera(IDCardCameraSelect.TYPE_IDCARD_All);
                    } else {
                        Toast.makeText(getApplicationContext(), "These permissions are denied: $deniedList", Toast.LENGTH_LONG).show();
                    }
                });
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
}
