package me.blankm.idcardlib.utils;

import android.util.Log;

import me.blankm.idcardlib.BuildConfig;

/**
 * 日志工具类
 * <p>
 * 库内统一日志出口，默认跟随 {@link BuildConfig#DEBUG}：发布成 release aar 后默认不打印任何日志。
 * 接入方需要排查问题时可调用 {@link #setDebug(boolean)} 手动打开。
 */
/**
 * 日志工具类，统一管理输出门控。
 * <p>debug/warning 日志受 {@code BuildConfig.DEBUG} 控制，仅调试包输出；error 日志始终输出。
 */
public final class LogUtils {

    private static final String TAG = "IDCardCamera";

    private static boolean sDebug = BuildConfig.DEBUG;

    /** 工具类，禁止实例化 */
    private LogUtils() {
    }

    /**
     * 开关日志，便于接入方在排查问题时临时打开
     *
     * @param debug true 打印日志
     */
    public static void setDebug(boolean debug) {
        sDebug = debug;
    }

    public static boolean isDebug() {
        return sDebug;
    }

    public static void d(String subTag, String msg) {
        if (sDebug) {
            Log.d(TAG, subTag + ": " + msg);
        }
    }

    public static void w(String subTag, String msg) {
        if (sDebug) {
            Log.w(TAG, subTag + ": " + msg);
        }
    }

    /**
     * 错误日志。异常属于真实故障，不受 debug 开关控制，始终输出以便线上定位问题。
     */
    public static void e(String subTag, String msg) {
        Log.e(TAG, subTag + ": " + msg);
    }

    public static void e(String subTag, String msg, Throwable t) {
        Log.e(TAG, subTag + ": " + msg, t);
    }
}
