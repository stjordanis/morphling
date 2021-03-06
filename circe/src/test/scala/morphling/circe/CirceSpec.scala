package morphling.circe

import cats.scalatest.{EitherValues, ValidatedValues}
import io.circe.Json
import io.circe.syntax._
import morphling.circe.FromJson._
import morphling.circe.Implicits._
import morphling.circe.ToJson._
import morphling.samples._
import morphling.scalacheck.Implicits._
import morphling.scalacheck.ToGen._
import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class CirceSpec extends AnyFunSuite with Matchers with EitherValues with ValidatedValues with Checkers {
  test("A value should serialise to JSON") {
    implicit val encoder = Person.schema.encoder

    person.asJson shouldBe Json.obj(
      "updateCounter" := 42,
      "roles" := Seq(
        Json.obj(
          "administrator" -> Json.obj(
            "subordinateCount" := 0,
            "department" := "windmill-tilting"
          )
        )
      ),
      "birthDate" := 20147028000L,
      "name" := "Kris Nuttycombe"
    )
  }

  test("A value should be deserialised from JSON"){
    implicit val encoder = Person.schema.encoder
    val decoder = Person.schema.decoder

    decoder.decodeJson(person.asJson).value shouldBe person.copy(stamp = 101)
    decoder.decodeAccumulating(person.asJson.hcursor).value shouldBe person.copy(stamp = 101)
  }

  test("A default value should be applied during deserialization") {
    implicit val encoder = Person.schema.encoder
    val decoder = Person.schema.decoder

    decoder.decodeJson(person.asJson.mapObject(_.filterKeys(_ != "updateCounter"))).value shouldBe person.copy(updateCounter = 0, stamp = 101)
    decoder.decodeAccumulating(person.asJson.mapObject(_.filterKeys(_ != "updateCounter")).hcursor).value shouldBe person.copy(updateCounter = 0, stamp = 101)
  }

  test("Serialization should round-trip values produced by a generator"){
    implicit val arbPerson : Arbitrary[Person] = Arbitrary(Person.schema.gen)
    implicit val encoder = Person.schema.encoder
    val decoder = Person.schema.decoder
    check {
      (p: Person) => decoder.decodeJson(p.asJson).toOption == Some(p)
    }
    check {
      (p: Person) => decoder.decodeAccumulating(p.asJson.hcursor).toOption == Some(p)
    }
  }

  test("A value should serialize to JSON flat") {
    implicit val encoder = Person.flatSchema.encoder

    person.asJson shouldBe Json.obj(
      "updateCounter" := 42,
      "roles" := Seq(
        Json.obj(
          "type" := "administrator",
          "subordinateCount" := 0,
          "department" := "windmill-tilting"
        )
      ),
      "birthDate" := 20147028000L,
      "name" := "Kris Nuttycombe"
    )
  }

  test("A value should be deserialized from JSON flat") {
    implicit val encoder = Person.flatSchema.encoder
    val decoder = Person.flatSchema.decoder

    decoder.decodeJson(person.asJson).value shouldBe person.copy(stamp = 101)
  }

  test("Flat serialization should round-trip values produced by a generator"){
    implicit val arbPerson : Arbitrary[Person] = Arbitrary(Person.flatSchema.gen)
    implicit val encoder = Person.flatSchema.encoder
    val decoder = Person.flatSchema.decoder
    check {
      (p: Person) => decoder.decodeJson(p.asJson).toOption == Some(p)
    }
  }
}
