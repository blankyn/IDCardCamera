package me.blankm.idcardlib.global;


import me.blankm.idcardlib.utils.FileUtils;

import java.io.File;

@Deprecated
public class Constant {
    //app名称
    public static final String APP_NAME = "IDCardCamera";
    //IDCardCamera/
    public static final String BASE_DIR = APP_NAME + File.separator;
    //文件夹根目录 /storage/emulated/0/IDCardCamera/
    public static final String DIR_ROOT = FileUtils.getRootPath() + File.separator + Constant.BASE_DIR;
}