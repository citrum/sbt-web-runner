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

package webrunner

import sbt.Keys._
import sbt._
import webrunner.Actions._

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

  lazy val settings = Seq[Setting[_]](

    mainClass in wr := (mainClass in run in Compile).value,

    fullClasspath in wr := (fullClasspath in Runtime).value,

    wr := {
      val state = Keys.state.value
      val streams = Keys.streams.value
      val project = thisProjectRef.value
      startWebServer(wrWebServerHost.value, wrWebServerPort.value, state.log)
      stopApp(streams.log, project, logIfNotStarted = false)
      val withJRebel: WithJRebel.Value =
        if (wrJRebelJar.value.nonEmpty) WithJRebel.V5
        else if (wrJRebel6AgentPath.value.nonEmpty) WithJRebel.V6
        else WithJRebel.No
      val fileWatcherThread: FileWatcherThread = GlobalState.get().getProcess(project) match {
        case Some(app) => app.fileWatcherThread
        case None => new FileWatcherThread(streams, state, wrMonitorDirs.value,
          wrMonitorFileFilter.value, wrMonitorAssetFileFilter.value, withJRebel)
      }
      startApp(streams, project, wrForkOptions.value, (mainClass in wr).value,
        (fullClasspath in wr).value, wrStartArgs.value, fileWatcherThread, withJRebel, wrJRebelMessages.value,
        wrRestartExitCode.value)
    },
    aggregate in wr := false,

    ws := stopAppWithStreams(streams.value, thisProjectRef.value),
    aggregate in ws := false,

    wrStatus := showStatus(streams.value, thisProjectRef.value),
    aggregate in wrStatus := false,

    // default: no arguments to the app
    wrStartArgs in Global := Seq.empty,

    // initialize with env variable
    wrJRebelJar in Global := Option(System.getenv("JREBEL_PATH")).getOrElse(""),
    wrJRebel6AgentPath in Global := Option(System.getenv("JREBEL6_PATH")).getOrElse(""),
    wrJRebelMessages in Global := false,

    wrRestartExitCode in Global := None,

    debugSettings in Global := None,

    // bake JRebel activation into java options for the forked JVM
    changeJavaOptionsWithExtra(debugSettings in wr) {(jvmOptions, jrebel5Jar, jrevel6So, debug) =>
      jvmOptions ++ createJRebelAgentOption(SysoutLogger, jrebel5Jar, jrevel6So).toSeq ++
        debug.map(_.toCmdLineArg).toSeq
    },

    // bundles the various parameters for forking
    wrForkOptions := {
      taskTemporaryDirectory.value
      // bootJars is empty by default because only jars on the user's classpath should be on the boot classpath
      ForkOptions()
        .withJavaHome(javaHome.value)
        .withOutputStrategy(outputStrategy.value)
        .withWorkingDirectory(Some((baseDirectory in wr).value))
        .withRunJVMOptions((javaOptions in wr).value.toVector)
    },

    // stop a possibly running application if the project is reloaded and the state is reset
    onUnload in Global ~= {onUnload => state =>
      stopApps(state.log)
      stopWebServer()
      onUnload(state)
    },

    wrMonitorDirs := (sourceDirectories in Compile).value ++ (resourceDirectories in Compile).value,
    wrMonitorFileFilter := new SimpleFilter(n => n.endsWith(".scala") || n.endsWith(".java")),
    wrMonitorAssetFileFilter := new SimpleFilter(n => n.indexOf('.') != -1),

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
//  def changeJavaOptions(f: (Seq[String], String) => Seq[String]): Setting[_] =
//    changeJavaOptionsWithExtra(sbt.Keys.baseDirectory /* just an ignored dummy */)((jvmArgs, path, _) => f(jvmArgs, path))

  def changeJavaOptionsWithExtra[T](extra: SettingKey[T])(f: (Seq[String], String, String, T) => Seq[String]): Setting[_] =
    javaOptions in wr := f(javaOptions.value, wrJRebelJar.value, wrJRebel6AgentPath.value, extra.value)
}
