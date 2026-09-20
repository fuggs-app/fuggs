package app.fuggs.bot.whatsapp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

/**
 * Drives real HTTP requests against the webhook endpoint with a stubbed Graph
 * API and a stubbed fuggs-app to prove the full verify-signature-download-
 * forward-reply round trip without touching either live service.
 */
@QuarkusTest
@TestProfile(WhatsAppWireMockTestProfile.class)
@ConnectWireMock
class WhatsAppWebhookResourceTest
{
	private static final String SEND_MESSAGE_PATH = "/" + WhatsAppWireMockTestProfile.TEST_PHONE_NUMBER_ID
		+ "/messages";

	WireMock wireMock;

	@BeforeEach
	void resetWireMock()
	{
		wireMock.resetRequests();
		wireMock.resetMappings();
	}

	@Test
	void shouldEchoChallenge_whenVerifyTokenMatches()
	{
		given()
			.queryParam("hub.mode", "subscribe")
			.queryParam("hub.verify_token", WhatsAppWireMockTestProfile.TEST_VERIFY_TOKEN)
			.queryParam("hub.challenge", "1158201444")
			.when()
			.get("/api/whatsapp/webhook")
			.then()
			.statusCode(200)
			.body(equalTo("1158201444"));
	}

	@Test
	void shouldRejectVerification_whenTokenIsWrong()
	{
		given()
			.queryParam("hub.mode", "subscribe")
			.queryParam("hub.verify_token", "wrong-token")
			.queryParam("hub.challenge", "1158201444")
			.when()
			.get("/api/whatsapp/webhook")
			.then()
			.statusCode(403);
	}

	@Test
	void shouldRejectPayload_whenSignatureHeaderIsMissing()
	{
		given()
			.contentType(ContentType.JSON)
			.body(documentPayload("700001", "wamid.test001", "4917012340001", "MEDIA_1", "Kaufland.pdf"))
			.when()
			.post("/api/whatsapp/webhook")
			.then()
			.statusCode(401);
	}

	@Test
	void shouldRejectPayload_whenSignatureIsWrong()
	{
		String body = documentPayload("700002", "wamid.test002", "4917012340001", "MEDIA_1", "Kaufland.pdf");

		given()
			.contentType(ContentType.JSON)
			.header("X-Hub-Signature-256", "sha256=deadbeef")
			.body(body)
			.when()
			.post("/api/whatsapp/webhook")
			.then()
			.statusCode(401);
	}

	@Test
	void shouldIgnoreTextOnlyMessages()
	{
		String body = textPayload("700003", "wamid.test003", "4917012340001", "hallo");

		postSigned(body).then().statusCode(200);

		wireMock.verifyThat(0, postRequestedFor(urlPathEqualTo(SEND_MESSAGE_PATH)));
	}

	@Test
	void shouldRejectOversizedMedia_withoutCallingFuggsApp()
	{
		stubGetMediaUrl("MEDIA_BIG", downloadUrl("MEDIA_BIG"), 25L * 1024 * 1024);
		stubSendMessage();

		String body = documentPayload("700004", "wamid.test004", "4917012340001", "MEDIA_BIG", "Riesig.pdf");
		postSigned(body).then().statusCode(200);

		awaitSendMessageContaining("4 MB");
		wireMock.verifyThat(0, postRequestedFor(urlPathEqualTo("/api/bot/documents")));
	}

	@Test
	void shouldReplySuccess_whenForwardedDocumentIsAnalyzedSuccessfully()
	{
		stubGetMediaUrl("MEDIA_OK", downloadUrl("MEDIA_OK"), 20481L);
		stubMediaDownload("MEDIA_OK");
		stubSubmitDocument(200, "{\"documentId\": 42}");
		stubStatus(42, """
			{
				"status": "COMPLETED",
				"complete": true,
				"error": null,
				"name": "Kaufland",
				"total": 12.34,
				"currencyCode": "EUR",
				"message": "Danke, dass du den Kaufland-Beleg hochgeladen hast! Dein Bommelwart wurde schon informiert."
			}
			""");
		stubSendMessage();

		String body = documentPayload("700005", "wamid.test005", "4917012340001", "MEDIA_OK", "Kaufland.pdf");
		postSigned(body).then().statusCode(200);

		awaitSendMessageContaining("Kaufland");
		wireMock.verifyThat(postRequestedFor(urlPathEqualTo(SEND_MESSAGE_PATH))
			.withRequestBody(containing("Bommelwart wurde schon informiert")));
	}

