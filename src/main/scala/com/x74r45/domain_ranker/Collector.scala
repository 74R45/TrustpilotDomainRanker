package com.x74r45.domain_ranker

import com.x74r45.domain_ranker.model.{Domain, Review}
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.*
import net.ruippeixotog.scalascraper.dsl.DSL.Extract.*
import net.ruippeixotog.scalascraper.model.*
import net.ruippeixotog.scalascraper.model.Element
import org.jsoup.Connection
import org.slf4j.LoggerFactory

import java.time.Instant
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.parallel.CollectionConverters.*
import scala.concurrent.duration.*
import scala.language.postfixOps

/**
 * Data collection layer
 */
object Collector {
  // Keep track of last collection time of each category page
  private val lastCollectionTimes: mutable.Map[String, Instant] = mutable.Map.empty
  private val InitialWindow = 30 minutes

  private val logger = LoggerFactory.getLogger(this.getClass)

  // URL parameters
  private val TrustpilotUrl =
    (categoryName: String) => f"https://trustpilot.com/categories/$categoryName?sort=latest_review"
  private val TrustpilotReviewsUrl =
    (id: String) => f"https://www.trustpilot.com/api/categoriespages/$id/reviews?locale=en-US"
  private val VstatUrl =
    (domainName: String) => f"https://www.vstat.info/$domainName"

  // Parsing parameters
  private val DomainElementCSSQuery = ".paper_paper__1PY90.paper_outline__lwsUX.card_card__lQWDv.card_noPadding__D8PcU.styles_wrapper__2JOo2"
  private val DomainIdRegex = "latest-reviews-(.*)-panel".r
  private val DomainNameRegex = "/review/(\\S*)\"".r
  private val DomainTotalReviewsRegex = "<span class=\"styles_separator__TG_uV\">\\|</span>([\\d,]+)".r

  // Browser settings
  private val trustpilotBrowser = new JsoupBrowser {
    override def requestSettings(conn: Connection): Connection =
      conn
        .ignoreContentType(true)
        .timeout((1 minute).toMillis.toInt)
  }
  private val vstatBrowser = new JsoupBrowser {
    override def requestSettings(conn: Connection): Connection =
      conn
        .header("authority", "web.vstat.info")
        .cookie("vstat_session", "O68lcne2YMZAhRU6PP4pV9OwRjtOWMrvhj3KECgt;")
        .timeout((1 minute).toMillis.toInt)
  }
  
  /**
   * Collects domains within a category that were reviewed recently and
   * processes them.
   * @param categoryName name of the category
   * @return an iterable of Domains with their corresponding data
   */
  def collectRecentlyReviewedDomains(categoryName: String): Iterable[Domain] = {
    // Get the instant after which domains need to be reviewed
    val after = lastCollectionTimes.get(categoryName) match {
      case Some(instant) =>
        lastCollectionTimes(categoryName) = Instant.now()
        instant
      case None =>
        lastCollectionTimes(categoryName) = Instant.now()
        lastCollectionTimes(categoryName).minusSeconds(InitialWindow.toSeconds)
    }

    // Get the page of the category, process each domain and filter the relevant ones
    // As this doesn't cause side effects, we can do this in parallel
    val trustpilotDoc = retrieveUrl(TrustpilotUrl(categoryName), trustpilotBrowser)
    (trustpilotDoc >> elements(DomainElementCSSQuery))
      .par.map(parseDomain(after))
      .collect { case Some(d) => d }.seq
  }

  /**
   * Parses an HTML element of the domain into an instance of Domain
   * @param after  collect reviews that were posted after this Instant
   * @param el     an HTML element
   * @return Some[Domain] if parsing was successful; otherwise None
   */
  private def parseDomain(after: Instant)(el: Element): Option[Domain] = {
    val html = el.innerHtml

    // Get domain name, id and the total number of reviews
    val name = DomainNameRegex.findFirstMatchIn(html) match {
      case Some(m) => m.group(1)
      case None =>
        logger.error("Error: parsing domain name failed!")
        return None
    }
    val id = DomainIdRegex.findFirstMatchIn(html) match {
      case Some(m) => m.group(1)
      case None =>
        logger.error(f"Error: parsing id of \"$name\" failed!")
        return None
    }
    val totalReviewCount = DomainTotalReviewsRegex.findFirstMatchIn(html) match {
      case Some(m) => ('0' +: m.group(1).filter(_.isDigit)).toInt
      case None =>
        logger.warn(f"Warning: parsing totalReviewCount for \"$name\" failed! Assigning it to 0.")
        0
    }

    // Get reviews JSON of this domain and parse it
    val reviewsDoc = retrieveUrl(TrustpilotReviewsUrl(id), trustpilotBrowser)
    val reviewsJson = reviewsDoc >> text("body")
    val reviews = parseReviewsJson(reviewsJson)
    if (reviews.isEmpty) {
      logger.error(f"Error: parsing reviews from url \"${TrustpilotReviewsUrl(id)}\" failed!")
      return None
    }

    // Filter reviews that were posted during secondsWindow
    val recentReviews = reviews.filter(_.date.createdAt.isAfter(after))
    if (recentReviews.isEmpty) return None

    // Get monthly traffic from Vstat
    val vstatDoc = retrieveUrl(VstatUrl(name), vstatBrowser)
    val monthlyVisitsParsed =
      (vstatDoc >> element("#MONTHLY_VISITS"))
        .attr("data-smvisits")
    val monthlyVisits = ('0' +: monthlyVisitsParsed.filter(_.isDigit)).toInt

    Some(Domain(name, recentReviews.size, totalReviewCount, monthlyVisits, recentReviews.head))
  }

  /**
   * Parses JSON containing reviews of a domain
   * @param json json string of format {"reviews": [...Reviews]}
   * @return a collection of Review instances or an empty collection if parsing failed
   */
  private def parseReviewsJson(json: String): Vector[Review] = {
    val parsed = parse(json).getOrElse(Json.Null)
    parsed.asObject match {
      case Some(obj) => obj("reviews").getOrElse(Json.Null).as[Vector[Review]] match {
        case Right(reviews) => reviews
        case Left(_) => Vector.empty
      }
      case None => Vector.empty
    }
  }

  /**
   * Retrieves a url using a browser, makes multiple attempts if an HTTP error occurs.
   * @param url      the url to retrieve
   * @param browser  the browser to use
   * @param attempts how many attempts to make before failing, defaults to 3
   * @return retrieved element
   */
  @tailrec
  private def retrieveUrl(url: String, browser: JsoupBrowser, attempts: Int = 3): JsoupBrowser.JsoupDocument = {
    try
      browser.get(url)
    catch
      case exception =>
        logger.error(exception.getMessage)
        if (attempts > 1)
          retrieveUrl(url, browser, attempts - 1)
        else
          throw exception
  }
}
