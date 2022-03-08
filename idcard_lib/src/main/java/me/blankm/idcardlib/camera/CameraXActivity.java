package me.blankm.idcardlib.camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import me.blankm.idcardlib.R;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;


import me.blankm.idcardlib.dialog.IDCardDialog;
import me.blankm.idcardlib.utils.ImageUtils;
import me.blankm.idcardlib.utils.PermissionChecker;
import me.blankm.idcardlib.utils.PermissionUtils;
import me.blankm.idcardlib.utils.ScreenUtils;
import me.blankm.idcardlib.utils.Tools;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static me.blankm.idcardlib.utils.CameraConstant.RATIO_16_9_VALUE;
import static me.blankm.idcardlib.utils.CameraConstant.RATIO_4_3_VALUE;

public class CameraXActivity extends AppCompatActivity {

    private final String TAG = this.getClass().getSimpleName();
    private PreviewView viewFinder;
    private ConstraintLayout cameraContainer;
    private int displayId = -1;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private Preview preview;
    private ImageCapture imageCapture;
    private ImageView ivCameraCrop;
    private TextView textTips;
    private FrameLayout viewMask;
    private ImageButton ibBack, cameraCaptureButton;
    private LinearLayout ll_picture_parent;
    private ImageView imgPicture, img_picture_cancel, img_picture_save;
    private RelativeLayout rl_result_picture;

    private ImageAnalysis imageAnalyzer;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;


    //拍摄类型
    private int mType;
    //是否弹吐司，保证权限for循环只弹一次
    private boolean isToast = true;
    //是否进入setting
    protected boolean isEnterSetting;
    //0 拍照正面 1 拍照反面 2 相册选择正面 3 相册选择反面
    private int curIDCardCamera = 0;
    //结果结合
    private ArrayList<String> mIDCardResult = new ArrayList<>();

    //提示的LayoutParams
    private FrameLayout.LayoutParams tipParams;
    private String takePhotoPath;

