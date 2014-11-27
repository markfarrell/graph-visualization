package crea.www

import scala.util.matching.Regex
import scala.util.{Try, Success, Failure}

import scala.concurrent._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.scalajs._
import scala.scalajs.js.Dynamic.newInstance
import js.annotation.JSExport

import org.scalajs.dom
import dom.document
import dom.{WebSocket, MessageEvent, console}

object WebApp extends js.JSApp {

  private[this] val client = new WebSocket("ws://localhost:8080/log")

  private[this] def gexfPath : String = "data/elastin-abstracts.gexf"

  private[this] def autocomplete(input : String) : Regex = input.toList.mkString("""[\s\w]*""").concat("""[\s\w]*""").r

  private[this] def searchInput : Option[dom.HTMLElement] = {
    Option(document.getElementById("search-input"))
  }

  private[this] def sigmajs = js.Dynamic.global.sigma

  private[this] lazy val sigma = {

    val settings = js.Dynamic.literal(
      font = "serif",
      drawEdgeLabels = true,
      edgeLabelSize = "fixed",
      defaultEdgeLabelSize = 12,
      defaultEdgeLabelColor = "#997F46",
      edgeLabelThreshold = 1.0,
      labelSize = "proportional",
      labelThreshold = 2.5,
      labelSizeRatio = 3.0,
      defaultLabelSize = 32,
      defaultLabelColor = "#FFD67D",
      minNodeSize = 0.5,
      maxNodeSize = 5,
      minEdgeSize = 0.1,
      maxEdgeSize = 0.4,
      scalingMode = "inside",
      hideEdgesOnMove = true,
      doubleClickEnabled = false
    )

    val  config = js.Dynamic.literal(
      container = "graph-container",
      graph = newInstance(sigmajs.classes.graph)(),
      settings = settings
    )

    newInstance(sigmajs)(config)

  }

  private[this] val promiseGraph : Promise[js.Dynamic] = Promise[js.Dynamic]
  private[this] val futureGraph : Future[js.Dynamic] = promiseGraph.future

  private[this] def dictionary : Array[String] = {
    sigma.graph.nodes().asInstanceOf[js.Array[js.Dynamic]].map((_ : js.Dynamic).label.asInstanceOf[String])
  }

  private[this] def resetGraph() : Future[Unit] = futureGraph.map { graph =>

    sigma.graph.clear()
    sigma.graph.read(js.Dynamic.literal(nodes = graph.nodes(), edges = graph.edges()))
    sigma.refresh()

  }

  private[this] def viewNeighborhood(label : String) : Future[Unit] = futureGraph.map { graph =>

    for (id <- findId(label)) {

      Try(graph.neighborhood(id)).map { neighborhood =>

        if(neighborhood.nodes.length > 1) {

          sigma.camera.goTo(js.Dynamic.literal(
            x = 0,
            y = 0,
            angle = 0,
            ratio = 1
          ))

          sigma.graph.clear()
          sigma.graph.read(neighborhood)
          sigma.refresh()

          searchInput.foreach(_.asInstanceOf[js.Dynamic].value = label)

        }

      }

    }

  }

  private[this] def findLabel : Option[String] = searchInput.map(_.asInstanceOf[js.Dynamic].value.asInstanceOf[String])

  private[this] def findId(label : String) : Future[js.Dynamic] = futureGraph.map { graph =>

    graph.nodes().asInstanceOf[js.Array[js.Dynamic]]
      .filter((elem : js.Dynamic) => elem.label.asInstanceOf[String] == label)
      .map((elem : js.Dynamic) => elem.id)
      .pop

  }

  @JSExport
  def search() = findLabel.foreach(viewNeighborhood)

  @JSExport
  def showSuggestions() = Option(document.getElementById("suggestions")).map { suggestions =>

    while(suggestions.hasChildNodes()) {
      suggestions.removeChild(suggestions.lastChild)
    }

    findLabel.map { label =>

      val dict = dictionary

      if(dict.size <= 500) {

        val items = dict.filter(suggestion => autocomplete(label).pattern.matcher(suggestion).matches)
          .sortBy(Levenshtein(label))
          .take(20)

        if(items.length > 0) {

          items.foreach { suggestion =>

            val li = document.createElement("li")
            li.onclick = (e : dom.MouseEvent) => viewNeighborhood(suggestion)
            li.appendChild(document.createTextNode(suggestion))

            suggestions.appendChild(li)

          }

          suggestions.style.display = "block";

        } else {

          hideSuggestions()

        }

      }

    }

  }

  @JSExport
  def hideSuggestions() = {

    document.getElementById("suggestions").style.display = "none"

  }

  def main() = {

    client.onmessage = { (event : MessageEvent) =>

      import js.JSON

      val data = JSON.parse(event.data.asInstanceOf[String]).asInstanceOf[js.Dynamic]

      console.log(data)

      val subject = data.subject
      val obj = data.obj

      console.log(s"Subject: $subject")

      val subjectNode = js.Dynamic.literal(
        id = subject,
        size = 1,
        x = Math.random*10,
        y = Math.random*10,
        `type` = subject,
        label = subject
      )

      val objectNode = js.Dynamic.literal(
        id = obj,
        size = 1,
        x = Math.random*10,
        y = Math.random*10,
        `type` = obj,
        label = obj
      )

      val edge = js.Dynamic.literal(
        label = data.predicate,
        id = data.timestamp,
        source = subject,
        target = obj,
        `type` = data.pmid
      )

      scala.util.Try(sigma.graph.addNode(subjectNode))
      scala.util.Try(sigma.graph.addNode(objectNode))
      sigma.graph.addEdge(edge)

      sigma.refresh()

    }

    sigmajs.renderers.`def` = sigmajs.renderers.canvas

    sigmajs.plugins.dragNodes(sigma, sigma.renderers.asInstanceOf[js.Array[js.Dynamic]](0))

    sigma.startForceAtlas2(js.Dynamic.literal(barnesHutOptimize = false))

  }

}
