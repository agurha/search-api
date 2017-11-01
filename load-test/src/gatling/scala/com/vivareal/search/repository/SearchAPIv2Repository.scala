package com.vivareal.search.repository

import com.typesafe.config.{Config, ConfigFactory}
import org.json4s.native.JsonMethods._
import org.json4s.{DefaultFormats, JValue}

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.Try
import scalaj.http.{Http, HttpResponse}

object SearchAPIv2Repository {
  implicit val formats = DefaultFormats

  val config = ConfigFactory.load()

  val http = config.getConfig("api.http")

  val path = config.getString("api.http.path")

  val index = config.getString("api.index")

  val url = s"http://${http.getString("base")}$path$index"

  def getFacets(config: Config): List[(String, String)] = {
    def title = config.getString("scenario.title")
    config.getObjectList("scenario.facets").asScala.foldLeft(List[(String, String)]()) {
      (list, facet) => {
        def fct = facet.toConfig

        println(s"* Getting facets to... $title")
        val response: HttpResponse[String] = Http(s"$url?${fct.getString("query")}").asString
        val json: JValue = parse(response.body)
        val values = (json \ "result" \ "facets").extract[Map[String, Map[String, Long]]]
        list ++ values.head._2.keySet.map(v => (values.head._1, v))
      }
    }
  }

  def getIds(users: Int, repeat: Int, range: Int = 1): Iterator[String] = {
    val size = users * repeat * range
    println(s"* Getting stream ids.. (size:$size)")
    Source.fromURL(s"$url/stream?includeFields=id&sort=&size=$size")
      .getLines
      .take(size)
      .map(parse(_))
      .map(json => (json \ "id").extract[String])
  }
}
