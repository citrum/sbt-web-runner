package rosrabota.webrunner
import java.io.File
import java.util.concurrent.TimeUnit

import sbt.Keys._
import sbt._

case class FileWatcherThread(streams: TaskStreams,
                             state: State,
                             monitorDirs: Seq[File],
                             monitorFileFilter: FileFilter,
                             withJRebel: Boolean = false) extends Thread {
  private var _compiling = false
  def isCompiling: Boolean = _compiling

  private var _compileError = false
  def isCompileError: Boolean = _compileError

  private var _stopping = false
  def markStop(): Unit = {
    // При остановке этого треда есть исключение в режиме jRebelMode == false:
    // Во время процесса компиляции нельзя останавливаться, потому что в случае ошибки компиляции,
    // этот тред уже не сможет запустить новую перекомпиляцию при изменении исходников.
    if (!withJRebel && _compiling) return
    _stopping = true
  }

  override def run(): Unit = {
    if (FileWatcherThread.lastThread != null) {
      streams.log.error("FileWatcherThread did not finished! Stopping this thread.")
      return
    }
    FileWatcherThread.lastThread = this
    val fileWatcher: FileWatcher = new FileWatcher
    monitorDirs.foreach(fileWatcher.addDirRecursively)
    while (!_stopping) {
      if (fileWatcher.poll(monitorFileFilter, 100, TimeUnit.MILLISECONDS)) {
        _compiling = true
        GlobalState.notifyListeners()

        val start = System.currentTimeMillis
        val stopThread: Thread =
          if (withJRebel) null
          else {
            val thread = new Thread() {override def run(): Unit = Project.runTask(WebRunnerPlugin.autoImport.ws, state)}
            thread.start()
            thread
          }
        val (afterCompileState, compileResult) = Project.runTask(compile in Compile, state).get
        compileResult.toEither match {
          case Right(_) =>
            val duration = System.currentTimeMillis - start
            streams.log.info("[success] Compiled in " + duration + " ms")
            _compileError = false

          case Left(_) =>
            _compileError = true
        }

        if (!withJRebel) stopThread.join()
        _compiling = false
        GlobalState.notifyListeners()

        if (!withJRebel && !_compileError) {
          Project.runTask(WebRunnerPlugin.autoImport.wr, afterCompileState)
          GlobalState.notifyListeners()
          _stopping = true
        }
      }
    }
    if (FileWatcherThread.lastThread != this) {
      streams.log.error("FileWatcherThread.lastThread != this did not finished! Stopping this thread.")
    }
    FileWatcherThread.lastThread = null
  }
}

object FileWatcherThread {
  private var lastThread: FileWatcherThread = null
}
