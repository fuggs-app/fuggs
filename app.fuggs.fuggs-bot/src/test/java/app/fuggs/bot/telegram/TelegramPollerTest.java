package app.fuggs.bot.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
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
 * Drives one poll cycle against a stubbed Telegram API and a stubbed fuggs-app
 * to prove the full receive-download-forward-reply round trip without touching
 * either live service.
 */
@QuarkusTest
@TestProfile(WireMockTestProfile.class)
@ConnectWireMock
class TelegramPollerTest
{
	private static final String TOKEN = WireMockTestProfile.TEST_TOKEN;
	private static final String SEND_MESSAGE_PATH = "/bot" + TOKEN + "/sendMessage";

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
	void shouldAskForAUsername_whenSenderHasNoTelegramUsername()
	{
		stubGetUpdates("""
			{
				"ok": true,
				"result": [{
					"update_id": 700001,
					"message": {
						"message_id": 42,
						"from": { "id": 555, "is_bot": false, "first_name": "Sascha" },
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
			""");
		stubSendMessage();

		poller.pollOnce();

		wireMock.verifyThat(postRequestedFor(urlPathEqualTo(SEND_MESSAGE_PATH))
			.withRequestBody(containing("Benutzernamen")));
	}

	@Test
	void shouldIgnoreUpdatesWithoutAMessage()
	{
		stubGetUpdates("""
			{"ok": true, "result": [{"update_id": 700002}]}
			""");

		poller.pollOnce();
		// No sendMessage stub registered - a reply attempt would fail the poll,
		// which the poller logs; absence of a request is the assertion.
		wireMock.verifyThat(0, postRequestedFor(urlPathEqualTo(SEND_MESSAGE_PATH)));
	}

	@Test
	void shouldIgnoreTextOnlyMessages()
	{
		stubGetUpdates("""
			{
				"ok": true,
				"result": [{
					"update_id": 700003,
					"message": {
						"message_id": 43,
						"from": { "id": 555, "is_bot": false, "username": "sascha_test" },
						"chat": { "id": 555, "type": "private" },
						"text": "hallo"
					}
				}]
			}
			""");

		poller.pollOnce();

		wireMock.verifyThat(0, postRequestedFor(urlPathEqualTo(SEND_MESSAGE_PATH)));
	}

	@Test
	void shouldRejectOversizedFiles_withoutCallingFuggsApp()
	{
		stubGetUpdates(documentUpdate(700004, "sascha_test", "Riesig.pdf", 25L * 1024 * 1024));
		stubSendMessage();

		poller.pollOnce();

		awaitSendMessageContaining("20 MB");
		wireMock.verifyThat(0, postRequestedFor(urlPathEqualTo("/api/bot/documents")));
	}

	@Test
	void shouldReplySuccess_whenForwardedDocumentIsAnalyzedSuccessfully()
	{
		stubGetUpdates(documentUpdate(700005, "sascha_test", "Kaufland.pdf", 20481));
		stubGetFile("BQACAgIAAx", "documents/Kaufland.pdf");
		stubFileDownload("documents/Kaufland.pdf");
		stubSubmitDocument(200, "{\"documentId\": 42}");
		stubStatus(42, """
			{
				"status": "COMPLETED",
				"complete": true,
				"error": null,
				"name": "Kaufland",
				"total": 12.34,
				"currencyCode": "EUR"
			}
			""");
		stubSendMessage();

		poller.pollOnce();

		awaitSendMessageContaining("Kaufland");
		wireMock.verifyThat(postRequestedFor(urlPathEqualTo(SEND_MESSAGE_PATH))
			.withRequestBody(containing("erfolgreich")));
	}

