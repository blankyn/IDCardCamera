package me.blankm.idcardlib.utils;

import android.content.Intent;
import android.text.TextUtils;

/**
 * 相册裁剪配置类（Builder 模式）。
 * <p>用于 {@link me.blankm.idcardlib.camera.CameraActivity} 从相册选图后的手动裁剪流程。
 */
public class ClipOptions {

    private int aspectX;
    private int aspectY;
    private int maxWidth;
    private String tip;
    private String inputPath;
    private String outputPath;

    /** 私有构造，通过 {@link #with()} 创建实例 */
    private ClipOptions() {
    }

    /** 设置裁剪宽高比的宽度分量（例如 16:9 中的 16） */
    public ClipOptions aspectX(int aspectX) {
        this.aspectX = aspectX;
        return this;
    }

    /** 设置裁剪宽高比的高度分量（例如 16:9 中的 9） */
    public ClipOptions aspectY(int aspectY) {
        this.aspectY = aspectY;
        return this;
    }

    /** 设置裁剪后图片的最大宽度（像素），超过时自动缩放 */
    public ClipOptions maxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        return this;
    }

    /** 设置裁剪界面顶部的提示文字 */
    public ClipOptions tip(String tip) {
        this.tip = tip;
        return this;
    }

    /** 设置待裁剪图片的输入路径 */
    public ClipOptions inputPath(String path) {
        this.inputPath = path;
        return this;
    }

    /** 设置裁剪结果的输出路径 */
    public ClipOptions outputPath(String path) {
        this.outputPath = path;
        return this;
    }

    /** 获取裁剪宽高比的宽度分量 */
    public int getAspectX() {
        return aspectX;
    }

    /** 获取裁剪宽高比的高度分量 */
    public int getAspectY() {
        return aspectY;
    }

    /** 获取裁剪后图片的最大宽度 */
    public int getMaxWidth() {
        return maxWidth;
    }

    /** 获取裁剪界面提示文字 */
    public String getTip() {
        return tip;
    }

    /** 获取待裁剪图片的输入路径 */
    public String getInputPath() {
        return inputPath;
    }

    /** 获取裁剪结果的输出路径 */
    public String getOutputPath() {
        return outputPath;
    }

    private void checkValues() {
        if (TextUtils.isEmpty(inputPath)) {
            throw new IllegalArgumentException("The input path could not be empty");
        }
        if (TextUtils.isEmpty(outputPath)) {
            throw new IllegalArgumentException("The output path could not be empty");
        }
    }

    public static ClipOptions createFromBundle(Intent intent) {
        return new ClipOptions()
                .aspectX(intent.getIntExtra("aspectX", 1))
                .aspectY(intent.getIntExtra("aspectY", 1))
                .maxWidth(intent.getIntExtra("maxWidth", 0))
                .tip(intent.getStringExtra("tip"))
                .inputPath(intent.getStringExtra("inputPath"))
                .outputPath(intent.getStringExtra("outputPath"));
    }
}
