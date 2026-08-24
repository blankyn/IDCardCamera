package me.blankm.idcardlib.dialog;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import me.blankm.idcardlib.R;


/**
 * 简单的提示对话框封装类。
 * <p>用于权限说明、错误提示等场景，提供标题、正文、双按钮的标准布局。
 */
public class IDCardDialog extends Dialog {

    public IDCardDialog(Context context, int layout) {
        super(context, R.style.Picture_Theme_Dialog);
        setContentView(layout);
        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
    }
}
