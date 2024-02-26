package com.x74r45.domain_ranker

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}
import akka.util.ByteString
import com.x74r45.domain_ranker.model.Domain
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.*
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * Service runner
 */
object DomainRankerApp extends App {
  implicit val system: ActorSystem = ActorSystem("trustpilot-domain-ranker")
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val logger = LoggerFactory.getLogger(this.getClass)

  val CategoryNames = Seq("jewelry_store", "clothing_store", "electronics_store")
  val UpdateInterval = 5 minutes

  val domainDao = DomainDao()
  val DashboardPath = Paths.get("/out/dashboard.txt")
  val DashboardSize = 10

  /** Domain ordering on the dashboard (latestReviewCount DESC, monthlyVisits DESC) */
  implicit val DashboardDomainOrdering: Ordering[Domain] =
    Ordering.by(d => (d.latestReviewCount, d.monthlyVisits))

  // Sources and Sinks for domains and the dashboard
  val domainSource: Source[Domain, NotUsed] =
    Source(CategoryNames).mapConcat(Collector.collectRecentlyReviewedDomains)

  val domainSink: Sink[Domain, Future[Int]] =
    Sink.fold(0)((count: Int, domain: Domain) => {
      domainDao.update(domain)
      count + 1
    })

  val lastUpdatedSource: Source[String, NotUsed] = Source.single(NotUsed)
    .map(_ =>
      val now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date())
      f"""--------- Domain Dashboard ---------
         |Last updated: $now\n\n""".stripMargin
    )

  val dashboardSource: Source[String, NotUsed] = Source.single(NotUsed)
    .mapConcat(_ => domainDao.getTopN(DashboardSize))
    .zipWithIndex.map((d, i) => f"${i + 1}. $d\n")
    .prepend(lastUpdatedSource)

  val dashboardSink: Sink[String, Future[IOResult]] = Flow[String]
    .map(ByteString.apply)
    .toMat(FileIO.toPath(DashboardPath))(Keep.right)

  /**
   * Runs stream to collect new domains, put them in the store, and update the dashboard.
   */
  private def updateDomains(): Unit = {
    logger.info("Collecting domain data...")
    domainSource
      .recoverWithRetries(3, _ => domainSource)
      .runWith(domainSink)
      .onComplete {
        case Success(count) =>
          logger.info("Collected {} domain{}!", count, if (count == 1) "" else "s")
          updateDashboard()
        case Failure(ex) => logger.error("Error while collecting domains: {}", ex.getMessage)
      }
  }

  /**
   * Runs the stream to update the dashboard.
   */
  private def updateDashboard(): Unit = {
    dashboardSource
      .runWith(dashboardSink)
      .onComplete {
        case Success(_) => logger.info("Dashboard updated!")
        case Failure(ex) => logger.error("Error while updating dashboard: {}", ex.getMessage)
      }
  }

  // Run update periodically
  Source.tick(0 milli, UpdateInterval, NotUsed)
    .runForeach(_ => updateDomains())
}
