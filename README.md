## 开发环境
+ Android Studio 3.5
+ 微信 7.0.6

## 准备工作
> 在 Android Studio 中新建项目这个直接略过。

1. 添加 **xposed** 依赖。

 在 app 下的 build.gradle 中添加以下依赖

  ~~~
  compileOnly 'de.robv.android.xposed:api:82'
  compileOnly 'de.robv.android.xposed:api:82:sources'
  ~~~
2. 配置清单文件。

 在 AndroidManifest.xml 中的 <application> 中添加以下内容
 
  ~~~
   <meta-data
            android:name="xposedmodule"
            android:value="true" />

   <meta-data
            android:name="xposeddescription"
            android:value="微信自动回复模块。" />

   <meta-data
            android:name="xposedminversion"
            android:value="54" />
  ~~~

3. 编写主Hook类。

 ~~~
 package com.wanzi.wechatrobot

 import de.robv.android.xposed.IXposedHookLoadPackage
 import de.robv.android.xposed.callbacks.XC_LoadPackage

 class MainHook :IXposedHookLoadPackage{
    
        override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {

        }
}
 ~~~


4. 添加模块入口。

 在 main 包下新建文件夹 assets ，并新建文件 xposed_init ，写入以下内容

 ~~~
 com.wanzi.wechatrobot.MainHook
 ~~~
 > 这里的内容是主Hook类的地址。

## 开始
> 这里主要分为两步，第一步是拦截微信数据库消息，第二步是调用微信方法发送消息。

