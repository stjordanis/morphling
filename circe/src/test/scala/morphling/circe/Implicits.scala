package morphling.circe

import cats._
import io.circe.{AccumulatingDecoder, Decoder, Encoder}
import morphling.protocol.SType.SSchema

object Implicits extends CircePack {
  implicit val primToJson: ToJson[SSchema] = new ToJson[SSchema] {
    val encoder = new (SSchema ~> Encoder) {
      def apply[I](s: SSchema[I]): Encoder[I] = sTypeEncoder[SSchema[I]#Inner].apply(s.unmutu)
    }
  }

  implicit val primFromJson: FromJson[SSchema] = new FromJson[SSchema] {
    val decoder = new (SSchema ~> Decoder) {
      def apply[I](s: SSchema[I]): Decoder[I] = sTypeDecoder[SSchema[I]#Inner].apply(s.unmutu)
    }

    val accumulatingDecoder: SSchema ~> AccumulatingDecoder =
      decoder.andThen(λ[Decoder ~> AccumulatingDecoder](AccumulatingDecoder.fromDecoder(_)))
  }
}
