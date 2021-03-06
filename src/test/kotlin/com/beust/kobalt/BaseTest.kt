package com.beust.kobalt

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.app.BuildFileCompiler
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.internal.KobaltPluginXml
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.internal.build.BuildFile
import org.testng.annotations.BeforeClass
import java.io.File
import java.nio.file.Paths

open class BaseTest {
    @BeforeClass
    fun bc() {
        Kobalt.init(TestModule())
    }

    fun compileBuildFile(buildFileText: String, args: Args, compilerFactory: BuildFileCompiler.IFactory)
            : BuildFileCompiler.FindProjectResult {
        val tmpBuildFile = File.createTempFile("kobaltTest", "").apply {
            deleteOnExit()
            writeText(buildFileText)
        }
        val thisBuildFile = BuildFile(Paths.get(tmpBuildFile.absolutePath), "Build.kt")
        args.buildFile = tmpBuildFile.absolutePath
        val jvmCompilerPlugin = Kobalt.findPlugin("JvmCompiler") as JvmCompilerPlugin
        val pluginInfo = PluginInfo(KobaltPluginXml(), null, null).apply {
            projectContributors.add(jvmCompilerPlugin)
        }
        return compilerFactory.create(listOf(thisBuildFile), pluginInfo).compileBuildFiles(args)
    }
}