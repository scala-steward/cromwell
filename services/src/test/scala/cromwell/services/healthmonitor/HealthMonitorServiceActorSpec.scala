package cromwell.services.healthmonitor

import akka.actor.Props
import akka.pattern.AskSupport
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import cromwell.core.TestKitSuite
import cromwell.services.healthmonitor.ProtoHealthMonitorServiceActor.{GetCurrentStatus, StatusCheckResponse}
import cromwell.services.healthmonitor.impl.HealthMonitorServiceActor
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration._

class HealthMonitorServiceActorSpec
    extends TestKitSuite
    with AnyFlatSpecLike
    with Matchers
    with Eventually
    with AskSupport {

  implicit override def patienceConfig =
    PatienceConfig(timeout = scaled(Span(15, Seconds)), interval = Span(500, Millis))

  behavior of "HealthMonitorServiceActor"

  it should "be able to load an instance and answer questions even with only basic configuration" in {

    val serviceConfigString =
      """check-engine-database: false
        |check-papi-backends: []
        |""".stripMargin

    val globalConfigString =
      s"""services.HealthMonitor.config: {
         |$serviceConfigString
         |}
         |""".stripMargin

    val actor = system.actorOf(
      Props(
        new HealthMonitorServiceActor(ConfigFactory.parseString(serviceConfigString),
                                      ConfigFactory.parseString(globalConfigString),
                                      null
        )
      )
    )

    val testProbe = TestProbe()

    eventually {
      testProbe.send(actor, GetCurrentStatus)
      testProbe.expectMsg(5.seconds, StatusCheckResponse(ok = true, systems = Map.empty))
    }
  }
}