	@Test
	void shouldReplyUnknownSender_whenFuggsAppDoesNotKnowTheMember()
	{
		stubGetMediaUrl("MEDIA_UNKNOWN", downloadUrl("MEDIA_UNKNOWN"), 20481L);
		stubMediaDownload("MEDIA_UNKNOWN");
		stubSubmitDocument(404, """
			{
				"error": "unknown_member",
				"message": "Wir konnten dein Konto bei Fuggs nicht finden, wende dich bitte an deinen Bommelwart."
			}
			""");
		stubSendMessage();

		String body = documentPayload("700006", "wamid.test006", "4917099999999", "MEDIA_UNKNOWN", "Kaufland.pdf");
		postSigned(body).then().statusCode(200);

		awaitSendMessageContaining("Bommelwart");
	}

	@Test
	void shouldIgnoreRedeliveredMessage_withSameMessageId()
	{
		stubGetMediaUrl("MEDIA_DUP", downloadUrl("MEDIA_DUP"), 20481L);
		stubMediaDownload("MEDIA_DUP");
		stubSubmitDocument(200, "{\"documentId\": 77}");
		stubStatus(77, """
			{"status": "COMPLETED", "complete": true, "error": null, "name": "Kaufland", "total": 1.0, "currencyCode": "EUR", "message": "ok"}
			""");
		stubSendMessage();

		String body = documentPayload("700007", "wamid.test007-dup", "4917012340001", "MEDIA_DUP", "Kaufland.pdf");
		postSigned(body).then().statusCode(200);
		awaitSendMessageContaining("ok");

		wireMock.resetRequests();
		// Duplicate message id: the dedup check runs synchronously before any
		// async work is spawned, so by the time this request completes,
		// nothing has been (or ever will be) submitted for it.
		postSigned(body).then().statusCode(200);

		wireMock.verifyThat(0, postRequestedFor(urlPathEqualTo("/api/bot/documents")));
	}

	private String downloadUrl(String mediaId)
	{
		return "http://localhost:" + WhatsAppWireMockTestProfile.WIREMOCK_PORT + "/media-download/" + mediaId;
	}

	private io.restassured.response.Response postSigned(String body)
	{
		return given()
			.contentType(ContentType.JSON)
			.header("X-Hub-Signature-256", sign(body))
			.body(body)
			.when()
			.post("/api/whatsapp/webhook");
	}

	private String sign(String body)
	{
		try
		{
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(WhatsAppWireMockTestProfile.TEST_APP_SECRET.getBytes(StandardCharsets.UTF_8),
				"HmacSHA256"));
			byte[] computed = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
			return "sha256=" + HexFormat.of().formatHex(computed);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	private String documentPayload(String entryId, String messageId, String from, String mediaId, String fileName)
	{
		return """
			{
				"object": "whatsapp_business_account",
				"entry": [{
					"id": "%s",
					"changes": [{
						"field": "messages",
						"value": {
							"messaging_product": "whatsapp",
							"messages": [{
								"from": "%s",
								"id": "%s",
								"type": "document",
								"document": {
									"id": "%s",
									"mime_type": "application/pdf",
									"filename": "%s"
								}
							}]
						}
					}]
				}]
			}
			""".formatted(entryId, from, messageId, mediaId, fileName);
	}

	private String textPayload(String entryId, String messageId, String from, String text)
	{
		return """
			{
				"object": "whatsapp_business_account",
				"entry": [{
					"id": "%s",
					"changes": [{
						"field": "messages",
						"value": {
							"messaging_product": "whatsapp",
							"messages": [{
								"from": "%s",
								"id": "%s",
								"type": "text",
								"text": {"body": "%s"}
							}]
						}
					}]
				}]
			}
			""".formatted(entryId, from, messageId, text);
	}

	private void stubGetMediaUrl(String mediaId, String url, long fileSize)
	{
		wireMock.register(get(urlPathEqualTo("/" + mediaId))
			.willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{"url": "%s", "mime_type": "application/pdf", "file_size": %d, "id": "%s"}
					""".formatted(url, fileSize, mediaId))));
	}

	private void stubMediaDownload(String mediaId)
	{
		wireMock.register(get(urlPathEqualTo("/media-download/" + mediaId))
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
				.withBody("{\"messaging_product\": \"whatsapp\", \"messages\": [{\"id\": \"wamid.reply\"}]}")));
	}

	/**
	 * The webhook hands document processing off to a background virtual thread,
	 * so the reply may arrive slightly after the POST returns. Polls the
	 * WireMock journal instead of adding a fixed sleep.
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
