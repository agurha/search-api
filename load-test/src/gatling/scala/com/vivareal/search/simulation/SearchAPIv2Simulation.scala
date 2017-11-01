package com.vivareal.search.simulation

import com.typesafe.config.ConfigFactory.load
import com.vivareal.search.config.SearchAPIv2Feeder.feeder
import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.collection.JavaConversions._
import scala.concurrent.duration._

class SearchAPIv2Simulation extends Simulation {

  val globalConfig = load()

  val runScenarios = globalConfig.getString("gatling.scenarios")
  val runScenariosSpl = runScenarios.split(",").toList

  val httpConf = http.baseURL(s"http://${globalConfig.getString("api.http.base")}")

  val path = globalConfig.getString("api.http.path")

  val index = globalConfig.getString("api.index")

  val scenariosConf = load("scenarios.conf")

  var scenarios = scenariosConf.getObjectList("scenarios")
    .map(configValue => configValue.toConfig)
    .filter(config => "_all".equals(runScenarios) || runScenariosSpl.contains(config.getString("scenario.id")))
    .map(config => {
      scenario(config.getString("scenario.decription"))
        .repeat(config.getInt("scenario.repeat")) {
          feed(feeder(config).random)
            .exec(http(config.getString("scenario.title")).get(path + index + config.getString("scenario.query")))
        }.inject(rampUsers(config.getInt("scenario.users")) over (globalConfig.getInt("gatling.rampUp") seconds))
    }).toList

  setUp(scenarios)
    .protocols(httpConf)
    .maxDuration(globalConfig.getInt("gatling.maxDuration") seconds)

}
