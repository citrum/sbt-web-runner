package rosrabota.webrunner

import java.io.File
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{FileSystems, Path => JPath, WatchEvent, WatchKey, WatchService}
import java.util.concurrent.TimeUnit

import sbt.FileFilter

import scala.annotation.tailrec
import scala.collection.JavaConversions._

class FileWatcher {
  private val watcher: WatchService = FileSystems.getDefault.newWatchService()

  def addDir(dirFile: File): Unit = {
    dirFile.toPath.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
  }

  def addDirRecursively(dirFile: File): Unit = {
    if (dirFile.exists() && dirFile.isDirectory) {
      addDir(dirFile)
      for (subFile <- dirFile.listFiles() if subFile.isDirectory) {
        addDirRecursively(subFile)
      }
    }
  }

  def poll(filter: FileFilter): Boolean = check(watcher.poll(), filter)
  def poll(filter: FileFilter, timeout: Long, unit: TimeUnit): Boolean = check(watcher.poll(timeout, unit), filter)
  def take(filter: FileFilter): Unit = while (!check(watcher.take(), filter)) {}

  def close(): Unit = {
    watcher.close()
  }

  @tailrec private def check(key: WatchKey, filter: FileFilter, lastResult: Boolean = false): Boolean = {
    if (key == null) lastResult
    else {
      val hasChangedFiles: Boolean =
        key.pollEvents().exists { event =>
          event.kind() match {
            case OVERFLOW => lastResult
            case _ =>
              val path: JPath = event.asInstanceOf[WatchEvent[JPath]].context()
              filter.accept(path.toFile)
          }
        }
      // idea сохраняет файлы через переименование, поэтому следует немного подождать, пока процесс завершится
      // это позволяет избежать короткой перекомпиляции сразу после компиляции
      key.reset()
      Thread.sleep(20)
      check(watcher.poll(), filter, hasChangedFiles | lastResult)
    }
  }
}

object FileWatcher {
  def wrap[T](body: FileWatcher => T): T = {
    val watcher: FileWatcher = new FileWatcher
    try body(watcher)
    finally watcher.close()
  }
}