### 拦截微信数据库

 微信使用的数据库是他们自家的开源数据库 [WCDB](https://github.com/Tencent/wcdb)，所以我们只需要去看一下他们的[api](https://tencent.github.io/wcdb/references/android/reference/com/tencent/wcdb/database/SQLiteDatabase.html)，找出 **插入数据** 的方法，然后通过 hook 这个方法，就可以获取到我们需要的数据。
 
 通过查看 api 和了解一些 SQL 常识，我们可以大概判断插入数据是这个方法[insert](https://tencent.github.io/wcdb/references/android/reference/com/tencent/wcdb/database/SQLiteDatabase.html#insert(java.lang.String,%20java.lang.String,%20android.content.ContentValues)),下面我们就先 hook 下这个方法看看。

 ~~~
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
                }
            }
        )
    }
}
 ~~~

 好了，写完以后，运行一下项目，然后在 Xposed 模块中启用我们的模块。

 最后让其他人给我们的微信发一条消息，我们可以在 Android Sutdio 的 Logcat 中发现我们成功的拦截到了接收到的消息，

 ![日志1](http://ww1.sinaimg.cn/large/c43dae63gy1g6mctimgs8j21ct00ut8o.jpg)
 我们需要的数据就在 values 中。
 
 好了，拦截微信数据库已经成功，下面就是我们就要调用微信方法发送消息。

### 调用微信方法发送消息

 要调用微信方法发送消息，首先就得要知道微信是调用哪些方法来发送的，这里我们用 Android SDK 自带的 Android Device Monitor 来调试分析。

#### 开始调试
 ![monitor1](http://ww1.sinaimg.cn/large/c43dae63gy1g6md174gyqj21hc0t4win.jpg)
 ![monitor2](http://ww1.sinaimg.cn/large/c43dae63gy1g6md2xqx9tj21hc0t4tdg.jpg)
 点 **OK** ，然后在微信聊天页面随便发送一条消息。
 ![monitor3](http://ww1.sinaimg.cn/large/c43dae63gy1g6md5jxiscj21hc0t4q77.jpg)
 发送消息以后，再点圈中的按钮。
 ![monitor4](http://ww1.sinaimg.cn/large/c43dae63gy1g6mdaw7srvj21hc0t47b8.jpg)
 这样就会生成分析文件。

 既然是点击事件，肯定和 click 有关，所以我们可以先搜索 click
 ![monitor5](http://ww1.sinaimg.cn/large/c43dae63gy1g6me1g05moj212t0os77p.jpg)
 然后一步步往下找

 ![monitor6](http://ww1.sinaimg.cn/large/c43dae63gy1g6me2zyat0j20j0047wei.jpg)

 果然没错，走到了微信的点击事件，接着再往下走
 ![monitor7](http://ww1.sinaimg.cn/large/c43dae63gy1g6me4lpd3uj20kz061t8z.jpg)
 
 我们发现，一共调用了4个方法，这4个方法中，只有 `TQ` 最让我们起疑，为什么呢？大家看一下， `TQ` 的参数是字符串，这个字符串会不会就是消息内容呢？返回的是 Boolean ，会不会就是消息是否发送成功呢？我们来 Hook 一下试试。
 
~~~
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
~~~
运行一下代码，然后我们发送一条微信消息，看下 Logcat 日志：
![日志2](http://ww1.sinaimg.cn/large/c43dae63gy1g6mehbqhdcj20de00pa9v.jpg)

哈哈，果然就是它了，我们再往下找。
![monitor8](http://ww1.sinaimg.cn/large/c43dae63gy1g6mes9vo7rj20ja0480st.jpg)

可以看到，调用了2个方法，这两个方法里面，只有 `avz ` 看起来最可疑，我们继续往下追踪
![monitor9](http://ww1.sinaimg.cn/large/c43dae63gy1g6meufkzucj20fe03oq2x.jpg)

这里看到，只调用了1个方法，参数是一个字符串，一个整数，返回值还是一个 Boolean，这个字符串应该还是消息内容，我们想一想，消息内容有了，是不是还缺少一个消息接收者？下面我们通过分析下微信源码来找找消息接受者在哪里。

#### 分析微信源码
通过反编译微信，我们可以得到微信源码。
> 我这里使用的是 [jadx](https://github.com/skylot/jadx) 来反编译的。

分析 `chatting.c.ai.eS`

~~~
    private boolean eS(String str, final int i) {
        int i2 = 0;
        AppMethodBeat.i(31684);
        final String arL = bo.arL(str);
        if (arL == null || arL.length() == 0) {
            ab.e("MicroMsg.ChattingUI.SendTextComponent", "doSendMessage null");
            AppMethodBeat.o(31684);
            return false;
        }
        this.Aro.avn(arL);
        bz bzVar = new bz();
        bzVar.cIT.cIV = arL;
        bzVar.cIT.context = this.ctY.AsT.getContext();
        bzVar.cIT.username = this.ctY.getTalkerUserName();
        com.tencent.mm.sdk.b.a.yVI.l(bzVar);
        if (bzVar.cIU.cIW) {
            AppMethodBeat.o(31684);
            return true;
        }
        boolean z = WXHardCoderJNI.hcSendMsgEnable;
        int i3 = WXHardCoderJNI.hcSendMsgDelay;
        int i4 = WXHardCoderJNI.hcSendMsgCPU;
        int i5 = WXHardCoderJNI.hcSendMsgIO;
        if (WXHardCoderJNI.hcSendMsgThr) {
            i2 = g.We().dAB();
        }
        this.Arp = WXHardCoderJNI.startPerformance(z, i3, i4, i5, i2, WXHardCoderJNI.hcSendMsgTimeout, 202, WXHardCoderJNI.hcSendMsgAction, "MicroMsg.ChattingUI.SendTextComponent");
        com.tencent.mm.ui.chatting.d.a.dRn().post(new Runnable() {
            public final void run() {
                String str;
                AppMethodBeat.i(31681);
                if (ai.this.ctY == null) {
                    ab.w("MicroMsg.ChattingUI.SendTextComponent", "NULL == mChattingContext");
                    AppMethodBeat.o(31681);
                    return;
                }
                com.tencent.mm.plugin.report.service.g.DG(20);
                if (ai.a(ai.this)) {
                    ai.this.ctY.dRi();
                    aw.Vs().a((m) new com.tencent.mm.ar.a(ai.this.ctY.uhw.field_username, arL), 0);
                    AppMethodBeat.o(31681);
                    return;
                }
                if (((h) ai.this.ctY.aU(h.class)).getCount() == 0 && com.tencent.mm.storage.ad.asF(ai.this.ctY.getTalkerUserName())) {
                    bv.afx().c(10076, Integer.valueOf(1));
                }
                String talkerUserName = ai.this.ctY.getTalkerUserName();
                int px = t.px(talkerUserName);
                String str2 = arL;
                String str3 = null;
                try {
                    str3 = ((com.tencent.mm.ui.chatting.c.b.t) ai.this.ctY.aU(com.tencent.mm.ui.chatting.c.b.t.class)).avx(talkerUserName);
                } catch (NullPointerException e2) {
                    ab.printErrStackTrace("MicroMsg.ChattingUI.SendTextComponent", e2, "", new Object[0]);
                }
                if (bo.isNullOrNil(str3)) {
                    ab.w("MicroMsg.ChattingUI.SendTextComponent", "tempUser is null");
                    AppMethodBeat.o(31681);
                    return;
                }
                o oVar = (o) ai.this.ctY.aU(o.class);
                int lastIndexOf = str2.lastIndexOf(8197);
                if (lastIndexOf <= 0 || lastIndexOf != str2.length() - 1) {
                    str = str2;
                } else {
                    str = str2.substring(0, lastIndexOf);
                    ab.w("MicroMsg.ChattingUI.SendTextComponent", "delete @ last char! index:".concat(String.valueOf(lastIndexOf)));
                }
                ChatFooter dPL = oVar.dPL();
                int i = i;
                int i2 = dPL.wFF.wHB.containsKey(talkerUserName) ? ((LinkedList) dPL.wFF.wHB.get(talkerUserName)).size() > 0 ? 291 : i : i;
                com.tencent.mm.modelmulti.h hVar = new com.tencent.mm.modelmulti.h(str3, str, px, i2, oVar.dPL().ii(talkerUserName, str2));
                ((com.tencent.mm.ui.chatting.c.b.t) ai.this.ctY.aU(com.tencent.mm.ui.chatting.c.b.t.class)).g(hVar);
                aw.Vs().a((m) hVar, 0);
                if (t.pt(talkerUserName)) {
                    aw.Vs().a((m) new j(q.PZ(), arL + " key " + bs.dGp() + " local key " + bs.dGo() + "NetType:" + at.getNetTypeString(ai.this.ctY.AsT.getContext().getApplicationContext()) + " hasNeon: " + n.PF() + " isArmv6: " + n.PH() + " isArmv7: " + n.PG()), 0);
                }
                AppMethodBeat.o(31681);
            }
        });
        this.ctY.ru(true);
        AppMethodBeat.o(31684);
        return true;
    }

~~~

这里我们发现，传入的 str 被处理了一下，变成了 `arL `，接下来我们看下调用 `arL` 的地方，第一个是在：
![源码1](http://ww1.sinaimg.cn/large/c43dae63gy1g6mfw9s640j20fq0b2jrv.jpg)

我们点进去看看

![源码2](http://ww1.sinaimg.cn/large/c43dae63gy1g6mfxfthqwj207x05nweg.jpg)

传入的 str 在这里被使用了，我们追踪进 `setContent` 看看

![源码3](http://ww1.sinaimg.cn/large/c43dae63gy1g6mg2czcn3j20c50odt9i.jpg)

这个类是 `com.tencent.mm.g.c.dd` ,我们发现这里不仅有 `field_content`，还有 `field_talker` ，刚才我们在调试的时候，只找到了消息内容，还缺少一个消息接收者，那这个 `kX` 方法传入的是不是就是消息接收者呢？我们来 hook 下这个方法试试。

```
XposedHelpers.findAndHookMethod(
            "com.tencent.mm.g.c.dd",
            lpparam.classLoader,
            "kX",
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    val str = param?.args?.get(0) as String
                    Log.i("Wanzi", "拦截kX str:$str")
                }
            }
        )
```

继续运行项目，然后用我们的微信发送一条消息，接着看下 Logcat

![日志3](http://ww1.sinaimg.cn/large/c43dae63gy1g6mgbexgf7j20dz01a745.jpg)

果然，这个 `kX` 传入的就是消息接受者。

这下消息内容有了，消息接受者也有了，那剩下的就是在哪里一起使用他们，我们来打印下 `kX` 调用堆栈信息看看。

```
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
```

用微信发送一条消息后，接着看下 Logcat

![日志4](http://ww1.sinaimg.cn/large/c43dae63gy1g6mghxac2hj20hn0asaba.jpg)

看一下，这里最可疑的应该就是这个 `com.tencent.mm.modelmulti.h.<init>` 了，我们来看下这个类的构造函数

![源码4](http://ww1.sinaimg.cn/large/c43dae63gy1g6mgktp77wj20js0gtjsh.jpg)

果然调用了 `kX`、`setContent`，应该就是它了。我们发现这个类一共有4个构造函数:
 ![源码5](http://ww1.sinaimg.cn/large/c43dae63gy1g6mgps7gycj20dn02ymx3.jpg)
 ![源码6](http://ww1.sinaimg.cn/large/c43dae63gy1g6mgqjmfh1j20jb04lt8u.jpg)
 ![源码7](http://ww1.sinaimg.cn/large/c43dae63gy1g6mgr7bxrlj20ir0h1wfg.jpg)
 ![源码8](http://ww1.sinaimg.cn/large/c43dae63gy1g6mgs5k3vwj20ib0gnt9p.jpg)

一共是4个:

+ 第一个无参（pass）
+ 第二个传入的是 `local id`（pass）
+ 第三个传入两个字符串一个整数，通过分析，我们得知第一个字符串是消息接收者，第二个字符串是消息内容，第三个整数是消息类型（这个可以参考我之前写过的[WeChatRecord](https://github.com/WANZIzZ/WeChatRecord/blob/master/app/src/main/java/com/wanzi/wechatrecord/CoreService.kt)）
+ 第四个和第三个差别不大

我们现在有了消息类，是不是还差怎么把消息发出去？接着回到 `chatting.c.ai.eS` 来，刚才我们就是从这里开始分析源码的。

现在我们已经知道了要发送消息，肯定会用到 `com.tencent.mm.modelmulti.h` ，那我们就看下，`eS` 方法里面哪块调用了 `com.tencent.mm.modelmulti.h`

![源码5](http://ww1.sinaimg.cn/large/c43dae63gy1g6mh4wktgdj20ug099aar.jpg)

最后调用 `hVar` 的是这里，我们大胆的猜想一下，是不是就是通过这里来发送微信消息的？来，先看下源码

![源码6](http://ww1.sinaimg.cn/large/c43dae63gy1g6mh9fe4h6j206900pgld.jpg)

![源码7](http://ww1.sinaimg.cn/large/c43dae63gy1g6mh9rivpwj20e003nzk7.jpg)

![源码8](http://ww1.sinaimg.cn/large/c43dae63gy1g6mha3wgo2j20bp08rmxc.jpg)

接着我们照着微信的调用步骤，代码走起

```
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
```
这里我们选择在接收到消息的时候，把消息内容再发回去，再次运行代码，然后让别人给我们发一条消息试试
![截图1](http://ww1.sinaimg.cn/large/c43dae63gy1g6mhikrqgjj20u01o07ag.jpg)

哈哈哈，成功啦！
