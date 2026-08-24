package me.blankm.idcardlib.camera;

import static me.blankm.idcardlib.camera.IDCardCameraSelect.PERMISSION_CODE_FIRST;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import me.blankm.idcardlib.cropper.CropImageView;
import me.blankm.idcardlib.dialog.IDCardDialog;
import me.blankm.idcardlib.utils.BitmapPool;
import me.blankm.idcardlib.utils.CommonUtils;
import me.blankm.idcardlib.utils.FileUtils;
import me.blankm.idcardlib.utils.ImageUtils;
import me.blankm.idcardlib.utils.LogUtils;
import me.blankm.idcardlib.utils.PermissionChecker;
import me.blankm.idcardlib.utils.PermissionUtils;
import me.blankm.idcardlib.utils.ProgressDialogHelper;

import me.blankm.idcardlib.R;

import me.blankm.idcardlib.cropper.AlbumClipImageView;
import me.blankm.idcardlib.cropper.CropListener;
import me.blankm.idcardlib.utils.ScreenUtils;
import me.blankm.idcardlib.utils.UriUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * 身份证拍照界面（传统 Camera API 实现）
 *
 * <p>入口：通过 {@link IDCardCameraSelect#openCamera(int)} 启动，支持三种模式：
 * 单拍正面（{@code TYPE_IDCARD_FRONT}）、单拍反面（{@code TYPE_IDCARD_BACK}）、
 * 连拍双面（{@code TYPE_IDCARD_All}）。
 *
 * <p>拍摄完成后以 {@link IDCardCameraSelect#RESULT_CODE} 回传，
 * 用 {@link IDCardCameraSelect#getImagePath(android.content.Intent)} 取图片路径列表。
 *
 * <p>内部状态机由 {@code curIDCardCamera} 驱动：
 * <ul>
 *   <li>0 — 相机拍正面</li>
 *   <li>1 — 相机拍反面</li>
 *   <li>2 — 相册选正面</li>
 *   <li>3 — 相册选反面</li>
 * </ul>
 *
 * <p>与 {@link CameraXActivity} 的主要区别：
 * 本类使用 {@code android.hardware.Camera}（legacy API），自带手动四点裁剪框（{@code CropOverlayView}）；
 * CameraXActivity 使用 CameraX，裁剪逻辑完全独立，两者不共享代码路径。
 */
public class CameraActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "CameraActivity";

    /**
     * 相册图片解码的边长上限。
     * 不用屏幕尺寸：裁剪后的身份证区域仍需保留足够分辨率用于后续识别。
     */
    private static final int MAX_ALBUM_IMAGE_SIZE = 2048;

    private CameraPreview mCameraPreview;
    private CropImageView mCropImageView;
    private Bitmap mCropBitmap;
    private ImageButton mIvCameraCrop;
    private TextView mIdCardCameraTipStrTv;
    private Button mNextResultOk;
    private ImageView mTakePhoto;
    private FrameLayout mIdCardCropFly;
    private ImageView mAlbum;
    private AlbumClipImageView mAlbumClipIv;
    private RelativeLayout mIdCardCameraRl;
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
    //标题
    private TextView titleTv;
    //max widget
    private int mMaxWidth;
    // 图片被旋转的角度
    private int mDegree;
    // 大图被设置之前的缩放比例
    private int mSampleSize;
    private int mSourceWidth;
    private int mSourceHeight;
    //相册选择图片裁剪后的输出路径
    private String mOutputPath;
    private String mInputPath;
    //后台线程处理图片解码/裁剪/写盘，避免阻塞主线程
    private final ExecutorService mIoExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    //进度提示辅助类
    private ProgressDialogHelper mProgressHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean checkPermissionFirst = PermissionUtils.checkPermissionFirst(this, PERMISSION_CODE_FIRST);
        if (checkPermissionFirst) init();
    }


    /**
     * 处理请求权限的响应
     *
     * @param requestCode  请求码
     * @param permissions  权限数组
     * @param grantResults 请求权限结果数组
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean isPermissions = true;
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                isPermissions = false;
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                    //用户选择了"不再询问"
                    if (isToast) {
                        Toast.makeText(this, getString(R.string.permission_open_str), Toast.LENGTH_SHORT).show();
                        isToast = false;
                    }
                }
            }
        }
        isToast = true;
        if (isPermissions) {
            LogUtils.d(TAG, "允许所有权限");
            init();
        } else {
            LogUtils.d(TAG, "有权限不允许");
            showPermissionsDialog(getString(R.string.permission_not_granted));
        }
    }

    /**
     * 权限校验通过后的初始化入口，设置布局、读取拍摄类型参数、依次初始化控件和监听器。
     */
    private void init() {
        setContentView(R.layout.idcard_camera_view);
        mType = getIntent().getIntExtra(IDCardCameraSelect.TAKE_TYPE, 0);
        initView();
        initListener();
        settingCameraType();
    }

    /**
     * 绑定布局控件并计算身份证扫描框的尺寸。
     * <p>扫描框宽度 = 屏幕短边 - 32dp 边距，高度 = 屏幕长边的 30%。
     */
    private void initView() {
        mProgressHelper = new ProgressDialogHelper(this);
        mCameraPreview = findViewById(R.id.camera_preview);
        mIvCameraCrop = findViewById(R.id.iv_camera_crop);
        mCropImageView = findViewById(R.id.crop_image_view);
        mIdCardCameraTipStrTv = findViewById(R.id.idcard_tip_str_tv);
        mNextResultOk = findViewById(R.id.iv_camera_result_ok);
        mTakePhoto = findViewById(R.id.iv_camera_take);
        mAlbum = findViewById(R.id.iv_camera_album);
        mIdCardCropFly = findViewById(R.id.idcard_crop_fly);
        titleTv = findViewById(R.id.idcard_title_tv);
        mAlbumClipIv = findViewById(R.id.album_clip_iv);
        mIdCardCameraRl = findViewById(R.id.idcard_camera_rl);
        //宽 1080 高2232
        float screenMinSize = Math.min(ScreenUtils.getScreenWidth(this), ScreenUtils.getScreenHeight(this));
        float screenMaxSize = Math.max(ScreenUtils.getScreenWidth(this), ScreenUtils.getScreenHeight(this));
        int mMargin = 8;
        float width = screenMinSize - (mMargin * 4);
        float height = screenMaxSize * 0.30f;
        tipParams = (FrameLayout.LayoutParams) mIdCardCameraTipStrTv.getLayoutParams();
        mIdCardCameraTipStrTv.setText(R.string.idcard_positive_reverse_tip_str);
        FrameLayout.LayoutParams cropParams = new FrameLayout.LayoutParams((int) width, (int) height);
        cropParams.setMargins(mMargin, mMargin, mMargin, mMargin);
        cropParams.gravity = Gravity.CENTER;
        mIvCameraCrop.setLayoutParams(cropParams);
        mIdCardCameraTipStrTv.setLayoutParams(tipParams);
    }

    /**
     * 根据当前拍摄状态（{@code curIDCardCamera} 和 {@code mType}）切换扫描框图标、标题和提示文字位置。
     * <p>切换后延迟 500ms 显示预览控件，规避个别机型首次申请权限后预览启动慢的问题。
     */
    private void settingCameraType() {
        switch (mType) {
            case IDCardCameraSelect.TYPE_IDCARD_FRONT:
                mIvCameraCrop.setImageResource(R.mipmap.idcard_lib_positive_bg_icon);
                titleTv.setText(R.string.type_idcard_front_str);
                tipParams.leftMargin = ScreenUtils.dip2px(this, 32);
                break;
            case IDCardCameraSelect.TYPE_IDCARD_BACK:
                mIvCameraCrop.setImageResource(R.mipmap.idcard_lib_reverse_bg_icon);
                tipParams.leftMargin = ScreenUtils.dip2px(this, 88);
                titleTv.setText(R.string.type_idcard_back_str);
                break;
            case IDCardCameraSelect.TYPE_IDCARD_All:
                if (curIDCardCamera == 0 || curIDCardCamera == 2) {
                    mIvCameraCrop.setImageResource(R.mipmap.idcard_lib_positive_bg_icon);
                    titleTv.setText(R.string.type_idcard_front_str);
                    tipParams.leftMargin = ScreenUtils.dip2px(this, 32);
                } else if (curIDCardCamera == 1 || curIDCardCamera == 3) {
                    mIvCameraCrop.setImageResource(R.mipmap.idcard_lib_reverse_bg_icon);
                    tipParams.leftMargin = ScreenUtils.dip2px(this, 88);
                    titleTv.setText(R.string.type_idcard_back_str);
                }
                break;
            default:
                break;
        }
        //增加0.5秒过渡界面，解决个别手机首次申请权限导致预览界面启动慢的问题
        //复用统一的主线程 Handler，onDestroy 中会移除未执行的回调
        mMainHandler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            mCameraPreview.setVisibility(View.VISIBLE);
        }, 500);
    }

    /**
     * 注册各操作按钮的点击监听，委托给 {@link #onClick(android.view.View)}。
     */
    private void initListener() {
        findViewById(R.id.iv_camera_close).setOnClickListener(this);
        findViewById(R.id.idcard_title_refresh_iv).setOnClickListener(this);
        mCameraPreview.setOnClickListener(this);
        mNextResultOk.setOnClickListener(this);
        mTakePhoto.setOnClickListener(this);
        mAlbum.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.camera_preview) {
            //对焦
            mCameraPreview.focus();
        } else if (id == R.id.iv_camera_close) {
            //退出
            finish();
        } else if (id == R.id.idcard_title_refresh_iv) {
            //刷新重置拍照模式
            if (mNextResultOk.getVisibility() == View.VISIBLE) {
                mCameraPreview.setEnabled(true);
                mCameraPreview.addCallback();
                mCameraPreview.startPreview();
                setTakePhotoLayout();
            }
        } else if (id == R.id.iv_camera_album) {
            //相册选择图片
            albumChoosePhoto();
        } else if (id == R.id.iv_camera_take) {
            //确定拍照
            if (!CommonUtils.isFastClick()) {
                takePhoto();
            }
        } else if (id == R.id.iv_camera_result_ok) {
            //下一步【原有功能增加->接着拍反面】
            NextConfirm();
        }
    }

    /**
     * 调起系统相册选图，结果通过 {@link #onActivityResult} 回调处理。
     */
    private void albumChoosePhoto() {
        //系统图库选择一张图片
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
//         开启一个带有返回值的Activity，请求码为PHOTO_REQUEST_GALLERY
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        startActivityForResult(intent, IDCardCameraSelect.REQUEST_ALBUM_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode != IDCardCameraSelect.REQUEST_ALBUM_CODE || data == null) return;
        //拿到相册选择的图片show 到裁剪UI
        final Uri uri = data.getData();
        if (uri == null) return;
        //显示加载提示
        mProgressHelper.show(R.string.loading_processing);
        //文件复制与图片解码都是耗时操作，放到后台线程
        mIoExecutor.execute(() -> {
            String path = "";
            Bitmap sourceBitmap = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                //适配AndroidQ
                File file = UriUtils.uriToFileApiQ(this, uri);
                if (file != null) {
                    path = file.getPath();
                    //采样解码，避免大图整图解码导致OOM。
                    //上限取 2048 而非屏幕尺寸：裁剪后的身份证区域仍需保留足够分辨率用于识别
                    sourceBitmap = ImageUtils.decodeSampledBitmap(path,
                            MAX_ALBUM_IMAGE_SIZE, MAX_ALBUM_IMAGE_SIZE);
                }
            } else {
                File file = UriUtils.getFileFromMediaUri(this, uri);
                if (file != null) {
                    path = file.getAbsolutePath();
                    Bitmap photoBmp = null;
                    try {
                        photoBmp = UriUtils.getBitmapFormUri(this, Uri.fromFile(file));
                    } catch (IOException e) {
                        LogUtils.e(TAG, "相册图片解码失败", e);
                    }
                    int degree = UriUtils.getBitmapDegree(file.getAbsolutePath());
                    //把图片旋转为正的方向
                    sourceBitmap = UriUtils.rotateBitmapByDegree(photoBmp, degree);
                }
            }
            final String finalPath = path;
            final Bitmap finalBitmap = sourceBitmap;
            mMainHandler.post(() -> {
                mProgressHelper.dismiss();
                if (isFinishing() || isDestroyed()) return;
                if (finalBitmap == null) {
                    Toast.makeText(getApplicationContext(),
                            getString(R.string.error_image_decode_failed), Toast.LENGTH_SHORT).show();
                    return;
                }
                showAlbumClipView(finalPath, finalBitmap);
            });
        });
    }

    /**
     * 展示相册图片的手动裁剪界面
     */
    private void showAlbumClipView(String path, Bitmap sourceBitmap) {
        mInputPath = path;
        if (curIDCardCamera == 0) curIDCardCamera = 2;
        if (curIDCardCamera == 1) curIDCardCamera = 3;
        mCameraPreview.setEnabled(false);
        mCameraPreview.onStop();
        mIdCardCameraRl.setVisibility(View.GONE);
        mAlbumClipIv.setVisibility(View.VISIBLE);
        setCropLayout();
        mAlbumClipIv.post(() -> mAlbumClipIv.setImageBitmap(sourceBitmap));
    }


    /**
     * 拍照 1080  x  2340
     */
    private void takePhoto() {
        mCameraPreview.setEnabled(false);
        CameraUtils.getCamera().setOneShotPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(final byte[] bytes, Camera camera) {
                //获取预览大小
                final Camera.Size size = camera.getParameters().getPreviewSize();
                camera.stopPreview();
                //复用统一的后台线程，随页面销毁一起结束
                mIoExecutor.execute(() -> {
                    final int w = size.width;
                    final int h = size.height;
                    LogUtils.d(TAG, "preview size " + w + "x" + h);
                    Bitmap bitmap = ImageUtils.getBitmapFromByte(bytes, w, h);
                    if (bitmap != null) {
                        cropImage(ImageUtils.roteBitmap(bitmap));
                    }
                });
            }
        });
    }

    /**
     * 正常拍照后裁剪图片
     */
    private void cropImage(Bitmap bitmap) {
        /*计算扫描框的坐标点*/
        //16
        float left = mIvCameraCrop.getLeft();
        float top = mIdCardCropFly.getTop() + mIvCameraCrop.getTop();
        float right = mIvCameraCrop.getRight() - left;
        float bottom = mIvCameraCrop.getBottom() + top;

        /*计算扫描框坐标点占原图坐标点的比例*/
        float leftProportion = left / mCameraPreview.getWidth();
        float topProportion = top / mCameraPreview.getHeight();
        float rightProportion = right / mCameraPreview.getWidth();
        float bottomProportion = bottom / mCameraPreview.getBottom();

        //系统先自动裁剪
        mCropBitmap = Bitmap.createBitmap(bitmap,
                (int) (leftProportion * (float) bitmap.getWidth()),
                (int) (topProportion * (float) bitmap.getHeight()),
                (int) ((rightProportion - leftProportion) * (float) bitmap.getWidth()),
                (int) ((bottomProportion - topProportion) * (float) bitmap.getHeight()));

        //设置显示手动裁剪模式
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //将手动裁剪区域设置成与扫描框一样大
                FrameLayout.LayoutParams cropParams = new FrameLayout.LayoutParams(mIdCardCropFly.getWidth(), mIdCardCropFly.getHeight());
                cropParams.gravity = Gravity.CENTER;
                mCropImageView.setLayoutParams(cropParams);
                setCropLayout();
                mCropImageView.setImageBitmap(mCropBitmap);
            }
        });
    }

    /**
     * 切换到手动裁剪模式：隐藏取景器和拍照控件，显示裁剪控件和确认按钮。
     * <p>拍照路径显示 {@code CropImageView}；相册路径显示 {@code AlbumClipImageView}。
     */
    private void setCropLayout() {
        mIvCameraCrop.setVisibility(View.GONE);
        mCameraPreview.setVisibility(View.GONE);
        mTakePhoto.setVisibility(View.GONE);
        mAlbum.setVisibility(View.GONE);
        mIdCardCameraTipStrTv.setVisibility(View.GONE);
        if (mAlbumClipIv.getVisibility() == View.GONE) {
            mCropImageView.setVisibility(View.VISIBLE);
        } else {
            mCropImageView.setVisibility(View.GONE);
        }
        if (mType == IDCardCameraSelect.TYPE_IDCARD_All) {
            mNextResultOk.setText(R.string.idcard_net_step_str);
        } else {
            mNextResultOk.setText(R.string.idcard_finish);
        }
        mNextResultOk.setVisibility(View.VISIBLE);
    }

    /**
     * 切换回拍照预览模式：恢复取景器和拍照控件，隐藏裁剪控件，同时触发一次对焦。
     */
    private void setTakePhotoLayout() {
        mIdCardCameraRl.setVisibility(View.VISIBLE);
        mIvCameraCrop.setVisibility(View.VISIBLE);
        mCameraPreview.setVisibility(View.VISIBLE);
        mTakePhoto.setVisibility(View.VISIBLE);
        mAlbum.setVisibility(View.VISIBLE);
        mIdCardCameraTipStrTv.setVisibility(View.VISIBLE);
        mCropImageView.setVisibility(View.GONE);
        mNextResultOk.setVisibility(View.GONE);
        mAlbumClipIv.setVisibility(View.GONE);
        mCameraPreview.focus();
    }

    /**
     * 点击「下一步 / 完成」按钮的处理逻辑。
     * <ul>
     *   <li>拍照路径（{@code curIDCardCamera < 2}）：调 {@link me.blankm.idcardlib.cropper.CropImageView#crop}
     *       异步裁剪，写盘后通过 {@link #cameraCropNext} 推进状态机。</li>
     *   <li>相册路径：调 {@link #clipImage} 完成相册图片裁剪。</li>
     * </ul>
     */
    private void NextConfirm() {
        if (curIDCardCamera < 2) {
            //拍照后裁剪确定的图片
            mProgressHelper.show(R.string.loading_cropping);
            mCropImageView.crop(new CropListener() {
                @Override
                public void onFinish(Bitmap bitmap) {
                    if (bitmap == null) {
                        mProgressHelper.dismiss();
                        Toast.makeText(getApplicationContext(), getString(R.string.error_image_decode_failed), Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    //保存图片到sdcard并返回图片路径，压缩写盘放到后台线程
                    final String imagePath = FileUtils.getImageCacheDir(CameraActivity.this)
                            + File.separator + System.currentTimeMillis() + ".jpg";
                    mIoExecutor.execute(() -> {
                        final boolean saved = ImageUtils.save(bitmap, imagePath, Bitmap.CompressFormat.JPEG);
                        mMainHandler.post(() -> {
                            mProgressHelper.dismiss();
                            if (isFinishing() || isDestroyed()) return;
                            if (saved) {
                                mIDCardResult.add(imagePath);
                                cameraCropNext();
                            } else {
                                Toast.makeText(getApplicationContext(),
                                        getString(R.string.error_image_save_failed), Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                }
            }, true);
        } else {
            //相册选择图片后的裁剪
            clipImage();
        }
    }

    /**
     * 相机拍照裁剪完成后的状态推进。
     * <ul>
     *   <li>双面模式：正面（0）完成后推进到反面（1）；反面完成后回传结果。</li>
     *   <li>单面模式：直接回传结果。</li>
     * </ul>
     */
    private void cameraCropNext() {
        if (mType == IDCardCameraSelect.TYPE_IDCARD_All) {
            if (curIDCardCamera == 0) {
                curIDCardCamera = 1;
                settingCameraType();
                if (mNextResultOk.getVisibility() == View.VISIBLE) {
                    mCameraPreview.setEnabled(true);
                    mCameraPreview.addCallback();
                    mCameraPreview.startPreview();
                    setTakePhotoLayout();
                }
            } else if (curIDCardCamera == 1) {
                setResultAndFinish();
            }
        } else {
            setResultAndFinish();
        }
    }

    /**
     * 回传图片路径并结束页面
     * <p>
     * 必须用 putStringArrayListExtra，读取侧 {@link IDCardCameraSelect#getImagePath} 用的是
     * getStringArrayListExtra，写成 putExtra(String, Serializable) 取不到值。
     */
    private void setResultAndFinish() {
        Intent intent = new Intent();
        intent.putStringArrayListExtra(IDCardCameraSelect.IMAGE_PATH, mIDCardResult);
        setResult(IDCardCameraSelect.RESULT_CODE, intent);
        finish();
    }

    /**
     * 相册选图裁剪完成后的状态推进，逻辑与 {@link #cameraCropNext} 对称：
     * 双面模式下正面（2）完成后切换到反面（3），反面完成后回传结果。
     */
    private void albumCropNext() {
        if (mType == IDCardCameraSelect.TYPE_IDCARD_All) {
            if (curIDCardCamera == 2) {
                curIDCardCamera = 3;
                settingCameraType();
                if (mNextResultOk.getVisibility() == View.VISIBLE) {
                    mCameraPreview.setEnabled(true);
                    mCameraPreview.addCallback();
                    mCameraPreview.startPreview();
                    setTakePhotoLayout();
                }
            } else if (curIDCardCamera == 3) {
                setResultAndFinish();
            }
        } else {
            setResultAndFinish();
        }
    }


    /**
     * 相册图片裁剪并保存到缓存目录。
     * <p>裁剪和写盘在 {@code mIoExecutor} 后台线程执行，完成后通过 {@code mMainHandler} 回到主线程更新状态。
     * 仅写盘成功后才将路径加入结果列表，避免回传不存在的文件。
     */
    private void clipImage() {

        mOutputPath = new File(getExternalCacheDir(), System.currentTimeMillis() + "_album.jpg").getPath();

        if (TextUtils.isEmpty(mOutputPath)) {
            finish();
            return;
        }

        mProgressHelper.show(R.string.loading_cropping);
        final String outputPath = mOutputPath;
        mIoExecutor.execute(() -> {
            boolean success = false;
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(outputPath);
                //裁剪返回bitmap
                Bitmap bitmap = createClippedBitmap();
                if (bitmap != null) {
                    //身份证照片用 90 质量，平衡识别率和文件大小
                    success = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                }
            } catch (Exception e) {
                LogUtils.e(TAG, "相册图片裁剪失败", e);
            } finally {
                FileUtils.closeIO(fos);
            }
            final boolean saved = success;
            mMainHandler.post(() -> {
                mProgressHelper.dismiss();
                if (isFinishing() || isDestroyed()) return;
                if (saved) {
                    //仅在写盘成功后才记录路径，避免回传不存在的文件
                    mIDCardResult.add(outputPath);
                    albumCropNext();
                } else {
                    Toast.makeText(getApplicationContext(), R.string.error_image_save_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * 从 {@code AlbumClipImageView} 中提取最终的裁剪位图。
     * <p>当前 {@code mSampleSize} 始终 ≤ 1（字段从未被赋值），因此固定走 {@code mAlbumClipIv.clip()} 分支。
     * {@code BitmapRegionDecoder} 分支为历史遗留代码，实际不可达。
     */
    private Bitmap createClippedBitmap() {
        if (mSampleSize <= 1) {
            return mAlbumClipIv.clip();
        }

        // 获取缩放位移后的矩阵值
        final float[] matrixValues = mAlbumClipIv.getClipMatrixValues();
        final float scale = matrixValues[Matrix.MSCALE_X];
        final float transX = matrixValues[Matrix.MTRANS_X];
        final float transY = matrixValues[Matrix.MTRANS_Y];

        // 获取在显示的图片中裁剪的位置
        final Rect border = mAlbumClipIv.getClipBorder();
        final float cropX = ((-transX + border.left) / scale) * mSampleSize;
        final float cropY = ((-transY + border.top) / scale) * mSampleSize;
        final float cropWidth = (border.width() / scale) * mSampleSize;
        final float cropHeight = (border.height() / scale) * mSampleSize;

        // 获取在旋转之前的裁剪位置
        final RectF srcRect = new RectF(cropX, cropY, cropX + cropWidth, cropY + cropHeight);
        final Rect clipRect = getRealRect(srcRect);

        final BitmapFactory.Options ops = new BitmapFactory.Options();
        final Matrix outputMatrix = new Matrix();

        outputMatrix.setRotate(mDegree);
        // 如果裁剪之后的图片宽高仍然太大,则进行缩小
        if (mMaxWidth > 0 && cropWidth > mMaxWidth) {
            ops.inSampleSize = findBestSample((int) cropWidth, mMaxWidth);

            final float outputScale = mMaxWidth / (cropWidth / ops.inSampleSize);
            outputMatrix.postScale(outputScale, outputScale);
        }

        // 裁剪
        BitmapRegionDecoder decoder = null;
        try {
            decoder = BitmapRegionDecoder.newInstance(mInputPath, false);
            final Bitmap source = decoder.decodeRegion(clipRect, ops);
            recycleImageViewBitmap();
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), outputMatrix, false);
        } catch (Exception e) {
            return mAlbumClipIv.clip();
        } finally {
            if (decoder != null && !decoder.isRecycled()) {
                decoder.recycle();
            }
        }
    }

    /**
     * 计算最好的采样大小。
     *
     * @param origin 当前宽度
     * @param target 限定宽度
     * @return sampleSize
     */
    private static int findBestSample(int origin, int target) {
        int sample = 1;
        for (int out = origin / 2; out > target; out /= 2) {
            sample *= 2;
        }
        return sample;
    }

    /**
     * 在主线程将 {@code AlbumClipImageView} 的图片引用置空，便于 GC 回收解码后的大 bitmap。
     */
    private void recycleImageViewBitmap() {
        mAlbumClipIv.post(() -> mAlbumClipIv.setImageBitmap(null));
    }

    /**
     * 将显示坐标系中的裁剪矩形还原到原始图片坐标系。
     * <p>图片可能因 EXIF 信息被旋转 90 / 180 / 270 度，
     * 需要对 {@code srcRect} 做对应的逆变换后才能用于 {@link android.graphics.BitmapRegionDecoder#decodeRegion}。
     *
     * @param srcRect 在旋转后的图片坐标系中的裁剪区域
     * @return 对应原始图片坐标系中的裁剪矩形
     */
    private Rect getRealRect(RectF srcRect) {
        switch (mDegree) {
            case 90:
                return new Rect((int) srcRect.top, (int) (mSourceHeight - srcRect.right),
                        (int) srcRect.bottom, (int) (mSourceHeight - srcRect.left));
            case 180:
                return new Rect((int) (mSourceWidth - srcRect.right), (int) (mSourceHeight - srcRect.bottom),
                        (int) (mSourceWidth - srcRect.left), (int) (mSourceHeight - srcRect.top));
            case 270:
                return new Rect((int) (mSourceWidth - srcRect.bottom), (int) srcRect.left,
                        (int) (mSourceWidth - srcRect.top), (int) srcRect.right);
            default:
                return new Rect((int) srcRect.left, (int) srcRect.top, (int) srcRect.right, (int) srcRect.bottom);
        }
    }


    /**
     * 弹出权限说明对话框。
     * <p>「去设置」按钮跳转到系统应用详情页，用户授权后通过 {@link #onResume} 重新检查权限。
     * 「取消」按钮直接关闭界面。
     *
     * @param errorMsg 对话框正文，描述缺少的具体权限
     */
    private void showPermissionsDialog(String errorMsg) {
        if (isFinishing()) {
            return;
        }
        final IDCardDialog dialog = new IDCardDialog(this, R.layout.picture_wind_base_dialog);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        Button btn_cancel = dialog.findViewById(R.id.btn_cancel);
        Button btn_commit = dialog.findViewById(R.id.btn_commit);
        btn_commit.setText(getString(R.string.picture_go_setting));
        TextView tvTitle = dialog.findViewById(R.id.tvTitle);
        TextView tv_content = dialog.findViewById(R.id.tv_content);
        tvTitle.setText(getString(R.string.picture_prompt));
        tv_content.setText(errorMsg);
        btn_cancel.setOnClickListener(v -> {
            if (!isFinishing()) {
                dialog.dismiss();
                finish();
            }
        });
        btn_commit.setOnClickListener(v -> {
            if (!isFinishing()) {
                dialog.dismiss();
            }
            PermissionChecker.launchAppDetailsSettings(this);
            isEnterSetting = true;
        });
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 这里针对权限被手动拒绝后进入设置页面重新获取权限后的操作
        if (isEnterSetting) {
            boolean isExternalStorage = PermissionChecker.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    PermissionChecker.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (isExternalStorage) {
                boolean isCameraPermissionChecker = PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA);
                if (isCameraPermissionChecker) {
                    init();
                } else {
                    showPermissionsDialog(getString(R.string.picture_camera));
                }
            } else {
                showPermissionsDialog(getString(R.string.picture_jurisdiction));
            }
            isEnterSetting = false;
        }
    }

    /**
     * 重写权限检查方法，支持 Android 11+ 的兼容性
     */
    @Override
    public int checkSelfPermission(String permission) {
        // Android 11+ 特殊处理存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission) ||
                    Manifest.permission.READ_EXTERNAL_STORAGE.equals(permission)) {
                // 如果已授予 MANAGE_EXTERNAL_STORAGE，返回已授予
                if (Environment.isExternalStorageManager()) {
                    return PackageManager.PERMISSION_GRANTED;
                }
                // 对于 Android 11+，即使没有 MANAGE_EXTERNAL_STORAGE，
                // 应用自己的私有目录也不需要权限，所以返回已授予
                return PackageManager.PERMISSION_GRANTED;
            }
        }
        return super.checkSelfPermission(permission);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mCameraPreview != null) {
            mCameraPreview.onStart();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mCameraPreview != null) {
            mCameraPreview.onStop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //移除未执行的回调并停止后台线程，避免页面销毁后仍持有 Activity 引用
        mMainHandler.removeCallbacksAndMessages(null);
        mIoExecutor.shutdownNow();
        //关闭可能未关闭的进度对话框，避免窗口泄漏
        if (mProgressHelper != null) {
            mProgressHelper.dismiss();
        }
        //清理 Bitmap 复用池，释放内存
        BitmapPool.get().clear();
    }
}