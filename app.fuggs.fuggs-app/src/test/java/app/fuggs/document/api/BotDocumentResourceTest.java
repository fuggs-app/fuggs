package app.fuggs.document.api;

import app.fuggs.member.domain.Member;
import app.fuggs.member.repository.MemberRepository;
import app.fuggs.messaging.BotMessageService;
import app.fuggs.organization.domain.Organization;
import app.fuggs.shared.BaseOrganizationTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Exercises the machine-to-machine document intake endpoint every chat bot
 * channel calls: member resolution by channel + sender identifier, the
 * shared-secret gate, the status lookup, and the AC #1/#2 LLM-generated
 * messages (issue #94). {@link BotMessageService} is mocked so these tests stay
 * fast and offline - the actual LLM prompts are exercised separately by
 * {@code BotNotificationServiceTest}.
 */
@QuarkusTest
@TestProfile(BotSharedSecretTestProfile.class)
class BotDocumentResourceTest extends BaseOrganizationTest
{
	private static final String FILE_BASE64 = Base64.getEncoder()
		.encodeToString("%PDF-1.4 fake receipt bytes".getBytes(StandardCharsets.UTF_8));

	private static final String MOCKED_UNKNOWN_SENDER_MESSAGE = "Wir konnten dein Konto bei Fuggs nicht finden.";
	private static final String MOCKED_UPLOAD_ACK_MESSAGE = "Danke, dein Beleg wurde hochgeladen.";

	@Inject
	MemberRepository memberRepository;

	@InjectMock
	BotMessageService botMessageService;

	@BeforeEach
	void setupMocks()
	{
		Mockito.when(botMessageService.unknownSenderMessage()).thenReturn(MOCKED_UNKNOWN_SENDER_MESSAGE);
		Mockito.when(botMessageService.uploadAcknowledgedMessage(Mockito.anyString()))
			.thenReturn(MOCKED_UPLOAD_ACK_MESSAGE);
	}

	@Test
	void shouldIntakeDocumentAndReportStatus_whenTelegramUsernameIsKnown()
	{
		createMemberWithTelegramUsername("bommelwart_hugo");

		Number documentId = given()
			.header("X-Bot-Secret", BotSharedSecretTestProfile.SECRET)
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("telegram", "@Bommelwart_Hugo", null, "Kaufland.pdf",
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
	void shouldCaptureTelegramChatId_whenPushAddressProvided()
	{
		Long memberId = createMemberWithTelegramUsername("chatid_test_member");

		given()
			.header("X-Bot-Secret", BotSharedSecretTestProfile.SECRET)
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("telegram", "chatid_test_member", "987654",
				"Kaufland.pdf", "application/pdf", FILE_BASE64))
			.when()
			.post("/api/bot/documents")
			.then()
			.statusCode(200);

		Member member = memberRepository.findById(memberId);
		org.junit.jupiter.api.Assertions.assertEquals(987654L, member.getTelegramChatId());
	}

	@Test
	void shouldRejectSubmission_whenTelegramUsernameIsUnknown()
	{
		given()
			.header("X-Bot-Secret", BotSharedSecretTestProfile.SECRET)
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("telegram", "nobody_registered", null, "Kaufland.pdf",
				"application/pdf", FILE_BASE64))
			.when()
			.post("/api/bot/documents")
			.then()
			.statusCode(404)
			.body("error", equalTo("unknown_member"))
			.body("message", equalTo(MOCKED_UNKNOWN_SENDER_MESSAGE));
	}

	@Test
	void shouldRejectSubmission_whenChannelIsUnsupported()
	{
		createMemberWithTelegramUsername("channel_test_member");

		given()
			.header("X-Bot-Secret", BotSharedSecretTestProfile.SECRET)
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("whatsapp", "channel_test_member", null, "Kaufland.pdf",
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
			.body(new BotDocumentResource.IntakeRequest("telegram", "secret_test_member", null, "Kaufland.pdf",
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
			.body(new BotDocumentResource.IntakeRequest("telegram", "irrelevant", null, "Kaufland.pdf",
				"application/pdf", FILE_BASE64))
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
	Long createMemberWithTelegramUsername(String telegramUsername)
	{
		Organization org = getOrCreateTestOrganization();
		Member member = new Member();
		member.setFirstName("Hugo");
		member.setLastName("Müller");
		member.setUserName(telegramUsername + "." + System.nanoTime());
		member.setTelegramUsername(telegramUsername);
		member.setOrganization(org);
		memberRepository.persist(member);
		return member.getId();
	}
}
