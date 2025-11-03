package me.blankm.idcardlib.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限工具类
 */

public class PermissionUtils {

    /**
     * 第一次检查权限，用在打开应用的时候请求应用需要的所有权限
     *
     * @param context
     * @param requestCode 请求码
     * @param permission  权限数组
     * @return
     */
    public static boolean checkPermissionFirst(Context context, int requestCode, String[] permission) {
        List<String> permissions = new ArrayList<String>();
        for (String per : permission) {
            int permissionCode = ActivityCompat.checkSelfPermission(context, per);
            if (permissionCode != PackageManager.PERMISSION_GRANTED) {
                permissions.add(per);
            }
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions((Activity) context, permissions.toArray(new String[permissions.size()]), requestCode);
            return false;
        } else {
            return true;
        }
    }

    /**
     * 检查并申请权限（Android 13+ 兼容）
     *
     * @return true 表示所有权限已授予
     */
    public static boolean checkPermissionFirst(Context context, int requestCode) {
        ArrayList<String> permissions = new ArrayList<>();

        // 相机权限（所有版本都需要）
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA);
        }

        // 存储权限（根据 Android 版本选择）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的媒体权限
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 检查 MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                Log.e("checkAndRequest", "需要所有文件访问权限");
                // 注意：MANAGE_EXTERNAL_STORAGE 不能通过 requestPermissions 申请
                // 需要跳转到设置页面，这里先跳过
            }
        } else {
            // Android 10 及以下使用旧权限
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (!permissions.isEmpty()) {
            Log.e("onRequestPermission", "申请权限: " + permissions);
            //permissions.toArray(new String[permissions.size()]
            ActivityCompat.requestPermissions((Activity) context, permissions.toArray(new String[permissions.size()]), requestCode);
            return false;
        }
        return true;
    }


    /**
     * 第二次检查权限，用在某个操作需要某个权限的时候调用
     *
     * @param context
     * @param requestCode 请求码
     * @param permission  权限数组
     * @return
     */
    public static boolean checkPermissionSecond(Context context, int requestCode, String[] permission) {
        List<String> permissions = new ArrayList<String>();
        for (String per : permission) {
            int permissionCode = ActivityCompat.checkSelfPermission(context, per);
            if (permissionCode != PackageManager.PERMISSION_GRANTED) {
                permissions.add(per);
            }
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions((Activity) context, permissions.toArray(new String[permissions.size()]), requestCode);

            /*跳转到应用详情，让用户去打开权限*/
            Intent localIntent = new Intent();
            localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 9) {
                localIntent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                localIntent.setData(Uri.fromParts("package", context.getPackageName(), null));
            } else if (Build.VERSION.SDK_INT <= 8) {
                localIntent.setAction(Intent.ACTION_VIEW);
                localIntent.setClassName("com.android.settings", "com.android.settings.InstalledAppDetails");
                localIntent.putExtra("com.android.settings.ApplicationPkgName", context.getPackageName());
            }
            context.startActivity(localIntent);
            return false;
        } else {
            return true;
        }
    }
}
