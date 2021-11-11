package me.blankm.idcardcamera;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

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
        /*


2021-11-11 21:35:37.470 18503-18503/me.blankm.idcardcamera I/System.out: w:1080
2021-11-11 21:35:37.470 18503-18503/me.blankm.idcardcamera I/System.out: h:2090
2021-11-11 21:35:37.470 18503-18503/me.blankm.idcardcamera I/System.out: bh:102
2021-11-11 21:35:37.470 18503-18503/me.blankm.idcardcamera I/System.out: nh:118

2021-11-11 21:36:48.445 21048-21048/me.blankm.idcardcamera I/System.out: w:1080
2021-11-11 21:36:48.445 21048-21048/me.blankm.idcardcamera I/System.out: h:2208
2021-11-11 21:36:48.445 21048-21048/me.blankm.idcardcamera I/System.out: bh:102
2021-11-11 21:36:48.445 21048-21048/me.blankm.idcardcamera I/System.out: nh:118

2021-11-11 21:37:51.590 16775-16775/me.blankm.idcardcamera I/System.out: w:720
2021-11-11 21:37:51.591 16775-16775/me.blankm.idcardcamera I/System.out: h:1436
2021-11-11 21:37:51.591 16775-16775/me.blankm.idcardcamera I/System.out: bh:56
2021-11-11 21:37:51.591 16775-16775/me.blankm.idcardcamera I/System.out: nh:84


         */


        System.out.println("w:" + ScreenUtils.getScreenWidth(this)
                + "\nh:" + ScreenUtils.getScreenHeight(this)
                + "\nbh:" + ScreenUtils.getStatusBarHeight(this)
                + "\nnh:" + ScreenUtils.getNavBarHeight(this));
    }

    public void shootingClick(View view) {
        IDCardCameraSelect.create(this).openCamera(IDCardCameraSelect.TYPE_IDCARD_FRONT);
    }

    public void backClick(View view) {
        IDCardCameraSelect.create(this).openCamera(IDCardCameraSelect.TYPE_IDCARD_BACK);
    }

    public void commonIDCardClick(View view) {
        IDCardCameraSelect.create(this).openCamera(IDCardCameraSelect.TYPE_IDCARD_All);
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
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        FileUtils.clearCache(getApplicationContext());
    }
}
