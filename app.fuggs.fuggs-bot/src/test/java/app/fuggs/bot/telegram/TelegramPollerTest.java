package app.fuggs.bot.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * Drives one poll cycle against a stubbed Telegram API to prove the full
 * receive-parse-reply round trip without touching the live service.
 */
@QuarkusTest
@TestProfile(WireMockTestProfile.class)
@ConnectWireMock
class TelegramPollerTest
{
	private static final String TOKEN = WireMockTestProfile.TEST_TOKEN;

	WireMock wireMock;

	@Inject
	TelegramPoller poller;

	@BeforeEach
	void resetWireMock()
	{
		wireMock.resetRequests();
		wireMock.resetMappings();
	}

	@Test
	void shouldAcknowledgeAnIncomingPdfDocument()
	{
		wireMock.register(get(urlPathEqualTo("/bot" + TOKEN + "/getUpdates"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{
						"ok": true,
						"result": [{
							"update_id": 700001,
							"message": {
								"message_id": 42,
								"from": {
									"id": 555,
									"is_bot": false,
									"first_name": "Sascha",
									"username": "sascha_test"
								},
								"chat": { "id": 555, "type": "private" },
								"document": {
									"file_id": "BQACAgIAAx",
									"file_name": "Kaufland.pdf",
									"mime_type": "application/pdf",
									"file_size": 20481
								}
							}
						}]
					}
					""")));

		wireMock.register(com.github.tomakehurst.wiremock.client.WireMock
			.post(urlPathEqualTo("/bot" + TOKEN + "/sendMessage"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody("{\"ok\": true, \"result\": {}}")));

		poller.pollOnce();

		wireMock.verifyThat(postRequestedFor(urlPathEqualTo("/bot" + TOKEN + "/sendMessage"))
			.withRequestBody(containing("Kaufland.pdf"))
			.withRequestBody(containing("\"chat_id\":555")));
	}

	@Test
	void shouldIgnoreUpdatesWithoutAMessage()
	{
		wireMock.register(get(urlPathEqualTo("/bot" + TOKEN + "/getUpdates"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{"ok": true, "result": [{"update_id": 700002}]}
					""")));

		poller.pollOnce();
		// No sendMessage stub registered - a reply attempt would fail the poll,
		// which the poller logs; absence of a request is the assertion.
		wireMock.verifyThat(0, postRequestedFor(urlPathEqualTo("/bot" + TOKEN + "/sendMessage")));
	}
}
