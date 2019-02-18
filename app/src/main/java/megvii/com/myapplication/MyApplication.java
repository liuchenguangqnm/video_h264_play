package megvii.com.myapplication;

import android.app.Application;

/**
 * 日期 ---------- 维护人 ------------ 变更内容 --------
 * 2019/2/12        Sunshine          请填写变更内容
 */
public class MyApplication extends Application {
    public static Application instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
}
