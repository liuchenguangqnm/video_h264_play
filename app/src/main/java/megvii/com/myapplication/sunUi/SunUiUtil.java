package megvii.com.myapplication.sunUi;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import megvii.com.myapplication.MainActivity;
import megvii.com.myapplication.MyApplication;

/**
 * 日期 ---------- 维护人 ------------ 变更内容 --------
 * 2018/11/13     Sunshine          UI适配工具类
 */
public class SunUiUtil {
    private static Context context = MyApplication.instance.getApplicationContext();
    private static float systemDensity = context.getResources().getDisplayMetrics().density;
    private static int systemDensityDpi = context.getResources().getDisplayMetrics().densityDpi;
    private static float systemScaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
    private static float realDensity;
    private static int realDensityDpi;
    private static float realScaledDensity;
    public static float densityRate = 0;

    static {
        // 注册系统字体变化监听
        MyApplication.instance.registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {

                if (newConfig != null && newConfig.fontScale > 0) {
                    systemScaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
                    // 重新获取真实的 density 和 scaledDensity
                    realDensity = initRealDensity();
                    realScaledDensity = realDensity * (systemScaledDensity / systemDensity);
                }
            }

            @Override
            public void onLowMemory() {

            }
        });
        // 获取真实的 density 和 scaledDensity
        realDensity = initRealDensity();
        realScaledDensity = realDensity * (systemScaledDensity / systemDensity);
    }

    public static void fixLayout(MainActivity mainActivity) {
        applicationDensityFix();
        DisplayMetrics activityDisplayMetrics = mainActivity.getResources().getDisplayMetrics();
        if (realDensity != activityDisplayMetrics.density ||
                realDensityDpi != activityDisplayMetrics.densityDpi ||
                realScaledDensity != activityDisplayMetrics.scaledDensity) {
            activityDisplayMetrics.density = realDensity;
            activityDisplayMetrics.densityDpi = realDensityDpi;
            activityDisplayMetrics.scaledDensity = realScaledDensity;
        }
    }

    private static void applicationDensityFix() {
        DisplayMetrics appDisplayMetrics = context.getResources().getDisplayMetrics();
        if (realDensity != appDisplayMetrics.density ||
                realDensityDpi != appDisplayMetrics.densityDpi ||
                realScaledDensity != appDisplayMetrics.scaledDensity) {
            if (densityRate == 0)
                densityRate = appDisplayMetrics.density / realDensity;
            appDisplayMetrics.density = realDensity;
            appDisplayMetrics.densityDpi = realDensityDpi;
            appDisplayMetrics.scaledDensity = realScaledDensity;
        }
    }

    /**
     * 获得实际的近似像素密度比
     */
    private static float initRealDensity() {
        float density; // 第一个判断 +200 是对最大屏幕的手机留一个适配的余地
        if (getScreenWidth() >= 1080 && getScreenWidth() < (2160 + 200) && getScreenHeight() >= 1920 && getScreenHeight() < (3840 + 200)) {
            float rate = getScreenWidth() / 1080f;
            density = 3f * rate;
            realDensityDpi = (int) (systemDensityDpi / systemDensity * density + .5);
        } else if (getScreenWidth() >= 720 && getScreenWidth() < 1080 && getScreenHeight() >= 1280 && getScreenHeight() < 1920) {
            float rate = getScreenWidth() / 720f;
            density = 2f * rate;
            realDensityDpi = (int) (systemDensityDpi / systemDensity * density + .5);
        } else if (getScreenWidth() >= 480 && getScreenWidth() < 720 && getScreenHeight() >= 800 && getScreenHeight() < 1280) {
            float rate = getScreenWidth() / 480f;
            density = 1.5f * rate;
            realDensityDpi = (int) (systemDensityDpi / systemDensity * density + .5);
        } else if (getScreenWidth() >= 320 && getScreenWidth() < 480 && getScreenHeight() >= 480 && getScreenHeight() < 800) {
            float rate = getScreenWidth() / 320f;
            density = 1f * rate;
            realDensityDpi = (int) (systemDensityDpi / systemDensity * density + .5);
        } else if (getScreenWidth() >= 240 && getScreenWidth() < 320 && getScreenHeight() >= 320 && getScreenHeight() < 480) {
            float rate = getScreenWidth() / 240f;
            density = 0.75f * rate;
            realDensityDpi = (int) (systemDensityDpi / systemDensity * density + .5);
        } else {
            density = context.getResources().getDisplayMetrics().density;
            realDensityDpi = systemDensityDpi;
        }
        return density;
    }

    /**
     * 获取屏幕的高度
     */
    private static int getScreenHeight() {
        // WindowManager windowManager = (WindowManager) context.getSystemService(context.WINDOW_SERVICE);
        // return windowManager.getDefaultDisplay().getHeight();
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.heightPixels;
    }

    /**
     * 获取屏幕的宽度
     */
    private static int getScreenWidth() {
        // WindowManager windowManager = (WindowManager) context.getSystemService(context.WINDOW_SERVICE);
        // return windowManager.getDefaultDisplay().getWidth();
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.widthPixels;
    }

}