    /**
     * Blocking camera operations are performed using this executor
     */
//    private ExecutorService cameraExecutor;
//    private androidx.window.WindowManager windowManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera_x);
        mType = getIntent().getIntExtra(IDCardCameraSelect.TAKE_TYPE, 0);
        initView();
        initListener();
        initData();


    }


    /**
     * 初始化UI
     */
    private void initView() {
        viewFinder = findViewById(R.id.view_finder);
        cameraContainer = findViewById(R.id.camera_container);
        ivCameraCrop = findViewById(R.id.iv_camera_crop);
        textTips = findViewById(R.id.text_tips);
        ibBack = findViewById(R.id.ib_back);
        cameraCaptureButton = findViewById(R.id.camera_capture_button);
        viewMask = findViewById(R.id.view_mask);
        ll_picture_parent = findViewById(R.id.ll_picture_parent);
        imgPicture = findViewById(R.id.img_picture);
        rl_result_picture = findViewById(R.id.rl_result_picture);
        img_picture_cancel = findViewById(R.id.img_picture_cancel);
        img_picture_save = findViewById(R.id.img_picture_save);
    }


    /**
     * 监听事件
     */
    private void initListener() {
        ibBack.setOnClickListener(v -> {
            finish();
        });

        img_picture_cancel.setOnClickListener(v -> {
            ll_picture_parent.setVisibility(View.GONE);
            rl_result_picture.setVisibility(View.GONE);
            ibBack.setVisibility(View.VISIBLE);
            cameraCaptureButton.setVisibility(View.VISIBLE);
        });

        img_picture_save.setOnClickListener(v -> {

//            String newPath = getPictureTempPath();

//            if (ImageUtils.save(bitmap, newPath, Bitmap.CompressFormat.JPEG)) {
            int[] outLocation = Tools.getViewLocal(viewMask);
            Rect rect = new Rect(outLocation[0], outLocation[1],
                    viewMask.getMeasuredWidth(), viewMask.getMeasuredHeight());
            String savePath = getPictureTempPath();
            if (Tools.saveBitmap(CameraXActivity.this, takePhotoPath, savePath, rect, false)) {
                Tools.deletTempFile(takePhotoPath);
                mIDCardResult.add(savePath);
                Intent intent = new Intent();
                intent.putExtra(IDCardCameraSelect.IMAGE_PATH, mIDCardResult);
                setResult(IDCardCameraSelect.RESULT_CODE, intent);
                finish();
            }
//            }
        });

        cameraCaptureButton.setOnClickListener(v -> {
            // 保证相机可用
            if (imageCapture == null)
                return;

             takePhotoPath = getPictureTempPath();

            Log.e("wld_____", "outPath:" + takePhotoPath);


            ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(new File(takePhotoPath)).build();

            //  设置图像捕获监听器，在拍照后触发
            imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            ibBack.setVisibility(View.GONE);
                            cameraCaptureButton.setVisibility(View.GONE);
                            rl_result_picture.setVisibility(View.VISIBLE);
                            ll_picture_parent.setVisibility(View.VISIBLE);
                            Bitmap bitmap = Tools.bitmapClip(CameraXActivity.this, takePhotoPath, false);
                            imgPicture.setImageBitmap(bitmap);


                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e("wld_____", "Photo capture failed: ${exc.message}", exception);
                        }
                    });
        });
    }

    private void initData() {
        Tools.reflectPreviewRatio(viewFinder, Tools.aspectRatio(this));
        viewFinder.post(new Runnable() {
            @Override
            public void run() {
                displayId = viewFinder.getDisplay().getDisplayId();

                // Build UI controls
                updateCameraUi();

                // Set up the camera and its use cases
                setUpCamera();
            }
        });


        tipParams = (FrameLayout.LayoutParams) textTips.getLayoutParams();
        textTips.setText(R.string.idcard_positive_reverse_tip_str);
        textTips.setLayoutParams(tipParams);

        settingCameraType();

    }


    private void settingCameraType() {
        switch (mType) {
            case IDCardCameraSelect.TYPE_IDCARD_FRONT:
                ivCameraCrop.setImageResource(R.mipmap.idcard_lib_positive_bg_icon);
                textTips.setText(R.string.type_idcard_front_str);
                tipParams.leftMargin = ScreenUtils.dip2px(this, 32);
                break;
            case IDCardCameraSelect.TYPE_IDCARD_BACK:
                ivCameraCrop.setImageResource(R.mipmap.idcard_lib_reverse_bg_icon);
                tipParams.leftMargin = ScreenUtils.dip2px(this, 88);
                textTips.setText(R.string.type_idcard_back_str);
                break;
            case IDCardCameraSelect.TYPE_IDCARD_All:
                if (curIDCardCamera == 0 || curIDCardCamera == 2) {
                    ivCameraCrop.setImageResource(R.mipmap.idcard_lib_positive_bg_icon);
                    textTips.setText(R.string.type_idcard_front_str);
                    tipParams.leftMargin = ScreenUtils.dip2px(this, 32);
                } else if (curIDCardCamera == 1 || curIDCardCamera == 3) {
                    ivCameraCrop.setImageResource(R.mipmap.idcard_lib_reverse_bg_icon);
                    tipParams.leftMargin = ScreenUtils.dip2px(this, 88);
                    textTips.setText(R.string.type_idcard_back_str);
                }
                break;
            default:
                break;
        }
        //增加0.5秒过渡界面，解决个别手机首次申请权限导致预览界面启动慢的问题
//        new Handler().postDelayed(() -> runOnUiThread(() -> mCameraPreview.setVisibility(View.VISIBLE)), 500);
    }


    public String getPictureTempPath() {
        File file = new File(Tools.getPicturePath(this));
        String pictureName = file.getName();
        String newName = null;
        if (pictureName.contains(".")) {
            int lastDotIndex = pictureName.lastIndexOf('.');
            newName = pictureName.substring(0, lastDotIndex) + "_temp" + pictureName.substring(lastDotIndex);
        }
        if (newName == null) {
            newName = pictureName;
        }
        return file.getParent() + File.separator + newName;
    }


    private void updateCameraUi() {

    }

    /**
     * Initialize CameraX, and prepare to bind the camera use cases
     */
    private void setUpCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                // CameraProvider
                try {
                    cameraProvider = cameraProviderFuture.get();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Select lensFacing depending on the available cameras
                if (hasBackCamera()) {
                    lensFacing = CameraSelector.LENS_FACING_BACK;
                } else {
                    lensFacing = CameraSelector.LENS_FACING_FRONT;
                }

                // Build and bind the camera use cases
                bindCameraUseCases();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Declare and bind preview, capture and analysis use cases
     * 声明和绑定预览，捕获和分析用例
     */
    private void bindCameraUseCases() {
        // Get screen metrics used to setup camera for full screen resolution
        int screenAspectRatio = Tools.aspectRatio(this);
        Log.d(TAG, "Preview aspect ratio: " + screenAspectRatio);
        int rotation = viewFinder.getDisplay() == null ? Surface.ROTATION_0 : viewFinder.getDisplay().getRotation();
        if (cameraProvider == null) {
            Log.e(TAG, "============> 1");
            return;
        }
        // Preview
        preview = new Preview.Builder()
                // We request aspect ratio but no resolution
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation
                .setTargetRotation(rotation)
                .build();
        // ImageCapture
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                // We request aspect ratio but no resolution to match preview config, but letting
                // CameraX optimize for whatever specific resolution best fits our use cases
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                .setTargetRotation(rotation)
                .build();

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll();

        // ImageAnalysis 指定分辨率
        imageAnalyzer = new ImageAnalysis.Builder()
                // We request aspect ratio but no resolution
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                .setTargetRotation(rotation)
                .build();

        // CameraSelector
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();


        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
//            camera = cameraProvider.bindToLifecycle(
//                    this, cameraSelector, preview, imageCapture, imageAnalyzer);

            camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture);

            // Attach the viewfinder's surface provider to preview use case
            preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
            observeCameraState(camera.getCameraInfo());
        } catch (Exception exc) {
            Log.e(TAG, "Use case binding failed", exc);
        }

    }

    /**
     * 监控相机当前状态
     */
    private void observeCameraState(CameraInfo cameraInfo) {

        cameraInfo.getCameraState().observe(this, new Observer<CameraState>() {
            @Override
            public void onChanged(CameraState cameraState) {
                switch (cameraState.getType()) {
                    case PENDING_OPEN:
                        // Ask the user to close other camera apps
                        Toast.makeText(CameraXActivity.this,
                                "CameraState: Pending Open",
                                Toast.LENGTH_SHORT).show();
                        break;

                    case OPENING:
                        // Show the Camera UI
                        Toast.makeText(CameraXActivity.this,
                                "CameraState: Opening",
                                Toast.LENGTH_SHORT).show();

                        break;
                    case OPEN:
                        // Setup Camera resources and begin processing
                        Toast.makeText(CameraXActivity.this,
                                "CameraState: Open",
                                Toast.LENGTH_SHORT).show();

                        break;
                    case CLOSING:
                        // Close camera UI
                        Toast.makeText(CameraXActivity.this,
                                "CameraState: Closing",
                                Toast.LENGTH_SHORT).show();

                        break;
                    case CLOSED:
                        // Free camera resources
                        Toast.makeText(CameraXActivity.this,
                                "CameraState: Closed",
                                Toast.LENGTH_SHORT).show();

                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + cameraState.getType());
                }

                if (cameraState.getError() != null) {
                    switch (cameraState.getError().getCode()) {
                        // Open errors
                        case CameraState.ERROR_STREAM_CONFIG: {
                            // Make sure to setup the use cases properly
                            Toast.makeText(CameraXActivity.this,
                                    "Stream config error",
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                        // Opening errors
                        case CameraState.ERROR_CAMERA_IN_USE: {
                            // Close the camera or ask user to close another camera app that's using the
                            // camera
                            Toast.makeText(CameraXActivity.this,
                                    "Camera in use",
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                        case CameraState.ERROR_MAX_CAMERAS_IN_USE: {
                            // Close another open camera in the app, or ask the user to close another
                            // camera app that's using the camera
                            Toast.makeText(CameraXActivity.this,
                                    "Max cameras in use",
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                        case CameraState.ERROR_OTHER_RECOVERABLE_ERROR: {
                            Toast.makeText(CameraXActivity.this,
                                    "Other recoverable error",
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                        // Closing errors
                        case CameraState.ERROR_CAMERA_DISABLED: {
                            // Ask the user to enable the device's cameras
                            Toast.makeText(CameraXActivity.this,
                                    "Camera disabled",
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                        case CameraState.ERROR_CAMERA_FATAL_ERROR: {
                            // Ask the user to reboot the device to restore camera function
                            Toast.makeText(CameraXActivity.this,
                                    "Fatal error",
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                        // Closed errors
                        case CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED: {
                            // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                            Toast.makeText(CameraXActivity.this,
                                    "Do not disturb mode enabled",
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                    }
                }


            }
        });
    }


    /**
     * Returns true if the device has an available back camera. False otherwise
     */
    private boolean hasBackCamera() {
        try {
            return cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Returns true if the device has an available front camera. False otherwise
     */
    private boolean hasFrontCamera() {
        try {
            return cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA);
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
        }
        return false;

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraProvider.unbindAll();
    }
}