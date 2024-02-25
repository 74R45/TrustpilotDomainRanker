package com.x74r45.domain_ranker

import com.x74r45.domain_ranker.model.Domain

import scala.collection.mutable

/**
 * Data access layer
 */
class DomainDao {
  /**
   * In-memory store of domains, optimized for fast access and updates
   */
  private val domains: mutable.HashMap[String, Domain] = mutable.HashMap.empty

  /**
   * Updates a domain or inserts it if it's new.
   * @param d a newly-collected domain
   */
  def update(d: Domain): Unit = {
    if (domains.contains(d.name)) {
      val oldD = domains(d.name)
      val newD = Domain(
        d.name,
        oldD.latestReviewCount + d.latestReviewCount,
        d.totalReviewCount,
        d.monthlyVisits,
        d.latestReview)
      domains(d.name) = newD
    } else {
      domains(d.name) = d
    }
  }

  /**
   * Creates an ordered top-N collection of domains.
   * @param n   the size of the collection
   * @param ord the [[Ordering]] to use
   * @return a new [[Iterable]] of size <b>n</b> or smaller if there are less domains
   */
  def getTopN(n: Int)(implicit ord: Ordering[Domain]): Iterable[Domain] = {
    val queue = mutable.PriorityQueue(domains.values.toSeq*)(ord)
    val end = n min queue.size
    (1 to end) map (_ => queue.dequeue())
  }
}
