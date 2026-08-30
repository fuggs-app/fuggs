package app.fuggs.bot.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import app.fuggs.bot.telegram.WireMockTestProfile;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Exercises {@code POST /api/notifications}, the endpoint fuggs-app calls to
 * push a proactive message (issue #94 AC #3) through this bot service.
 */
@QuarkusTest
@TestProfile(WireMockTestProfile.class)
@ConnectWireMock
class NotificationResourceTest
{
	private static final String TOKEN = WireMockTestProfile.TEST_TOKEN;
	private static final String SEND_MESSAGE_PATH = "/bot" + TOKEN + "/sendMessage";

	WireMock wireMock;

	@BeforeEach
	void resetWireMock()
	{
		wireMock.resetRequests();
		wireMock.resetMappings();
	}

	@Test
	void shouldDeliverViaTelegram_whenChannelIsTelegram()
	{
		wireMock.register(post(urlPathEqualTo(SEND_MESSAGE_PATH))
			.willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody("{\"ok\": true, \"result\": {}}")));

		given()
			.contentType("application/json")
			.body(new NotificationResource.NotificationRequest("telegram", "555",
				"Dein Kauflandbeleg wurde gerade bearbeitet."))
			.when()
			.post("/api/notifications")
			.then()
			.statusCode(200);

		wireMock.verifyThat(postRequestedFor(urlPathEqualTo(SEND_MESSAGE_PATH))
			.withRequestBody(equalToJson(
				"{\"chat_id\": 555, \"text\": \"Dein Kauflandbeleg wurde gerade bearbeitet.\"}")));
	}

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
		// WireMockTestProfile only configures Telegram; %test.fuggs.whatsapp.
		// enabled=false is the effective default here, proving the dispatch
		// wiring for the "whatsapp" case without needing a live Graph API stub
		// (the actual send call is exercised by WhatsAppWebhookResourceTest).
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
	void shouldRejectNonNumericTelegramRecipient()
	{
		given()
			.contentType("application/json")
			.body(new NotificationResource.NotificationRequest("telegram", "not-a-chat-id", "egal"))
			.when()
			.post("/api/notifications")
			.then()
			.statusCode(400)
			.body("error", equalTo("invalid_recipient"));
	}

	@Test
	void shouldRejectMissingMessage()
	{
		given()
			.contentType("application/json")
			.body(new NotificationResource.NotificationRequest("telegram", "555", ""))
			.when()
			.post("/api/notifications")
			.then()
			.statusCode(400)
			.body("error", equalTo("missing_fields"));
	}
}
