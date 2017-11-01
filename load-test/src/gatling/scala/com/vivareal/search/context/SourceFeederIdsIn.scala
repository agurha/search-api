package com.vivareal.search.context

import com.typesafe.config.Config
import com.vivareal.search.repository.SearchAPIv2Repository.getIds

object SourceFeederIdsIn extends SourceFeeder{

  def feeds(config: Config): Array[Map[String, String]] = {
    def values = getIds(config.getInt("scenario.users"), config.getInt("scenario.repeat"))
    val range = config.getInt("scenario.range")

    values
      .sliding(range, range)
      .map(ids => Map("value" -> ids.mkString(",")))
      .toArray

    getIds(config.getInt("scenario.users"), config.getInt("scenario.repeat")).map(value => Map("value" -> value)).toArray
  }
}
