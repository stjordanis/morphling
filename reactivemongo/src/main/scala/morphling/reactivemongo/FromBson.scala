package morphling.reactivemongo

import cats._
import cats.data.EitherK
import cats.free._
import morphling.HFunctor.HAlgebra
import morphling.Schema.Schema
import morphling.{Alt, HFix, IsoSchema, OneOfSchema, Optional, PrimSchema, PropSchema, RecordSchema, Required, SchemaF}
import mouse.boolean._
import ops._
import reactivemongo.bson._
import simulacrum.typeclass

@typeclass
trait FromBson[S[_]] {
  def reader: S ~> BSONReader[BSONValue, ?]
}

object FromBson {
  implicit class FromBsonOps[F[_], A](fa: F[A]) {
    def reader(implicit FB: FromBson[F]): BSONReader[BSONValue, A] = FB.reader(fa)
  }

  implicit def schemaFromBson[P[_]: FromBson]: FromBson[Schema[P, ?]] = new FromBson[Schema[P, ?]] {
    def reader: Schema[P, ?] ~> BSONReader[BSONValue, ?] = new (Schema[P, ?] ~> BSONReader[BSONValue, ?]) {
      override def apply[I](schema: Schema[P, I]) = {
        HFix.cataNT[SchemaF[P, ?[_], ?], BSONReader[BSONValue, ?]](decoderAlg[P]).apply(schema)
      }
    }
  }

  def decoderAlg[P[_]: FromBson]: HAlgebra[SchemaF[P, ?[_], ?], BSONReader[BSONValue, ?]] =
    new HAlgebra[SchemaF[P, ?[_], ?], BSONReader[BSONValue, ?]] {
      def apply[I](s: SchemaF[P, BSONReader[BSONValue, ?], I]): BSONReader[BSONValue, I] = s match {
        case PrimSchema(p) =>
          FromBson[P].reader(p)

        case OneOfSchema(alts) =>
          BSONReader[BSONDocument, I] { doc =>
            val results = for {
              fields <- doc.elements.map(_.name).toList
              altResult <- alts.toList flatMap {
                case Alt(id, base, prism) =>
                  fields.contains(id).option(
                    doc.getAs(id)(base).map(prism.reverseGet)
                  ).toList
              }
            } yield altResult

            val altIds = alts.map(_.id)
            results match {
              case Some(x) :: Nil => x
              case None :: Nil => throw exceptions.TypeDoesNotMatch(s"Could not deserialize ${alts.head.id}")
              case Nil => throw exceptions.DocumentKeyNotFound(s"No fields found matching any of $altIds")
              //case _ => Left(DecodingFailure(s"More than one matching field found among $altIds}", c.history))
            }
          }.widenReader

        case RecordSchema(rb) =>
          decodeObj(rb)

        case IsoSchema(base, iso) =>
          base.afterRead(iso.get)
      }
    }

  def decodeObj[I](rb: FreeApplicative[PropSchema[I, BSONReader[BSONValue, ?], ?], I]): BSONReader[BSONValue, I] = {
    implicit val djap: Applicative[BSONReader[BSONValue, ?]] = new Applicative[BSONReader[BSONValue, ?]] {
      override def pure[T](a: T) = BSONReader[BSONValue, T](_ => a)

      override def ap[T, U](ff: BSONReader[BSONValue, T => U])(fa: BSONReader[BSONValue, T]): BSONReader[BSONValue, U] =
        (v: BSONValue) => ff.read(v)(fa.read(v))
    }

    rb.foldMap(
      new (PropSchema[I, BSONReader[BSONValue, ?], ?] ~> BSONReader[BSONValue, ?]) {
        def apply[B](ps: PropSchema[I, BSONReader[BSONValue, ?], B]): BSONReader[BSONValue, B] = ps match {
          case Required(field, base, _, _) =>
            BSONReader[BSONDocument, B](doc =>
              doc.getAs[B](field)(base).getOrElse(throw exceptions.DocumentKeyNotFound(field))
            ).widenReader

          case opt: Optional[I, BSONReader[BSONValue, ?], i] =>
            BSONReader[BSONDocument, B](doc =>
              doc.getAs[i](opt.fieldName)(opt.base)
            ).widenReader
        }
      }
    )
  }

  implicit def eitherKFromBson[P[_]: FromBson, Q[_]: FromBson] = new FromBson[EitherK[P, Q, ?]] {
    val reader = new (EitherK[P, Q, ?] ~> BSONReader[BSONValue, ?]) {
      def apply[A](p: EitherK[P, Q, A]): BSONReader[BSONValue, A] = {
        p.run.fold(
          FromBson[P].reader(_),
          FromBson[Q].reader(_),
        )
      }
    }
  }
}
