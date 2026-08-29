package app.fuggs.document.api;

import app.fuggs.member.domain.Member;
import app.fuggs.member.repository.MemberRepository;
import app.fuggs.organization.domain.Organization;
import app.fuggs.shared.BaseOrganizationTest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Exercises the machine-to-machine document intake endpoint the Telegram bot
 * service calls: member resolution by Telegram username, the shared-secret
 * gate, and the status lookup.
 */
@QuarkusTest
@TestProfile(BotSharedSecretTestProfile.class)
class BotDocumentResourceTest extends BaseOrganizationTest
{
	private static final String FILE_BASE64 = Base64.getEncoder()
		.encodeToString("%PDF-1.4 fake receipt bytes".getBytes(StandardCharsets.UTF_8));

	@Inject
	MemberRepository memberRepository;

	@Test
	void shouldIntakeDocumentAndReportStatus_whenTelegramUsernameIsKnown()
	{
		createMemberWithTelegramUsername("bommelwart_hugo");

		Number documentId = given()
			.header("X-Bot-Secret", BotSharedSecretTestProfile.SECRET)
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("telegram", "@Bommelwart_Hugo", "Kaufland.pdf",
				"application/pdf", FILE_BASE64))
			.when()
			.post("/api/bot/documents")
			.then()
			.log().ifValidationFails()
			.statusCode(200)
			.body("documentId", notNullValue())
			.extract().path("documentId");

		given()
			.header("X-Bot-Secret", BotSharedSecretTestProfile.SECRET)
			.when()
			.get("/api/bot/documents/" + documentId + "/status")
			.then()
			.log().ifValidationFails()
			.statusCode(200)
			.body("status", notNullValue())
			.body("complete", notNullValue());
	}

	@Test
	void shouldRejectSubmission_whenTelegramUsernameIsUnknown()
	{
		given()
			.header("X-Bot-Secret", BotSharedSecretTestProfile.SECRET)
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("telegram", "nobody_registered", "Kaufland.pdf",
				"application/pdf", FILE_BASE64))
			.when()
			.post("/api/bot/documents")
			.then()
			.statusCode(404)
			.body("error", equalTo("unknown_member"));
	}

	@Test
	void shouldRejectSubmission_whenChannelIsUnsupported()
	{
		createMemberWithTelegramUsername("channel_test_member");

		given()
			.header("X-Bot-Secret", BotSharedSecretTestProfile.SECRET)
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("whatsapp", "channel_test_member", "Kaufland.pdf",
				"application/pdf", FILE_BASE64))
			.when()
			.post("/api/bot/documents")
			.then()
			.statusCode(404)
			.body("error", equalTo("unknown_member"));
	}

	@Test
	void shouldRejectSubmission_whenSharedSecretHeaderIsMissing()
	{
		createMemberWithTelegramUsername("secret_test_member");

		given()
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("telegram", "secret_test_member", "Kaufland.pdf",
				"application/pdf", FILE_BASE64))
			.when()
			.post("/api/bot/documents")
			.then()
			.statusCode(401);
	}

	@Test
	void shouldRejectSubmission_whenSharedSecretHeaderIsWrong()
	{
		given()
			.header("X-Bot-Secret", "not-the-configured-secret")
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("telegram", "irrelevant", "Kaufland.pdf", "application/pdf",
				FILE_BASE64))
			.when()
			.post("/api/bot/documents")
			.then()
			.statusCode(401);
	}

	@Test
	void shouldRejectStatusLookup_whenSharedSecretHeaderIsWrong()
	{
		given()
			.header("X-Bot-Secret", "not-the-configured-secret")
			.when()
			.get("/api/bot/documents/1/status")
			.then()
			.statusCode(401);
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	void createMemberWithTelegramUsername(String telegramUsername)
	{
		Organization org = getOrCreateTestOrganization();
		Member member = new Member();
		member.setFirstName("Hugo");
		member.setLastName("Müller");
		member.setUserName(telegramUsername + "." + System.nanoTime());
		member.setTelegramUsername(telegramUsername);
		member.setOrganization(org);
		memberRepository.persist(member);
	}
}
