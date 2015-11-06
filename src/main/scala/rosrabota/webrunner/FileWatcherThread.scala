package rosrabota.webrunner
import java.io.File
import java.util.concurrent.TimeUnit

import rosrabota.webrunner.Utilities._
import sbt.Keys._
import sbt._

case class FileWatcherThread(streams: TaskStreams, state: State, monitorDirs: Seq[File], monitorFileFilter: FileFilter) extends Thread {
  private var _compiling = false
  def isCompiling: Boolean = _compiling

  private var _stopping = false
  def markStop(): Unit = _stopping = true

  override def run(): Unit = {
    println(":::::::::::::::::::: Started FileWatcher ::::::::::::::::::::")////////////
    val fileWatcher: FileWatcher = new FileWatcher
    monitorDirs.foreach(fileWatcher.addDirRecursively)
    while (!_stopping) {
      if (fileWatcher.poll(monitorFileFilter, 100, TimeUnit.MILLISECONDS)) {
        _compiling = true
        GlobalState.notifyListeners()

        val start = System.currentTimeMillis
        Project.runTask(compile in Compile, state).get._2.toEither.right.foreach {_ =>
          val duration = System.currentTimeMillis - start
          colorLogger(streams.log).info("[success] Compiled in " + duration + " ms")
        }
        _compiling = false
        GlobalState.notifyListeners()
      }
    }
    println(":::::::::::::::::::: Stopped FileWatcher ::::::::::::::::::::")////////////
  }
}
