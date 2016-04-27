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

import java.lang.Thread.UncaughtExceptionHandler
import java.lang.reflect.Field
import java.lang.{Runtime => JRuntime}

import sbt.{Logger, Process, ProjectRef}

/**
 * A token which we put into the SBT state to hold the Process of an application running in the background.
 */
case class AppProcess(projectRef: ProjectRef, log: Logger, fileWatcherThread: FileWatcherThread, onExit: (Int) => Any)(process: Process) {
  if (!fileWatcherThread.isAlive) fileWatcherThread.start()

  val shutdownHook = createShutdownHook("... killing ...")

  def createShutdownHook(msg: => String) =
    new Thread(new Runnable {
      def run() {
        if (isRunning) {
          log.info(msg)
          killProcess()
        }
      }
    })

  @volatile var finishState: Option[Int] = None

  val watchThread = {
    val thread = new Thread(new Runnable {
      def run() {
        val code = process.exitValue()
        finishState = Some(code)
        log.info("... finished with exit code " + code)
        fileWatcherThread.markStop()
        unregisterShutdownHook()
        Actions.unregisterAppProcess(projectRef)
        onExit(code)
      }
    })
    thread.start()
    thread
  }

  def projectName: String = projectRef.project

  def isCompiling: Boolean = fileWatcherThread.isCompiling
  def isCompileError: Boolean = fileWatcherThread.isCompileError
  def isAssetChanged: Boolean = fileWatcherThread.isAssetChanged

  def state: String =
    if (isCompiling) "compiling"
    else if (isCompileError) "compile-error"
    else if (isAssetChanged) "asset-changed"
    else if (isRunning) "running"
    else "stopped"

  registerShutdownHook()

  def stop() {
    fileWatcherThread.markStop()
    unregisterShutdownHook()
    killProcess()
    val code = process.exitValue()
    onExit(code)
  }

  @SuppressWarnings(Array("deprecation"))
  def killProcess(): Unit = {
    // Hack to silence output & error stream reading threads errors after killing process.
    // This hack prevents pesky error "java.io.IOException: Stream closed" when process
    // writes something to stdout/stderr AFTER receiving shutdown signal.
    // Seems to sbt bug.
    val outputThreadsField: Field = process.getClass.getDeclaredField("outputThreads")
    outputThreadsField.setAccessible(true)
    val outputThreads: Seq[Thread] = outputThreadsField.get(process).asInstanceOf[Seq[Thread]]
    outputThreads.foreach(_.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {} // just ignore exceptions
    }))

    process.destroy()
  }

  def registerShutdownHook() {
    JRuntime.getRuntime.addShutdownHook(shutdownHook)
  }

  def unregisterShutdownHook() {
    JRuntime.getRuntime.removeShutdownHook(shutdownHook)
  }

  def isRunning: Boolean =
    finishState.isEmpty
}
