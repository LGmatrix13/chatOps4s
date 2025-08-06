package chatops4s.slack

import cats.effect.IO
import chatops4s.{InboundGateway, Message, OutboundGateway}
import chatops4s.slack.models.*
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode

class SlackGatewayTest extends AnyFreeSpec with Matchers {

  "SlackGateway" - {
    "create should return both gateways" in {
      val config = SlackConfig("test-token", "test-secret")
      val backend = new MockBackend()

      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = Some("1234567890.123")
      )

      backend.setResponse("chat.postMessage", response.asJson.noSpaces)

      val result = SlackGateway.create(config, backend).use { case (outbound, inbound) =>
        IO.pure((outbound, inbound))
      }.unsafeRunSync()

      result._1 shouldBe a[OutboundGateway]
      result._2 shouldBe a[InboundGateway]
    }

    "createOutboundOnly should work and send messages" in {
      val config = SlackConfig("test-token", "test-secret")
      val backend = new MockBackend()

      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = Some("1234567890.123")
      )

      backend.setResponse("chat.postMessage", response.asJson.noSpaces)

      val result = SlackGateway.createOutboundOnly(config, backend).use { gateway =>
        val message = Message("Test message")
        gateway.sendToChannel("C123", message).map(messageResponse => (gateway, messageResponse))
      }.unsafeRunSync()

      result._1 shouldBe a[OutboundGateway]
      result._2.messageId shouldBe "C123-1234567890.123"
    }

    "should handle configuration errors" in {
      val config = SlackConfig("", "") // Invalid config
      val backend = new MockBackend()

      val errorResponse = SlackPostMessageResponse(
        ok = false,
        error = Some("invalid_auth")
      )

      backend.setResponse("chat.postMessage", errorResponse.asJson.noSpaces)

      assertThrows[RuntimeException] {
        SlackGateway.createOutboundOnly(config, backend).use { gateway =>
          val message = Message("Test message")
          gateway.sendToChannel("C123", message)
        }.unsafeRunSync()
      }
    }

    "should work with resource management" in {
      val config = SlackConfig("test-token", "test-secret")
      val backend = new MockBackend()

      val response = SlackPostMessageResponse(
        ok = true,
        channel = Some("C123"),
        ts = Some("1234567890.123")
      )

      backend.setResponse("chat.postMessage", response.asJson.noSpaces)

      var gatewayUsed = false

      SlackGateway.create(config, backend).use { case (outbound, inbound) =>
        gatewayUsed = true
        val message = Message("Resource test")
        outbound.sendToChannel("C123", message).map(_ => ())
      }.unsafeRunSync()

      gatewayUsed shouldBe true
    }
  }
}