package com.wanzi.wechatrobot

import android.content.ContentValues
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 *     author : WZ
 *     e-mail : 1253437499@qq.com
 *     time   : 2019/09/03
 *     desc   :
 *     version: 1.0
 */
class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        // 只关心微信包名
        if (lpparam?.packageName != "com.tencent.mm") {
            return
        }

        XposedHelpers.findAndHookMethod(
            "com.tencent.wcdb.database.SQLiteDatabase",
            lpparam.classLoader,
            "insert",
            String::class.java, // table
            String::class.java, // nullColumnHack
            ContentValues::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    val table = param?.args?.get(0) as String
                    val values = param.args?.get(2) as ContentValues
                    // https://github.com/WANZIzZ/WeChatRecord/blob/master/app/src/main/java/com/wanzi/wechatrecord/CoreService.kt
                    // 这个是我以前写过的一个破解微信数据库的代码，从这个里面我们可以知道 message 表就是聊天记录表，现在我们只关心这个表
                    if (table != "message") {
                        return
                    }
                    Log.i("Wanzi", "拦截微信数据库 values:${values}")
                    val talker = values.getAsString("talker")
                    val content = values.getAsString("content")
                    val clz_h = XposedHelpers.findClass("com.tencent.mm.modelmulti.h", lpparam.classLoader)
                    val message = XposedHelpers.newInstance(clz_h, talker, content, 1)
                    val clz_aw = XposedHelpers.findClass("com.tencent.mm.model.aw", lpparam.classLoader)
                    val clz_p = XposedHelpers.callStaticMethod(clz_aw, "Vs")
                    XposedHelpers.callMethod(clz_p,"a",message,0)
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            "com.tencent.mm.ui.chatting.p",
            lpparam.classLoader,
            "TQ",
            String::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    val str = param?.args?.get(0) as String
                    Log.i("Wanzi", "拦截TQ str:$str 结果:${param.result}")
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            "com.tencent.mm.g.c.dd",
            lpparam.classLoader,
            "kX",
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    val str = param?.args?.get(0) as String
                    Log.i("Wanzi", "拦截kX str:$str")
                    Log.i("Wanzi", "打印堆栈\n${Log.getStackTraceString(Throwable())}")
                }
            }
        )
    }
}