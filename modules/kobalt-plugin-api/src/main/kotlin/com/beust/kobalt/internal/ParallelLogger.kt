package com.beust.kobalt.internal

import com.beust.kobalt.Args
import com.beust.kobalt.KobaltException
import com.beust.kobalt.misc.kobaltError
import com.beust.kobalt.misc.kobaltLog
import com.beust.kobalt.misc.kobaltWarn
import com.google.inject.Inject
import com.google.inject.Singleton
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

interface ILogger {
    fun log(tag: CharSequence, level: Int, message: CharSequence, newLine: Boolean = true)
}

/**
 * This class manages logs for parallel builds. These logs come from multiple projects interwoven as
 * they are being scheduled on different threads. This class maintains a "current" project which has
 * its logs always displayed instantaneously while logs from other projects are being stored for later display.
 * Once the current project is done, this class will catch up all the finished project logs and then
 * pick the next current project to be displayed live.
 *
 * Yes, this code was pretty painful to write and I'm pretty sure it can be made less ugly.
 */
@Singleton
class ParallelLogger @Inject constructor(val args: Args) : ILogger {
    enum class Type { LOG, WARN, ERROR }

    class LogLine(val name: CharSequence? = null, val level: Int, val message: CharSequence, val type: Type,
            val newLine: Boolean)
    private val logLines = ConcurrentHashMap<CharSequence, ArrayList<LogLine>>()

    private val runningProjects = ConcurrentLinkedQueue<String>()
    var startTime: Long? = null

    fun onProjectStarted(name: String) {
        if (startTime == null) {
            startTime = System.currentTimeMillis()
        }
        runningProjects.add(name)
        logLines[name] = arrayListOf()
        if (currentName == null) {
            currentName = name
        }
    }

    val stoppedProjects = ConcurrentHashMap<String, String>()

    fun onProjectStopped(name: String) {
        debug("onProjectStopped($name)")
        stoppedProjects[name] = name

        if (name == currentName && runningProjects.any()) {
            emptyProjectLog(name)
            var nextProject = runningProjects.peek()
            while (nextProject != null && stoppedProjects.containsKey(nextProject)) {
                val sp = runningProjects.remove()
                emptyProjectLog(sp)
                nextProject = runningProjects.peek()
            }
            currentName = nextProject
        } else {
            debug("Non current project $name stopping, not doing anything")
        }
    }

    private fun debug(s: CharSequence) {
        if (args.log >= 2) {
            val time = System.currentTimeMillis() - startTime!!
            println("                    ### [$time] $s")
        }
    }

    val LOCK = Any()
    var currentName: String? = null
        set(newName) {
            field = newName
        }

    private fun displayLine(ll: LogLine) {
        val time = System.currentTimeMillis() - startTime!!
        val m = (if (args.dev) "### [$time] " else "") + ll.message
        when(ll.type) {
            Type.LOG -> kobaltLog(ll.level, m, ll.newLine)
            Type.WARN -> kobaltWarn(m)
            Type.ERROR -> kobaltError(m)
        }
    }

    private fun emptyProjectLog(name: CharSequence?) {
        val lines = logLines[name]
        if (lines != null && lines.any()) {
            debug("emptyProjectLog($name)")
            lines.forEach {
                displayLine(it)
            }
            lines.clear()
            debug("Done emptyProjectLog($name)")
//            logLines.remove(name)
        } else if (lines == null) {
            throw KobaltException("Didn't call onStartProject() for $name")
        }
    }

    private fun addLogLine(name: CharSequence, ll: LogLine) {
        if (name != currentName) {
            val list = logLines[name] ?: arrayListOf()
            logLines[name] = list
            list.add(ll)
        } else {
            emptyProjectLog(name)
            displayLine(ll)
        }
    }

    override fun log(tag: CharSequence, level: Int, message: CharSequence, newLine: Boolean) {
        if (args.sequential) {
            kobaltLog(level, message, newLine)
        } else {
            addLogLine(tag, LogLine(tag, level, message, Type.LOG, newLine))
        }
    }

    fun shutdown() {
        runningProjects.forEach {
            emptyProjectLog(it)
        }
        println("")
    }
}
