package me.blankm.idcardlib.utils;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.StringRes;

/**
 * 进度对话框辅助类
 * <p>
 * 封装 ProgressDialog 的显示/隐藏逻辑，确保总在主线程操作且避免窗口泄漏。
 * 用法：
 * <pre>
 * ProgressDialogHelper helper = new ProgressDialogHelper(context);
 * helper.show(R.string.loading_processing);
 * // 后台任务完成后
 * helper.dismiss();
 * </pre>
 */
public class ProgressDialogHelper {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final Context context;
    private ProgressDialog progressDialog;

    public ProgressDialogHelper(Context context) {
        this.context = context;
    }

    /**
     * 显示进度对话框
     *
     * @param messageResId 提示文本资源 ID
     */
    public void show(@StringRes int messageResId) {
        show(context.getString(messageResId));
    }

    /**
     * 显示进度对话框
     *
     * @param message 提示文本
     */
    public void show(String message) {
        runOnUiThread(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                return;
            }
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage(message);
            progressDialog.setCancelable(false);
            progressDialog.show();
        });
    }

    /**
     * 关闭进度对话框
     */
    public void dismiss() {
        runOnUiThread(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        });
    }

    /**
     * 确保操作在主线程执行
     */
    private void runOnUiThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            MAIN_HANDLER.post(action);
        }
    }
}
