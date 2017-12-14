package webrunner
import java.io.File
import java.util.concurrent.TimeUnit

import sbt.Keys._
import sbt._

case class FileWatcherThread(streams: TaskStreams,
                             state: State,
                             monitorDirs: Seq[File],
                             monitorFileFilter: FileFilter,
                             assetFileFilter: FileFilter,
                             withJRebel: WithJRebel.Value) extends Thread {
  private def hasJRebel: Boolean = withJRebel != WithJRebel.No

  private var _compiling = false
  def isCompiling: Boolean = _compiling

  private var _compileError = false
  def isCompileError: Boolean = _compileError

  private var _assetChanged = false
  def isAssetChanged: Boolean = _assetChanged

  private var _stopping = false
  def markStop(): Unit = {
    // При остановке этого треда есть исключение в режиме jRebelMode == false:
    // Во время процесса компиляции нельзя останавливаться, потому что в случае ошибки компиляции,
    // этот тред уже не сможет запустить новую перекомпиляцию при изменении исходников.
    if (!hasJRebel && _compiling) return
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
    val allFilter = monitorFileFilter || assetFileFilter
    while (!_stopping) {
      val changed: Set[File] = fileWatcher.poll(allFilter, 100, TimeUnit.MILLISECONDS)

      if (changed.exists(monitorFileFilter.accept)) {
        _compiling = true
        GlobalState.notifyListeners()

        val start = System.currentTimeMillis
        val stopThread: Thread =
          if (hasJRebel) null
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
            // Additional paddings after failed compilation for better error log readability.
            streams.log.info("")
            streams.log.info("------------------------------------------------------------")
            streams.log.info("")
        }

        if (!hasJRebel) stopThread.join()
        _compiling = false
        GlobalState.notifyListeners()

        if (!_compileError) {
          // Nullify lastThread here to make sure new thread started from `wr` will not stop
          FileWatcherThread.lastThread = null

          if (!hasJRebel) {
            Project.runTask(WebRunnerPlugin.autoImport.wr, afterCompileState)
            GlobalState.notifyListeners()
            _stopping = true
          }
        }
      }
      else if (changed.exists(assetFileFilter.accept)) {
        _assetChanged = true
        GlobalState.notifyListeners()
        _assetChanged = false
        GlobalState.notifyListeners()
      }
    }
    if (FileWatcherThread.lastThread == this) {
      FileWatcherThread.lastThread = null
    }
  }
}

object FileWatcherThread {
  private var lastThread: FileWatcherThread = null
}
