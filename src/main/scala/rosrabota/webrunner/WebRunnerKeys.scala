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

import sbt._

trait WebRunnerKeys {

  val wr = TaskKey[AppProcess]("wr", "Starts the application in a forked JVM (in the background). " +
    "If it is already running the application is first stopped and then restarted.")

  val wrStop = TaskKey[Unit]("wr-stop", "Stops the application if it is currently running in the background")

  val wrStatus = TaskKey[Unit]("wr-status", "Shows information about the application that is potentially running")

  val wrStartArgs = SettingKey[Seq[String]]("wr-start-args",
    "The arguments to be passed to the applications main method when being started")

  val wrForkOptions = TaskKey[ForkOptions]("wr-fork-options", "The options needed for the start task for forking")

  val wrJRebelJar = SettingKey[String]("wr-jrebel-jar", "The path to the JRebel JAR. Automatically initialized to " +
    "value of the `JREBEL_PATH` environment variable.")
  val wrJRebelMessages = SettingKey[Boolean]("wr-jrebel-messages", "Show JRebel messages. Defaults to false.")

  val debugSettings = SettingKey[Option[DebugSettings]]("debug-settings", "Settings for enabling remote JDWP debugging.")

  val wrMonitorDirs = SettingKey[Seq[File]]("wr-monitor-dirs")
  val wrMonitorFileFilter = SettingKey[FileFilter]("wr-monitor-file-filter")

  val wrWebServerHost = SettingKey[String]("wr-web-server-host", "Host for web server helper")
  val wrWebServerPort = SettingKey[Int]("wr-web-server-port", "Port for web server helper")

  val wrRestartExitCode = SettingKey[Option[Int]]("wr-restart-exit-code", "Application will be restarted after terminating with this exit code.")
}
