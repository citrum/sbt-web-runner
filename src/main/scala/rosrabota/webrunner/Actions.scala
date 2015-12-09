/*
 * Copyright (C) 2009-2012 Johannes Rudolph and Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rosrabota.webrunner

import java.io.File
import java.net.BindException

import rosrabota.webrunner.server.WebServer
import sbt.Keys._
import sbt._

object Actions {
  def startApp(streams: TaskStreams, project: ProjectRef, options: ForkOptions, mainClass: Option[String],
               cp: Classpath, args: Seq[String], fileWatcherThread: FileWatcherThread,
               withJRebel: Boolean, showJRebelMessages: Boolean, restartExitCode: Option[Int]): AppProcess = {
    def onExit(code: Int): Unit = {
      if (restartExitCode.exists(_ == code)) {
        stopApp(streams.log, project, logIfNotStarted = false)
        doStart()
      }
    }
    def doStart(): AppProcess = {
      assert(!revolverState.getProcess(project).exists(_.isRunning))

      // fail early
      val theMainClass = mainClass.getOrElse(sys.error("No main class detected!"))
      val logger = new SysoutLogger("wr", showJRebelMessages)
      streams.log.info(Colors.yellow("Starting application " + project.project + " in the background, ") +
        (if (withJRebel) Colors.green("with JRebel") else Colors.red("no JRebel")))

      val appProcess =
        AppProcess(project, logger, fileWatcherThread, onExit) {
          forkRun(options, theMainClass, cp.map(_.data), args, logger, Nil)
        }
      registerAppProcess(project, appProcess)
      appProcess
    }
    doStart()
  }

  def stopAppWithStreams(streams: TaskStreams, project: ProjectRef) = stopApp(streams.log, project)

  def stopApp(log: Logger, project: ProjectRef, logIfNotStarted: Boolean = true): Unit = {
    revolverState.getProcess(project) match {
      case Some(appProcess) =>
        if (appProcess.isRunning) {
          log.info(Colors.yellow("Stopping application " + appProcess.projectName + " (by killing the forked JVM) ..."))

          appProcess.stop()
        }
      case None =>
        if (logIfNotStarted) log.info(Colors.yellow("Application " + project.project + " not yet started"))
    }
    unregisterAppProcess(project)
  }

  def stopApps(log: Logger): Unit =
    revolverState.runningProjects.foreach(stopApp(log, _))

  def showStatus(streams: TaskStreams, project: ProjectRef): Unit =
    streams.log.info {
      revolverState.getProcess(project).find(_.isRunning) match {
        case Some(appProcess) =>
          Colors.green("Application " + appProcess.projectName + " is currently running")
        case None =>
          Colors.yellow("Application " + project.project + " is currently NOT running")
      }
    }

  def createJRebelAgentOption(log: Logger, path: String): Option[String] = {
    if (!path.trim.isEmpty) {
      val file = new File(path)
      if (!file.exists) {
        log.warn("jrebel: " + path + " not found")
        None
      } else Some("-javaagent:" + path)
    } else None
  }

  def updateState(f: WebRunnerState => WebRunnerState): Unit = GlobalState.update(f)
  def updateStateAndGet[T](f: WebRunnerState => (WebRunnerState, T)): T = GlobalState.updateAndGet(f)
  def revolverState: WebRunnerState = GlobalState.get()

  def registerAppProcess(project: ProjectRef, process: AppProcess) =
    updateState {state =>
      // before we overwrite the process entry we have to make sure the old
      // project is really closed to avoid the unlikely (impossible?) race condition that we
      // have started two processes concurrently but only register the second one
      val oldProcess = state.getProcess(project)
      if (oldProcess.exists(_.isRunning)) oldProcess.get.stop()

      state.addProcess(project, process)
    }

  def unregisterAppProcess(project: ProjectRef) = updateState(_.removeProcessAndColor(project))

  case class ExtraCmdLineOptions(jvmArgs: Seq[String], startArgs: Seq[String])

  def forkRun(config: ForkOptions, mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger, extraJvmArgs: Seq[String]): Process = {
    log.info(options.mkString("Starting " + mainClass + ".main(", ", ", ")"))
    val scalaOptions = "-classpath" :: Path.makeString(classpath) :: mainClass :: options.toList
    val newOptions =
      config.copy(
        outputStrategy = Some(config.outputStrategy getOrElse LoggedOutput(log)),
        runJVMOptions = config.runJVMOptions ++ extraJvmArgs)

    Fork.java.fork(newOptions, scalaOptions)
  }

  var webServer: WebServer = null

  def startWebServer(host: String, port: Int, log: Logger): Unit = {
    if (webServer == null && port != 0) {
      try {
        webServer = new WebServer(host, port)
        webServer.start()
        log.info("WebRunner server started on " + host + ":" + port)
      } catch {
        case e: BindException =>
          log.error("Cannot start WebRunner server on " + host + ":" + port + " because of: " + e.getMessage)
      }
    }
  }

  def stopWebServer(): Unit = {
    if (webServer != null) {
      webServer.stop()
      webServer = null
    }
  }
}
