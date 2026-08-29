package app.fuggs.document.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.fuggs.member.domain.Member;
import app.fuggs.member.repository.MemberRepository;
import app.fuggs.organization.domain.Organization;
import app.fuggs.shared.BaseOrganizationTest;
import app.fuggs.shared.TestSecurityHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

/**
 * Closes the loop promised by {@code docs/plan-telegram-bot.md}: once a member
 * has a Telegram username (settable via
 * {@link app.fuggs.member.api.MemberResource}), a document submitted through
 * the bot intake endpoint must show up in the same org-scoped document list a
 * manual upload would produce - not just get persisted somewhere invisible to
 * the Bommelwart.
 */
@QuarkusTest
@TestSecurity(user = TestSecurityHelper.TEST_USER_MARIA, roles = "user")
class TelegramBotIntakeVisibilityTest extends BaseOrganizationTest
{
	private static final String FILE_BASE64 = Base64.getEncoder()
		.encodeToString("%PDF-1.4 fake receipt bytes".getBytes(StandardCharsets.UTF_8));

	@Inject
	MemberRepository memberRepository;

	private Organization organization;

	@BeforeEach
	@Transactional(Transactional.TxType.REQUIRES_NEW)
	void setupOrganizationContext()
	{
		organization = organizationRepository.findBySlug("musikverein-harmonie");
		if (organization == null)
		{
			organization = getOrCreateTestOrganization();
		}
		createTestMember(TestSecurityHelper.TEST_USER_MARIA, organization);
	}

	@Test
	void shouldShowBotUploadedDocumentInDocumentList()
	{
		createMemberWithTelegramUsername("hugo_visibility_test");

		Number documentId = given()
			.contentType("application/json")
			.body(new BotDocumentResource.IntakeRequest("telegram", "hugo_visibility_test", "Kaufland.pdf",
				"application/pdf", FILE_BASE64))
			.when()
			.post("/api/bot/documents")
			.then()
			.log().ifValidationFails()
			.statusCode(200)
			.extract().path("documentId");

		given()
			.when()
			.get("/belege")
			.then()
			.statusCode(200)
			.body(containsString("Beleg #" + documentId));
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	void createMemberWithTelegramUsername(String telegramUsername)
	{
		Member member = new Member();
		member.setFirstName("Hugo");
		member.setLastName("Müller");
		member.setUserName(telegramUsername + "." + System.nanoTime());
		member.setTelegramUsername(telegramUsername);
		member.setOrganization(organization);
		memberRepository.persist(member);
	}
}
