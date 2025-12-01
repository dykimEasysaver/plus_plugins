package dev.fluttercommunity.plus.device_info

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import kotlin.collections.HashMap
import android.os.StatFs
import android.os.Environment
import java.io.BufferedReader
import java.io.FileReader
import java.io.FileWriter
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.net.NetworkInterface
import java.util.Collections

/**
 * The implementation of [MethodChannel.MethodCallHandler] for the plugin. Responsible for
 * receiving method calls from method channel.
 */
internal class MethodCallHandlerImpl(
    private val packageManager: PackageManager,
    private val activityManager: ActivityManager,
    private val contentResolver: ContentResolver,
) : MethodCallHandler {



    fun getLANMacAddress(): String? {
        val filePath = "/sys/class/net/eth0/address"
        val fileData = StringBuffer()
        val reader: BufferedReader
        try {
            reader = BufferedReader(FileReader(filePath))
            val buf = CharArray(1024)
            var numRead = 0
            while (reader.read(buf).also { numRead = it } != -1) {
                val readData = String(buf, 0, numRead)
                fileData.append(readData)
            }
            reader.close()
            if (fileData.length >= 16)
                return fileData.toString().uppercase(Locale.getDefault()).replace("\n","")

        } catch (e1: FileNotFoundException) {
            e1.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun getDongleMacAddress(): String? {

        try {
            val writer = FileWriter("/sys/class/unifykeys/name")
            writer.write("mac")
            writer.close()

            val reader = BufferedReader(FileReader("/sys/class/unifykeys/read"))
            val fileData = StringBuffer()
            val buf = CharArray(1024)
            var numRead = 0
            while (reader.read(buf).also { numRead = it } != -1) {
                val readData = String(buf, 0, numRead)
                fileData.append(readData)
            }
            reader.close()
            if (fileData.length >= 16)
                return fileData.toString().uppercase(Locale.getDefault())

        } catch (e1: FileNotFoundException) {
            e1.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun getEthernetMacAddress(): String {
        var macAddress = "20:00:00:00:00:00"

        try {
            val allNetworkInterfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())

            for (nif in allNetworkInterfaces) {

                //if (!nif.name.equals("eth0", ignoreCase = true)) continue
                if (!nif.name.equals("eth0", ignoreCase = true)) continue
                if (nif.hardwareAddress == null)
                    continue

                val macBytes: ByteArray = nif.hardwareAddress ?: return macAddress
                val res1 = StringBuilder()
                for (b in macBytes) {
                    res1.append(String.format("%02X:", b))
                }
                if (res1.isNotEmpty()) {
                    res1.deleteCharAt(res1.length - 1)
                }
                macAddress = res1.toString()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }


        if (macAddress == "20:00:00:00:00:00") {
            val lanMacAddress = getLANMacAddress()
            if (lanMacAddress != null)
                return lanMacAddress
        }

        if (macAddress == "20:00:00:00:00:00") {
            val lanMacAddress = getDongleMacAddress()
            if (lanMacAddress != null)
                return lanMacAddress
        }

        return macAddress
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (call.method.equals("getDeviceInfo")) {
            val build: MutableMap<String, Any> = HashMap()

            build["board"] = Build.BOARD
            build["bootloader"] = Build.BOOTLOADER
            build["brand"] = Build.BRAND
            build["device"] = Build.DEVICE
            build["display"] = Build.DISPLAY
            build["fingerprint"] = Build.FINGERPRINT
            build["hardware"] = Build.HARDWARE
            build["host"] = Build.HOST
            build["id"] = Build.ID
            build["manufacturer"] = Build.MANUFACTURER
            build["model"] = Build.MODEL
            build["product"] = Build.PRODUCT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                build["name"] = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) ?: ""
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                build["supported32BitAbis"] = listOf(*Build.SUPPORTED_32_BIT_ABIS)
                build["supported64BitAbis"] = listOf(*Build.SUPPORTED_64_BIT_ABIS)
                build["supportedAbis"] = listOf<String>(*Build.SUPPORTED_ABIS)
            } else {
                build["supported32BitAbis"] = emptyList<String>()
                build["supported64BitAbis"] = emptyList<String>()
                build["supportedAbis"] = emptyList<String>()
            }

            build["tags"] = Build.TAGS
            build["type"] = Build.TYPE
            build["isPhysicalDevice"] = !isEmulator
            build["systemFeatures"] = getSystemFeatures()

            val statFs = StatFs(Environment.getDataDirectory().getPath())
            build["freeDiskSize"] = statFs.getFreeBytes()
            build["totalDiskSize"] = statFs.getTotalBytes()

            val version: MutableMap<String, Any> = HashMap()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                version["baseOS"] = Build.VERSION.BASE_OS
                version["previewSdkInt"] = Build.VERSION.PREVIEW_SDK_INT
                version["securityPatch"] = Build.VERSION.SECURITY_PATCH
            }
            version["codename"] = Build.VERSION.CODENAME
            version["incremental"] = Build.VERSION.INCREMENTAL
            version["release"] = Build.VERSION.RELEASE
            version["sdkInt"] = Build.VERSION.SDK_INT
            build["version"] = version

            val memoryInfo: ActivityManager.MemoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            build["isLowRamDevice"] = memoryInfo.lowMemory
            build["physicalRamSize"] = memoryInfo.totalMem / 1048576L // Mb
            build["availableRamSize"] = memoryInfo.availMem / 1048576L // Mb

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                build["serialNumber"] = try {
                    Build.getSerial()
                } catch (ex: SecurityException) {
                    Build.UNKNOWN
                }
            } else {
                @Suppress("DEPRECATION")
                build["serialNumber"] = Build.SERIAL
            }

            build["macAddress"] = getEthernetMacAddress()

            result.success(build)
        } else {
            result.notImplemented()
        }
    }

    private fun getSystemFeatures(): List<String> {
        val featureInfos: Array<FeatureInfo> = packageManager.systemAvailableFeatures
        return featureInfos
            .filterNot { featureInfo -> featureInfo.name == null }
            .map { featureInfo -> featureInfo.name }
    }

    /**
     * A simple emulator-detection based on the flutter tools detection logic and a couple of legacy
     * detection systems
     */
    private val isEmulator: Boolean
        get() = ((Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.PRODUCT.contains("sdk")
            || Build.PRODUCT.contains("vbox86p")
            || Build.PRODUCT.contains("emulator")
            || Build.PRODUCT.contains("simulator"))
}