	@Test
	void shouldReplyFailure_whenForwardedDocumentAnalysisFails()
	{
		stubGetUpdates(documentUpdate(700006, "sascha_test", "Kaputt.pdf", 20481));
		stubGetFile("BQACAgIAAx", "documents/Kaputt.pdf");
		stubFileDownload("documents/Kaputt.pdf");
		stubSubmitDocument(200, "{\"documentId\": 43}");
		stubStatus(43, """
			{
				"status": "FAILED",
				"complete": true,
				"error": "KI-Analyse fehlgeschlagen",
				"name": null,
				"total": null,
				"currencyCode": null
			}
			""");
		stubSendMessage();

		poller.pollOnce();

		awaitSendMessageContaining("fehlgeschlagen");
	}

	@Test
	void shouldReplyUnknownSender_whenFuggsAppDoesNotKnowTheMember()
	{
		stubGetUpdates(documentUpdate(700007, "unregistered_user", "Kaufland.pdf", 20481));
		stubGetFile("BQACAgIAAx", "documents/Kaufland.pdf");
		stubFileDownload("documents/Kaufland.pdf");
		stubSubmitDocument(404, "{\"error\": \"unknown_member\"}");
		stubSendMessage();

		poller.pollOnce();

		awaitSendMessageContaining("zugeordnet");
	}

	private String documentUpdate(long updateId, String username, String fileName, long fileSize)
	{
		return """
			{
				"ok": true,
				"result": [{
					"update_id": %d,
					"message": {
						"message_id": 1,
						"from": { "id": 555, "is_bot": false, "username": "%s" },
						"chat": { "id": 555, "type": "private" },
						"document": {
							"file_id": "BQACAgIAAx",
							"file_name": "%s",
							"mime_type": "application/pdf",
							"file_size": %d
						}
					}
				}]
			}
			""".formatted(updateId, username, fileName, fileSize);
	}

	private void stubGetUpdates(String body)
	{
		wireMock.register(get(urlPathEqualTo("/bot" + TOKEN + "/getUpdates"))
			.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)));
	}

	private void stubGetFile(String fileId, String filePath)
	{
		wireMock.register(get(urlPathEqualTo("/bot" + TOKEN + "/getFile"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{"ok": true, "result": {"file_id": "%s", "file_size": 20481, "file_path": "%s"}}
					""".formatted(fileId, filePath))));
	}

	private void stubFileDownload(String filePath)
	{
		wireMock.register(get(urlPathEqualTo("/file/bot" + TOKEN + "/" + filePath))
			.willReturn(aResponse()
				.withHeader("Content-Type", "application/pdf")
				.withBody("%PDF-1.4 fake receipt bytes")));
	}

	private void stubSubmitDocument(int status, String body)
	{
		wireMock.register(post(urlPathEqualTo("/api/bot/documents"))
			.willReturn(aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody(body)));
	}

	private void stubStatus(long documentId, String body)
	{
		wireMock.register(get(urlPathEqualTo("/api/bot/documents/" + documentId + "/status"))
			.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)));
	}

	private void stubSendMessage()
	{
		wireMock.register(post(urlPathEqualTo(SEND_MESSAGE_PATH))
			.willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody("{\"ok\": true, \"result\": {}}")));
	}

	/**
	 * The poller hands document processing off to a background virtual thread,
	 * so the reply may arrive slightly after {@code pollOnce()} returns. Polls
	 * the WireMock journal instead of adding a fixed sleep.
	 */
	private void awaitSendMessageContaining(String bodyFragment)
	{
		long deadline = System.currentTimeMillis() + 5000;
		AssertionError lastFailure = null;
		while (System.currentTimeMillis() < deadline)
		{
			try
			{
				wireMock.verifyThat(postRequestedFor(urlPathEqualTo(SEND_MESSAGE_PATH))
					.withRequestBody(containing(bodyFragment)));
				return;
			}
			catch (AssertionError e)
			{
				// WireMock's VerificationException extends AssertionError, not
				// RuntimeException - caught here so the poll loop can retry.
				lastFailure = e;
				try
				{
					Thread.sleep(100);
				}
				catch (InterruptedException interrupted)
				{
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
		if (lastFailure != null)
		{
			throw lastFailure;
		}
		throw new AssertionError("Timed out waiting for sendMessage containing: " + bodyFragment);
	}
}
