package me.blankm.idcardcamera;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.CameraXConfig;

/**
 * Author by Mr.Meng
 * created 2022/2/25 13:10
 *
 * @desc
 */

public class MainApplication extends Application implements  CameraXConfig.Provider {
   @NonNull
   @Override
   public CameraXConfig getCameraXConfig() {
      return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
              .setMinimumLoggingLevel(Log.ERROR).build();
   }
}