package app.fuggs.bot.notification;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Exercises {@code POST /api/notifications}, the endpoint fuggs-app calls to
 * push a proactive message (issue #94 AC #3) through this bot service. The
 * actual WhatsApp send call is exercised end-to-end by
 * {@code WhatsAppWebhookResourceTest} against a stubbed Graph API; this class
 * only needs to prove the dispatch/validation wiring here.
 */
@QuarkusTest
class NotificationResourceTest
{
	@Test
	void shouldRejectUnsupportedChannel()
	{
		given()
			.contentType("application/json")
			.body(new NotificationResource.NotificationRequest("signal", "+491234", "egal"))
			.when()
			.post("/api/notifications")
			.then()
			.statusCode(400)
			.body("error", equalTo("unsupported_channel"));
	}

	@Test
	void shouldReturnServiceUnavailable_whenWhatsAppIsNotConfigured()
	{
		// %test.fuggs.whatsapp.enabled=false is the effective default here,
		// proving the dispatch wiring for the "whatsapp" case without needing
		// a live Graph API stub.
		given()
			.contentType("application/json")
			.body(new NotificationResource.NotificationRequest("whatsapp", "4917012340001",
				"Dein Kauflandbeleg wurde gerade bearbeitet."))
			.when()
			.post("/api/notifications")
			.then()
			.statusCode(503)
			.body("error", equalTo("whatsapp_unavailable"));
	}

	@Test
	void shouldRejectMissingMessage()
	{
		given()
			.contentType("application/json")
			.body(new NotificationResource.NotificationRequest("whatsapp", "4917012340001", ""))
			.when()
			.post("/api/notifications")
			.then()
			.statusCode(400)
			.body("error", equalTo("missing_fields"));
	}
}
