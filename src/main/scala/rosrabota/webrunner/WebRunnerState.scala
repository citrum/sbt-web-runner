package rosrabota.webrunner

import java.util.concurrent.atomic.AtomicReference

import sbt.ProjectRef

import scala.annotation.tailrec

case class WebRunnerState(processes: Map[ProjectRef, AppProcess]) {
  def addProcess(project: ProjectRef, process: AppProcess): WebRunnerState = copy(processes = processes + (project -> process))
  private[this] def removeProcess(project: ProjectRef): WebRunnerState = copy(processes = processes - project)
  def removeProcessAndColor(project: ProjectRef): WebRunnerState =
    getProcess(project) match {
      case Some(process) => removeProcess(project)
      case None => this
    }

  def exists(project: ProjectRef): Boolean = processes.contains(project)
  def runningProjects: Seq[ProjectRef] = processes.keys.toSeq
  def getProcess(project: ProjectRef): Option[AppProcess] = processes.get(project)
}

object WebRunnerState {
  def initial = WebRunnerState(Map.empty)
}

/**
 * Manages global state. This is not a full-blown STM so be cautious not to lose
 * state when doing several updates depending on another.
 */
object GlobalState {
  private[this] val state = new AtomicReference(WebRunnerState.initial)
  private var stateListeners = Vector.empty[WebRunnerState => Any]

  @tailrec def update(f: WebRunnerState => WebRunnerState): WebRunnerState = {
    val originalState = state.get()
    val newState = f(originalState)
    if (!state.compareAndSet(originalState, newState)) update(f)
    else {
      notifyListeners(newState)
      newState
    }
  }
  @tailrec def updateAndGet[T](f: WebRunnerState => (WebRunnerState, T)): T = {
    val originalState = state.get()
    val (newState, value) = f(originalState)
    if (!state.compareAndSet(originalState, newState)) updateAndGet(f)
    else {
      notifyListeners(newState)
      value
    }
  }

  def get(): WebRunnerState = state.get()

  def addListener(listener: WebRunnerState => Any): Unit = {
    stateListeners :+= listener
  }

  def removeListener(listener: WebRunnerState => Any): Unit = {
    stateListeners = stateListeners.filter(_ ne listener)
  }

  private def notifyListeners(state: WebRunnerState): Unit = {
    stateListeners.foreach(_.apply(state))
  }
  def notifyListeners(): Unit = notifyListeners(get())
}
