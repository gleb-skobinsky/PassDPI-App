import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Creates a universal macOS framework by combining Arm64 and X64 binaries with lipo.
 *
 * Example usage:
 *
 * tasks.register<UniversalMacosFrameworkTask>("createUniversalMacosFramework") {
 *     arm64FrameworkDir.set(layout.projectDirectory.dir("composeApp/build/bin/macosArm64/debugFramework/ComposeApp.framework"))
 *     x64FrameworkDir.set(layout.projectDirectory.dir("composeApp/build/bin/macosX64/debugFramework/ComposeApp.framework"))
 *     outputFrameworkDir.set(layout.projectDirectory.dir("composeApp/build/xcode-frameworks/Debug/macos-universal/ComposeApp.framework"))
 * }
 */
@CacheableTask
abstract class UniversalMacosFrameworkTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val arm64FrameworkDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val x64FrameworkDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputFrameworkDir: DirectoryProperty

    override fun getGroup(): String? = "build"

    @Suppress("NewApi")
    @TaskAction
    fun createUniversalFramework() {
        val armDir = arm64FrameworkDir.get().asFile
        val x64Dir = x64FrameworkDir.get().asFile
        val outDir = outputFrameworkDir.get().asFile

        val frameworkName = armDir.name // e.g. ComposeApp.framework
        val binaryName = frameworkName.removeSuffix(".framework") // e.g. ComposeApp

        val armBinary = File(armDir, binaryName)
        val x64Binary = File(x64Dir, binaryName)
        val outBinary = File(outDir, binaryName)

        // Clean and recreate output directory
        if (outDir.exists()) outDir.deleteRecursively()
        outDir.mkdirs()

        // Copy all non-binary contents
        project.copy { copySpec ->
            copySpec.from(armDir)
            copySpec.into(outDir)
            copySpec.exclude(binaryName)
        }

        // Run lipo to merge binaries
        logger.lifecycle("🔧 Combining binaries into universal framework...")
        val process = ProcessBuilder(
            "lipo",
            "-create",
            armBinary.absolutePath,
            x64Binary.absolutePath,
            "-output",
            outBinary.absolutePath
        )
            .redirectErrorStream(true)
            .inheritIO()
            .start()

        val exit = process.waitFor()
        if (exit != 0) {
            throw GradleException("❌ lipo failed with exit code $exit")
        }

        logger.lifecycle("✅ Universal macOS framework created at: ${outDir.absolutePath}")
    }
}
