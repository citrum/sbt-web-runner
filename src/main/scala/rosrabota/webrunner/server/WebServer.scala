package rosrabota.webrunner.server
import java.io.{IOException, OutputStream}

import rosrabota.webrunner.{AppProcess, GlobalState, WebRunnerState}
import sbt.ProjectRef

class WebServer(host: String, port: Int) extends SimpleHttpServerBase(host, port) {
  get("/") {exchange =>
    def result(json: String): Unit = {
      exchange.getResponseHeaders.add("Content-Type", "application/json; charset=utf-8")
      exchange.getResponseHeaders.add("Access-Control-Allow-Origin", "*")
      respond(exchange, 200, json)
    }

    var appState: String = null
    var project: String = null

    exchange.getRequestURI.getQuery match {
      case WebServer.stateQueryRe(curState) =>
        setParams(GlobalState.get())
        if (curState != appState) {
          resultParams()
        } else {
          GlobalState.addListener {wrs =>
            setParams(wrs)
            resultParams()
          }
        }
      case _ =>
        setParams(GlobalState.get())
        resultParams()
    }

    def setParams(wrs: WebRunnerState): Unit = {
      val processes: Map[ProjectRef, AppProcess] = wrs.processes
      if (processes.isEmpty) {
        appState = "stopped"
        project = null
      } else {
        val st = processes.head._2
        appState = st.state
        project = st.projectName
      }
    }
    def resultParams(): Unit = {
      if (project == null) result("{\"appState\":\"" + appState + "\"}")
      else result("{\"project\":\"" + project + "\", " + "\"appState\":\"" + appState + "\"}")
    }
  }

  // -------------------------------  -------------------------------

  get("/evt") {exchange =>
    exchange.getResponseHeaders.add("Content-Type", "text/event-stream")
    exchange.getResponseHeaders.add("Access-Control-Allow-Origin", "*")
    exchange.sendResponseHeaders(200, 0)

    def result(msg: String): Boolean = {
      try {
        val resp: OutputStream = exchange.getResponseBody
        resp.write("data: ".getBytes)
        resp.write(msg.getBytes)
        resp.write("\n\n".getBytes)
        resp.flush()
        //        exchange.getResponseBody.close()
        //      exchange.close()
        true
      } catch {
        case e: IOException => false
      }
    }

    var lastState: String = null

    def sendState(wrs: WebRunnerState): Boolean = {
      var appState: String = null
      var project: String = null
      val processes: Map[ProjectRef, AppProcess] = wrs.processes
      if (processes.isEmpty) {
        appState = "stopped"
        project = null
      } else {
        val st = processes.head._2
        appState = st.state
        project = st.projectName
      }
      if (lastState != appState) {
        lastState = appState
        if (project == null) result("{\"state\":\"" + appState + "\"}")
        else result("{\"project\":\"" + project + "\", " + "\"state\":\"" + appState + "\"}")
      } else true
    }

    if (sendState(GlobalState.get())) {
      var listener: (WebRunnerState) => Unit = null
      listener = {wrs: WebRunnerState =>
        if (!sendState(wrs)) {
          GlobalState.removeListener(listener)
        }
      }
      GlobalState.addListener(listener)
    }
  }
}

object WebServer {
  val stateQueryRe = "^state=(.*)$".r
}
