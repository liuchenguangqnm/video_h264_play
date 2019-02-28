package megvii.com.myapplication

import android.app.Application

/**
 * 日期 ---------- 维护人 ------------ 变更内容 --------
 * 2019/2/12        Sunshine          请填写变更内容
 */
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: Application
    }
}
