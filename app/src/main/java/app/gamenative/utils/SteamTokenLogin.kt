package app.gamenative.utils

import android.annotation.SuppressLint
import android.content.Context
import com.auth0.android.jwt.JWT
import com.winlator.container.Container
import com.winlator.contents.ContentProfile
import com.winlator.contents.ContentsManager
import com.winlator.core.FileUtils
import com.winlator.core.ProcessHelper
import com.winlator.core.TarCompressorUtils
import com.winlator.core.envvars.EnvVars
import com.winlator.xconnector.UnixSocketConfig
import com.winlator.xenvironment.ImageFs
import `in`.dragonbra.javasteam.types.KeyValue
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

// This is the key to make config.vdf work
const val NULL_CHAR = '\u0000'
const val TOKEN_EXPIRE_TIME = 86400L // 1 day

class SteamTokenLogin(
    private val context: Context,
    private val steamId: String,
    private val login: String,
    private val token: String,
    private val imageFs: ImageFs,
    private val container: Container,
    private val isArm64EC: Boolean,
    private val wineProfile: ContentProfile?,
) {
    private val winePath: String = if (container.containerVariant == Container.BIONIC) {
        imageFs.winePath + "/bin"
    } else {
        if (wineProfile == null)
            imageFs.getWinePath() + "/bin"
        else
            ContentsManager.getSourceFile(context, wineProfile, wineProfile.wineBinPath).absolutePath
    }

    fun setupSteamFiles() {
        // For loginusers.vdf and reg values
        SteamUtils.autoLoginUserChanges(imageFs = imageFs)
        phase1SteamConfig()
    }

    private fun hdr() : String {
        val crc = CRC32()
        crc.update(login.toByteArray())
        return "${crc.value.toString(16)}1"
    }

    private fun execCommand(command: String) : String {
        val envVars = EnvVars()
        // Common environment variables for both container types
        envVars.put("WINEDEBUG", "-all")
        envVars.put("WINEPREFIX", imageFs.wineprefix)

        if (container.containerVariant == Container.BIONIC) {
            envVars.put("HOME", imageFs.home_path)
            envVars.put("USER", ImageFs.USER)
            envVars.put("TMPDIR", imageFs.rootDir.path + "/tmp")
            envVars.put("DISPLAY", ":0")
            envVars.put("PATH", winePath + ":" + imageFs.rootDir.path + "/usr/bin")
            envVars.put("LD_LIBRARY_PATH", imageFs.rootDir.path + "/usr/lib" + ":" + "/system/lib64")
            envVars.put("FONTCONFIG_PATH", imageFs.rootDir.path + "/usr/etc/fonts")
            envVars.put("XDG_DATA_DIRS", imageFs.rootDir.path + "/usr/share")
            envVars.put("XDG_CONFIG_DIRS", imageFs.rootDir.path + "/usr/etc/xdg")
            envVars.put("ANDROID_SYSVSHM_SERVER", imageFs.rootDir.path + UnixSocketConfig.SYSVSHM_SERVER_PATH)
            envVars.put("PREFIX", imageFs.rootDir.path + "/usr")
            envVars.put("WINE_NO_DUPLICATE_EXPLORER", "1")
            envVars.put("WINE_DISABLE_FULLSCREEN_HACK", "1")

            if (!isArm64EC) {
                // Set execute permissions.
                val box64File: File = File(imageFs.rootDir, "usr/bin/box64")
                if (box64File.exists()) {
                    FileUtils.chmod(box64File, 493) // 0755
                }
            }
        } else {
            if (!File(imageFs.rootDir.path, "/usr/local/bin/box64").exists()) {
                // Extract box64 if it doesn't exist yet
                TarCompressorUtils.extract(
                    TarCompressorUtils.Type.ZSTD,
                    context.assets,
                    "box86_64/box64-" + container.box64Version + ".tzst",
                    imageFs.rootDir,
                )
            }

            envVars.put("HOME", imageFs.home_path)
            envVars.put("USER", ImageFs.USER)
            envVars.put("TMPDIR", imageFs.rootDir.path + "/tmp")
            envVars.put("DISPLAY", ":0")
            envVars.put(
                "PATH",
                winePath + ":" +
                        imageFs.rootDir.path + "/usr/bin:" +
                        imageFs.rootDir.path + "/usr/local/bin",
            )
            envVars.put("LD_LIBRARY_PATH", imageFs.rootDir.path + "/usr/lib")
            envVars.put("BOX64_LD_LIBRARY_PATH", imageFs.rootDir.path + "/usr/lib/x86_64-linux-gnu")
            envVars.put("ANDROID_SYSVSHM_SERVER", imageFs.rootDir.path + UnixSocketConfig.SYSVSHM_SERVER_PATH)
            envVars.put("FONTCONFIG_PATH", imageFs.rootDir.path + "/usr/etc/fonts")

            if (File(imageFs.glibc64Dir, "libandroid-sysvshm.so").exists() ||
                File(imageFs.glibc32Dir, "libandroid-sysvshm.so").exists()) {
                envVars.put("LD_PRELOAD", "libredirect.so libandroid-sysvshm.so")
            }

            envVars.put("WINEESYNC_WINLATOR", "1")
        }

        val finalCommand = if (container.containerVariant == Container.BIONIC) {
            if (isArm64EC) {
                winePath + "/" + command
            } else {
                imageFs.binDir.path + "/box64" + " " + command
            }
        } else {
            imageFs.rootDir.path + "/usr/local/bin/box64" + " " + command
        }

        Timber.tag("SteamTokenLogin").d("Executing: " + ProcessHelper.splitCommand(finalCommand).contentToString() + ", " + envVars.toStringArray().contentToString() + ", " + imageFs.rootDir)

        val process = Runtime.getRuntime().exec(finalCommand, envVars.toStringArray(), imageFs.rootDir)
        val reader = BufferedReader(process.inputStream.reader())
        val errorReader = BufferedReader(process.errorStream.reader())

        val output = StringBuilder()
        val errorOutput = StringBuilder()

        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            output.append(line).append("\n")
        }

        var errorLine: String?
        while ((errorReader.readLine().also { errorLine = it }) != null) {
            errorOutput.append(errorLine).append("\n")
        }

        val exitCode = process.waitFor()

        // Check if command succeeded
        if (exitCode != 0) {
            throw RuntimeException("Command execution failed with exit code: $exitCode. Output: $output, Error Output: $errorOutput")
        }

        // Filter out Wine debug messages and get the actual result
        return output.lines()
            .joinToString("\n")
            .trim()
    }

    private fun killWineServer() {
        try {
            execCommand("wineserver -k")
        } catch (e: Exception) {
            Timber.tag("SteamTokenLogin").e("Failed to kill wineserver: ${e.message}")
        }
    }

    private fun encryptToken(token: String) : String {
        // Simple encoding (not as secure as Windows CryptProtectData, but cross-platform)
        // run steam-token.exe from extractDir and return the result
        return execCommand("wine ${imageFs.rootDir}/opt/apps/steam-token.exe encrypt $login $token")
    }

    private fun decryptToken(vdfValue: String) : String {
        // Simple decoding (not as secure as Windows CryptProtectData, but cross-platform)
        // run steam-token.exe from extractDir and return the result
        return execCommand("wine ${imageFs.rootDir}/opt/apps/steam-token.exe decrypt $login $vdfValue")
    }

    private fun obfuscateToken(value: String, mtbf: Long) : String {
        return SteamTokenHelper.obfuscate(value.toByteArray(), mtbf)
    }

    private fun deobfuscateToken(value: String, mtbf: Long) : String {
        return SteamTokenHelper.deobfuscate(value, mtbf)
    }

    @SuppressLint("BinaryOperationInTimber")
    private fun createConfigVdf(): String {
        // Simple hash-based encryption for cross-platform compatibility
        val hdr = hdr()

        // e.g. 1329969238
        val minMTBF = 1000000000L
        val maxMTBF = 2000000000L
        var mtbf = kotlin.random.Random.nextLong(minMTBF, maxMTBF)
        var encoded = ""

        // Try to encode the token until it get a value
        do {
            try {
                encoded = obfuscateToken("$token$NULL_CHAR", mtbf)
            } catch (_: Exception) {
                mtbf = kotlin.random.Random.nextLong(minMTBF, maxMTBF)
            }
        } while (encoded == "")

        Timber.tag("SteamTokenLogin").d("MTBF: $mtbf")
        Timber.tag("SteamTokenLogin").d("Encoded: $encoded")

        return """
            "InstallConfigStore"
            {
                "Software"
                {
                    "Valve"
                    {
                        "Steam"
                        {
                            "MTBF"		"$mtbf"
                            "ConnectCache"
                            {
                                "$hdr"		"$encoded$NULL_CHAR"
                            }
                            "Accounts"
                            {
                                "$login"
                                {
                                    "SteamID"		"$steamId"
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
    }

    @SuppressLint("BinaryOperationInTimber")
    private fun createLocalVdf(): String {
        // Simple hash-based encryption for cross-platform compatibility
        val hdr = hdr()
        val encoded = encryptToken(token)

        return """
            "MachineUserConfigStore"
            {
                "Software"
                {
                    "Valve"
                    {
                        "Steam"
                        {
                            "ConnectCache"
                            {
                                "$hdr"		"$encoded"
                            }
                        }
                    }
                }
            }
        """.trimIndent()
    }

    /**
     * Phase 1 Steam Config
     * Write config.vdf
     */
    fun phase1SteamConfig() {
        val steamConfigDir = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam/config").toPath()
        Files.createDirectories(steamConfigDir)

        // Check if config.vdf not exists, or its contents does not contains MTBF
        val configVdfPath = steamConfigDir.resolve("config.vdf")

        var shouldWriteConfig = true
        var shouldProcessPhase2 = false

        if (Files.exists(configVdfPath)) {
            val vdfContent = FileUtils.readString(configVdfPath.toFile())
            if (vdfContent.contains("ConnectCache")) {
                // Find the value of ConnectCache
                // Use structured parsing:
                val vdfData = KeyValue.loadFromString(vdfContent)!!
                val mtbf = vdfData["Software"]["Valve"]["Steam"]["MTBF"].value
                val connectCacheValue = vdfData["Software"]["Valve"]["Steam"]["ConnectCache"][hdr()].value

                if (mtbf != null && connectCacheValue != null) {
                    try {
                        val dToken = deobfuscateToken(connectCacheValue.trimEnd(NULL_CHAR), mtbf.toLong()).trimEnd(NULL_CHAR)
                        if (JWT(dToken).isExpired(TOKEN_EXPIRE_TIME)) {
                            Timber.tag("SteamTokenLogin").d("Saved JWT expired, overriding config.vdf")
                            // If the saved JWT is expired, override it
                            shouldWriteConfig = true
                        } else {
                            Timber.tag("SteamTokenLogin").d("Saved JWT is not expired, do not override config.vdf")
                            shouldWriteConfig = false
                        }
                    } catch (_: Exception) {
                        Timber.tag("SteamTokenLogin").d("Cannot parse saved JWT, overriding config.vdf")
                        shouldWriteConfig = true
                    }
                } else {
                    if (mtbf == null && connectCacheValue == null) {
                        Timber.tag("SteamTokenLogin").d("MTBF and ConnectCache not found, overriding config.vdf")
                        shouldWriteConfig = true
                    } else if (mtbf != null) {
                        Timber.tag("SteamTokenLogin").d("MTBF exists but ConnectCache not found, it is an updated steam client, processing phase 2")
                        shouldWriteConfig = false
                        shouldProcessPhase2 = true
                    }
                }
            } else if (vdfContent.contains("MTBF")) {
                Timber.tag("SteamTokenLogin").d("MTBF exists but ConnectCache not found, it is an updated steam client, processing phase 2")
                shouldWriteConfig = false
                shouldProcessPhase2 = true
            }
        }

        if (shouldWriteConfig) {
            Timber.tag("SteamTokenLogin").d("Overriding config.vdf")

            Files.write(
                steamConfigDir.resolve("config.vdf"),
                createConfigVdf().toByteArray(),
            )

            // Set permissions
            FileUtils.chmod(File(steamConfigDir.absolutePathString(), "loginusers.vdf"), 505) // 0771
            FileUtils.chmod(File(steamConfigDir.absolutePathString(), "config.vdf"), 505) // 0771

            // Remove local.vdf
            val localSteamDir = File(imageFs.wineprefix, "drive_c/users/${ImageFs.USER}/AppData/Local/Steam").toPath()
            localSteamDir.createDirectories()

            if (localSteamDir.resolve("local.vdf").exists()) {
                Files.delete(localSteamDir.resolve("local.vdf"))
            }
        } else if (shouldProcessPhase2) {
            phase2LocalConfig()
        }
    }

    /**
     * Phase 2 Local Config
     * Refresh local.vdf value
     */
    fun phase2LocalConfig() {
        try {
            // Extract steam-token.tzst
            val extractDir = File(imageFs.rootDir, "/opt/apps/")
            Files.createDirectories(extractDir.toPath())
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, File(imageFs.filesDir, "steam-token.tzst"), extractDir)

            val localSteamDir = File(imageFs.wineprefix, "drive_c/users/${ImageFs.USER}/AppData/Local/Steam").toPath()
            Files.createDirectories(localSteamDir)

            // Remove local.vdf
            if (localSteamDir.resolve("local.vdf").exists()) {
                val vdfContent = FileUtils.readString(localSteamDir.resolve("local.vdf").toFile())
                val vdfData = KeyValue.loadFromString(vdfContent)!!
                val connectCacheValue = vdfData["Software"]["Valve"]["Steam"]["ConnectCache"][hdr()].value
                if (connectCacheValue != null) {
                    try {
                        val dToken = decryptToken(connectCacheValue.trimEnd(NULL_CHAR))
                        val savedJWT = JWT(dToken)

                        // If the saved JWT is not expired, do not override it
                        if (!savedJWT.isExpired(TOKEN_EXPIRE_TIME)) {
                            Timber.tag("SteamTokenLogin").d("Saved JWT is not expired, do not override local.vdf")
                            return
                        }
                    } catch (e: Exception) {
                        Timber.tag("SteamTokenLogin").d("An unexpected error occurred: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            Timber.tag("SteamTokenLogin").d("Overriding local.vdf")

            Files.write(
                localSteamDir.resolve("local.vdf"),
                createLocalVdf().toByteArray(),
            )

            killWineServer()

            // Set permissions
            FileUtils.chmod(File(localSteamDir.absolutePathString(), "local.vdf"), 505) // 0771
        } catch (e: Exception) {
            Timber.tag("SteamTokenLogin").d("An unexpected error occurred: ${e.message}")
            e.printStackTrace()
        }
    }
}
