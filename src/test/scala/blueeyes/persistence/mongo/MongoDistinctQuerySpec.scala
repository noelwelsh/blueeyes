package blueeyes.persistence.mongo

import org.spex.Specification
import MongoQueryBuilder._
import MongoFilterBuilder._
import MongoFilterOperators._
import blueeyes.json.JPathImplicits._
import blueeyes.json.JPath


class MongoDistinctQuerySpec extends Specification{
  private val query = distinct("foo").from(MongoCollection("collection"))

  "'where' method sets new filter" in {
    import MongoFilterImplicits._
    query.where("name" === "Joe") mustEqual (MongoDistinctQuery(JPath("foo"), MongoCollection("collection"), Some(MongoFieldFilter("name", $eq, "Joe"))))
  }
}