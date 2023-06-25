package com.xiaoniu.viahook

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.luckypray.dexkit.DexKitBridge
import io.luckypray.dexkit.descriptor.member.DexMethodDescriptor
import java.lang.reflect.Method

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam?.packageName == "mark.via") {
            val apkPath = lpparam.appInfo.sourceDir
            XposedHelpers.findClass("mark.via.BrowserApp", lpparam.classLoader)?.let { clazz ->
                XposedHelpers.findAndHookMethod(clazz, "onCreate", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        val context = param!!.thisObject as Context
                        val sp = context.getSharedPreferences("hook", Context.MODE_PRIVATE)
                        var method: Method? = null
                        val version = sp.getLong("version", 0)
                        sp.getString("method", "").let { desc ->
                            if (desc == "" || version != getVersionCode(context)) {
                                System.loadLibrary("dexkit")
                                DexKitBridge.create(apkPath)?.use { bridge ->
                                    val resultMap = bridge.batchFindMethodsUsingStrings {
                                        addQuery("parseWhiteList", setOf("di5xcS5jb20seW91a3UuY29tLGlxaXlpLmNvbSxtZ3R2LmNvbQ=="))
                                    }
                                    resultMap["parseWhiteList"].let {
                                        if (it.isNullOrEmpty()) {
                                            XposedBridge.log("ViaHook: search result empty")
                                            Toast.makeText(context, "ViaHook: search result empty", Toast.LENGTH_LONG).show()
                                        } else {
                                            val classDescriptor = it.first().also { descr ->
                                                XposedBridge.log("ViaHook: Found method: $descr")
                                                sp.edit().apply {
                                                    putLong("version", getVersionCode(context)).apply()
                                                    putString("method", descr.toString()).apply()
                                                }
                                            }
                                            method = classDescriptor.getMethodInstance(lpparam.classLoader)
                                        }
                                    }
                                }
                            } else {
                                val a = desc!!.indexOf("->")
                                val b = desc.indexOf('(', a)
                                val declaringClass = desc.substring(0, a)
                                val name = desc.substring(a + 2, b)
                                val signature = desc.substring(b)
                                val descr = DexMethodDescriptor(declaringClass, name, signature)
                                XposedBridge.log("ViaHook: Load method: $descr")
                                method = descr.getMethodInstance(lpparam.classLoader)
                            }
                        }
                        method?.let {
                            XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(null))
                        }
                    }
                })
            }
        }
    }

    fun getVersionCode(context: Context): Long {
        val packageManager = context.packageManager
        return try {
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode
        } catch (e: NameNotFoundException) {
            -1
        }
    }
}