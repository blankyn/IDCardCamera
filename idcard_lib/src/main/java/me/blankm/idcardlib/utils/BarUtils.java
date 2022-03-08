package me.blankm.idcardlib.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * Author by Mr.Meng
 * created 2022/2/25 14:08
 *
 * @desc
 */

public class BarUtils {


   private static final String TAG_STATUS_BAR = "TAG_STATUS_BAR";
   private static final String TAG_OFFSET     = "TAG_OFFSET";
   private static final int    KEY_OFFSET     = -123;

   private BarUtils() {
      throw new UnsupportedOperationException("u can't instantiate me...");
   }

   /**
    * Return the status bar's height.
    *
    * @return the status bar's height
    */
   public static int getStatusBarHeight(Context context) {
      Resources resources = context.getResources();
      int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
      return resources.getDimensionPixelSize(resourceId);
   }


   /**
    * Set the status bar's visibility.
    *
    * @param activity  The activity.
    * @param isVisible True to set status bar visible, false otherwise.
    */
   public static void setStatusBarVisibility(@NonNull final Activity activity,
                                             final boolean isVisible) {
      setStatusBarVisibility(activity,activity.getWindow(), isVisible);
   }

   /**
    * Set the status bar's visibility.
    *
    * @param window    The window.
    * @param isVisible True to set status bar visible, false otherwise.
    */
   public static void setStatusBarVisibility(Context context, @NonNull final Window window,
                                             final boolean isVisible) {
      if (isVisible) {
         window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
         showStatusBarView(window);
         addMarginTopEqualStatusBarHeight(context,window);
      } else {
         window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
         hideStatusBarView(window);
         subtractMarginTopEqualStatusBarHeight(context,window);
      }
   }




   /**
    * Add the top margin size equals status bar's height for view.
    *
    * @param view The view.
    */
   public static void addMarginTopEqualStatusBarHeight(Context context, @NonNull View view) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
      view.setTag(TAG_OFFSET);
      Object haveSetOffset = view.getTag(KEY_OFFSET);
      if (haveSetOffset != null && (Boolean) haveSetOffset) return;
      ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
      layoutParams.setMargins(layoutParams.leftMargin,
              layoutParams.topMargin + getStatusBarHeight(context),
              layoutParams.rightMargin,
              layoutParams.bottomMargin);
      view.setTag(KEY_OFFSET, true);
   }

   /**
    * Subtract the top margin size equals status bar's height for view.
    *
    * @param view The view.
    */
   public static void subtractMarginTopEqualStatusBarHeight(Context context,@NonNull View view) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
      Object haveSetOffset = view.getTag(KEY_OFFSET);
      if (haveSetOffset == null || !(Boolean) haveSetOffset) return;
      ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
      layoutParams.setMargins(layoutParams.leftMargin,
              layoutParams.topMargin - getStatusBarHeight(context),
              layoutParams.rightMargin,
              layoutParams.bottomMargin);
      view.setTag(KEY_OFFSET, false);
   }



   private static void addMarginTopEqualStatusBarHeight(Context context, @NonNull final Window window) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
      View withTag = window.getDecorView().findViewWithTag(TAG_OFFSET);
      if (withTag == null) return;
      addMarginTopEqualStatusBarHeight(context,withTag);
   }

   private static void subtractMarginTopEqualStatusBarHeight(Context context,@NonNull final Window window) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
      View withTag = window.getDecorView().findViewWithTag(TAG_OFFSET);
      if (withTag == null) return;
      subtractMarginTopEqualStatusBarHeight(context,withTag);
   }













   private static void hideStatusBarView(@NonNull final Window window) {
      ViewGroup decorView = (ViewGroup) window.getDecorView();
      View fakeStatusBarView = decorView.findViewWithTag(TAG_STATUS_BAR);
      if (fakeStatusBarView == null) return;
      fakeStatusBarView.setVisibility(View.GONE);
   }

   private static void showStatusBarView(@NonNull final Window window) {
      ViewGroup decorView = (ViewGroup) window.getDecorView();
      View fakeStatusBarView = decorView.findViewWithTag(TAG_STATUS_BAR);
      if (fakeStatusBarView == null) return;
      fakeStatusBarView.setVisibility(View.VISIBLE);
   }




} 