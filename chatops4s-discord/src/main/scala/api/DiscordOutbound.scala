package api

import io.circe.*
import io.circe.syntax.*
import cats.effect.IO
import com.typesafe.scalalogging.{Logger, StrictLogging}
import enums.{ButtonStyle, ContentType}
import models.*
import sttp.client4.circe.asJson
import sttp.client4.*

class DiscordOutbound(
  token: String,
  url: String,
  backend: Backend[IO]
) extends OutboundGateway, StrictLogging {
  private final val rootUrl = "https://discord.com/api/v10"
  private final val versionNumber = 1.0

  private def baseRequest = basicRequest
    .header("Authorization", s"Bot $token")
    .header("User-Agent", s"DiscordBot ($url, $versionNumber)")
    .header("Content-Type", "application/json")

  override def sendToChannel(channelId: String, message: Message): IO[MessageResponse] = {
    val json = if (message.interactions.nonEmpty) {
      Json.obj(
        "content" := message.text,
        "components" := Json.arr(Json.obj(
          "type" := ContentType.ActionRow.value,
          "components" := message.interactions.map { b =>
            Json.obj(
              "type" := ContentType.Button.value,
              "style" := ButtonStyle.Primary.value,
              "label" := b.label,
              "custom_id" := b.value
            )
          }
        ))
      )
    } else {
      Json.obj(
        "content" := message.text,
      )
    }

    val request = baseRequest
      .post(uri"$rootUrl/channels/$channelId/messages")
      .body(json.noSpaces)
      .response(asJson[Json])

    request.send(backend).flatMap { response =>
        response.body match {
          case Right(json) =>
            val messageId = json.hcursor.get[String]("id").getOrElse("")
            logger.info("Message sent to Discord")
            IO.pure(MessageResponse(messageId = messageId))
          case Left(error) =>
            logger.info(s"Failed to send message: $error")
            IO.raiseError(new RuntimeException(s"Failed to send message: $error"))
        }
    }
  }

  override def sendToThread(messageId: String, message: Message): IO[MessageResponse] = {
    IO.raiseError(new NotImplementedError("Not yet implemented"))
  }
}
