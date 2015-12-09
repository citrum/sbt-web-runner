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

import rosrabota.webrunner.Actions._
import sbt.Keys._
import sbt._
object WebRunnerPlugin extends AutoPlugin {

  object autoImport extends WebRunnerKeys {
    object WebRunner {
      def settings = WebRunnerPlugin.settings

      def enableDebugging(port: Int = 5005, suspend: Boolean = false) =
        debugSettings in wr := Some(DebugSettings(port, suspend))
    }

    val revolverSettings = WebRunnerPlugin.settings
  }
  import autoImport._

  lazy val settings = Seq(

    mainClass in wr <<= mainClass in run in Compile,

    fullClasspath in wr <<= fullClasspath in Runtime,

    wr := {
      val state = Keys.state.value
      val streams = Keys.streams.value
      val project = thisProjectRef.value
      startWebServer(wrWebServerHost.value, wrWebServerPort.value, state.log)
      stopApp(streams.log, project, logIfNotStarted = false)
      val withJRebel: Boolean = wrJRebelJar.value.nonEmpty
      val fileWatcherThread: FileWatcherThread = GlobalState.get().getProcess(project) match {
        case Some(app) => app.fileWatcherThread
        case None => new FileWatcherThread(streams, state, wrMonitorDirs.value, wrMonitorFileFilter.value, withJRebel)
      }
      startApp(streams, project, wrForkOptions.value, (mainClass in wr).value,
        (fullClasspath in wr).value, wrStartArgs.value, fileWatcherThread, withJRebel, wrJRebelMessages.value,
        wrRestartExitCode.value)
    },
    aggregate in wr := false,

    wrStop <<= (streams, thisProjectRef).map(stopAppWithStreams),
    aggregate in wrStop := false,

    wrStatus <<= (streams, thisProjectRef) map showStatus,
    aggregate in wrStatus := false,

    // default: no arguments to the app
    wrStartArgs in Global := Seq.empty,

    // initialize with env variable
    wrJRebelJar in Global := Option(System.getenv("JREBEL_PATH")).getOrElse(""),
    wrJRebelMessages in Global := false,

    wrRestartExitCode in Global := None,

    debugSettings in Global := None,

    // bake JRebel activation into java options for the forked JVM
    changeJavaOptionsWithExtra(debugSettings in wr) {(jvmOptions, jrJar, debug) =>
      jvmOptions ++ createJRebelAgentOption(SysoutLogger, jrJar).toSeq ++
        debug.map(_.toCmdLineArg).toSeq
    },

    // bundles the various parameters for forking
    wrForkOptions <<= (taskTemporaryDirectory, baseDirectory in wr, javaOptions in wr, outputStrategy, javaHome) map ((tmp, base, jvmOptions, strategy, javaHomeDir) =>
      ForkOptions(
        javaHomeDir,
        strategy,
        Nil, // bootJars is empty by default because only jars on the user's classpath should be on the boot classpath
        workingDirectory = Some(base),
        runJVMOptions = jvmOptions,
        connectInput = false
      )),

    // stop a possibly running application if the project is reloaded and the state is reset
    onUnload in Global ~= {onUnload => state =>
      stopApps(state.log)
      stopWebServer()
      onUnload(state)
    },

    wrMonitorDirs := (sourceDirectories in Compile).value ++ (resourceDirectories in Compile).value,
    wrMonitorFileFilter := new FileFilter {
      override def accept(pathname: File): Boolean = {
        val fileName = pathname.getName
        fileName.endsWith(".scala") || fileName.endsWith(".java")
      }
    },

    wrWebServerHost := "127.0.0.1",
    wrWebServerPort := 0  // disable web-server
  )

  override def requires = sbt.plugins.JvmPlugin
  override def trigger = allRequirements
  override def projectSettings = settings

  /**
   * Changes javaOptions by using transformer function
   * (javaOptions, jrebelJarPath) => newJavaOptions
   */
  def changeJavaOptions(f: (Seq[String], String) => Seq[String]): Setting[_] =
    changeJavaOptionsWithExtra(sbt.Keys.baseDirectory /* just an ignored dummy */)((jvmArgs, path, _) => f(jvmArgs, path))

  def changeJavaOptionsWithExtra[T](extra: SettingKey[T])(f: (Seq[String], String, T) => Seq[String]): Setting[_] =
    javaOptions in wr <<= (javaOptions, wrJRebelJar, extra) map f
}
