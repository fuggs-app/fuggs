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
	void shouldRejectSubmission_whenChannelIsUnsupported()
	{
		given()
			.header("X-Bot-Secret", BotSharedSecretTestProfile.SECRET)
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("signal", "channel_test_member", null, "Kaufland.pdf",
				"application/pdf", FILE_BASE64))
			.when()
			.post("/api/bot/documents")
			.then()
			.statusCode(404)
			.body("error", equalTo("unknown_member"));
	}

	@Test
	void shouldIntakeDocumentAndReportStatus_whenWhatsAppPhoneIsKnown()
	{
		createMemberWithWhatsAppPhone("4917012340001");

		Number documentId = given()
			.header("X-Bot-Secret", BotSharedSecretTestProfile.SECRET)
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("whatsapp", "+49 170 1234 0001", null, "Kaufland.pdf",
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
	void shouldRejectSubmission_whenWhatsAppPhoneIsUnknown()
	{
		given()
			.header("X-Bot-Secret", BotSharedSecretTestProfile.SECRET)
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("whatsapp", "4917099999999", null, "Kaufland.pdf",
				"application/pdf", FILE_BASE64))
			.when()
			.post("/api/bot/documents")
			.then()
			.statusCode(404)
			.body("error", equalTo("unknown_member"))
			.body("message", equalTo(MOCKED_UNKNOWN_SENDER_MESSAGE));
	}

	@Test
	void shouldAcceptSubmission_whenWhatsAppProvidesAnUnusedPushAddress()
	{
		// WhatsApp has no separate chat id - the phone number already doubles
		// as the push address - so a non-null pushAddress here must be
		// harmlessly ignored rather than rejected.
		createMemberWithWhatsAppPhone("4917012340002");

		given()
			.header("X-Bot-Secret", BotSharedSecretTestProfile.SECRET)
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("whatsapp", "4917012340002", "irrelevant", "Kaufland.pdf",
				"application/pdf", FILE_BASE64))
			.when()
			.post("/api/bot/documents")
			.then()
			.statusCode(200);
	}

	@Test
	void shouldRejectSubmission_whenSharedSecretHeaderIsMissing()
	{
		createMemberWithWhatsAppPhone("4917012340003");

		given()
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("whatsapp", "4917012340003", null, "Kaufland.pdf",
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
			.body(new BotDocumentResource.IntakeRequest("whatsapp", "irrelevant", null, "Kaufland.pdf",
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
	Long createMemberWithWhatsAppPhone(String phone)
	{
		Organization org = getOrCreateTestOrganization();
		Member member = new Member();
		member.setFirstName("Hugo");
		member.setLastName("Müller");
		member.setUserName("whatsapp." + phone + "." + System.nanoTime());
		member.setPhone(phone);
		member.setOrganization(org);
		memberRepository.persist(member);
		return member.getId();
	}
}
