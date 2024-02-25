package com.x74r45.domain_ranker

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date

/**
 * Data model layer
 */
object model {
  case class Domain(
      name: String,
      latestReviewCount: Int,
      totalReviewCount: Int,
      monthlyVisits: Int,
      latestReview: Review) {
    
    override def toString: String =
      f"""$name
        |-- Latest Review Count: $latestReviewCount
        |-- Total Review Count: $totalReviewCount
        |-- Monthly Visits: $monthlyVisits
        |-- Latest Review:
        |---- Author: ${latestReview.consumer.displayName}
        |---- Text: ${latestReview.text.replaceAll("\\s+", " ")}
        |---- Rating: ${latestReview.rating} / 5
        |---- Date: ${latestReview.date}
        |""".stripMargin
  }

  // Case classes that mimic the format of review JSON
  case class Review(
      text: String,
      rating: Int,
      date: ReviewDate,
      consumer: Consumer)

  case class Consumer(displayName: String)
  case class ReviewDate(createdAt: Instant) {
    override def toString: String =
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date.from(createdAt))
  }
}