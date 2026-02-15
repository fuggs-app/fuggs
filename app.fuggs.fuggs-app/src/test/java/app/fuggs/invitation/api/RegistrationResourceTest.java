package app.fuggs.invitation.api;

import app.fuggs.invitation.domain.Invitation;
import app.fuggs.invitation.domain.InvitationRole;
import app.fuggs.invitation.domain.InvitationStatus;
import app.fuggs.invitation.repository.InvitationRepository;
import app.fuggs.member.domain.Member;
import app.fuggs.member.repository.MemberRepository;
import app.fuggs.organization.domain.Organization;
import app.fuggs.organization.repository.OrganizationRepository;
import app.fuggs.shared.BaseOrganizationTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class RegistrationResourceTest extends BaseOrganizationTest
{
	@Inject
	InvitationRepository invitationRepository;

	@Inject
	MemberRepository memberRepository;

	@Inject
	OrganizationRepository organizationRepository;

	private Organization testOrg;
	private Member invitingMember;

	@BeforeEach
	void setupTestData()
	{
		testOrg = getOrCreateTestOrganization();
		invitingMember = getOrCreateInvitingMember();
		deleteAllInvitations();
		deleteTestMembers();
	}

	@Test
	void shouldShowRegistrationFormWithValidToken()
	{
		Invitation invitation = createFounderInvitation("founder@example.com");

		given()
			.queryParam("token", invitation.getToken())
			.when().get("/registrierung")
			.then()
			.statusCode(200)
			.body(containsString("Willkommen bei Fuggs"))
			.body(containsString("founder@example.com"));
	}

	@Test
	void shouldShowInvalidPageForExpiredToken()
	{
		Invitation invitation = createFounderInvitation("expired@example.com");
		expireInvitation(invitation);

		given()
			.queryParam("token", invitation.getToken())
			.when().get("/registrierung")
			.then()
			.statusCode(200)
			.body(containsString("Ungültiger Einladungslink"));
	}

	@Test
	void shouldShowInvalidPageForMissingToken()
	{
		given()
			.when().get("/registrierung")
			.then()
			.statusCode(200)
			.body(containsString("Kein Einladungstoken angegeben"));
	}

	@Test
	void shouldShowInvalidPageForNonExistentToken()
	{
		given()
			.queryParam("token", "non-existent-token-12345")
			.when().get("/registrierung")
			.then()
			.statusCode(200)
			.body(containsString("Ungültiger Einladungslink"));
	}

	@Test
	void shouldShowOrganizationNameFieldForFounder()
	{
		Invitation invitation = createFounderInvitation("founder2@example.com");

		given()
			.queryParam("token", invitation.getToken())
			.when().get("/registrierung")
			.then()
			.statusCode(200)
			.body(containsString("Organisationsname"))
			.body(containsString("Organisation gründen"));
	}

	@Test
	void shouldShowOrganizationInfoForMember()
	{
		Invitation invitation = createMemberInvitation("member@example.com");

		given()
			.queryParam("token", invitation.getToken())
			.when().get("/registrierung")
			.then()
			.statusCode(200)
			.body(containsString("Test Organization"))
			.body(containsString("beitreten"));
	}

	@Test
	void shouldShowSuccessPageAfterRegistration()
	{
		given()
			.when().get("/registrierung/erfolg")
			.then()
			.statusCode(200)
			.body(containsString("Registrierung erfolgreich"))
			.body(containsString("Jetzt anmelden"));
	}

	// Helper methods

	@Transactional(TxType.REQUIRES_NEW)
	Member getOrCreateInvitingMember()
	{
		Member member = memberRepository.findByUsername("inviter");
		if (member == null)
		{
			member = new Member();
			member.setUserName("inviter");
			member.setEmail("inviter@test.local");
			member.setFirstName("Test");
			member.setLastName("Inviter");
			member.setOrganization(testOrg);
			memberRepository.persist(member);
		}
		return member;
	}

	@Transactional(TxType.REQUIRES_NEW)
	void deleteAllInvitations()
	{
		invitationRepository.deleteAll();
	}

	@Transactional(TxType.REQUIRES_NEW)
	void deleteTestMembers()
	{
		// Clean up test members (not the inviting member)
		memberRepository.delete("email like ?1 AND userName != ?2", "%@example.com", "inviter");
	}

	@Transactional(TxType.REQUIRES_NEW)
	Invitation createFounderInvitation(String email)
	{
		// Founder invitations have no organization assigned
		Invitation invitation = new Invitation();
		invitation.setEmail(email);
		invitation.setToken(java.util.UUID.randomUUID().toString());
		invitation.setRole(InvitationRole.ADMIN);
		invitation.setStatus(InvitationStatus.PENDING);
		invitation.setInvitedBy(invitingMember);
		invitation.setExpiresAt(java.time.Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS));
		invitationRepository.persist(invitation);
		return invitation;
	}

	@Transactional(TxType.REQUIRES_NEW)
	Invitation createMemberInvitation(String email)
	{
		Invitation invitation = new Invitation(email, testOrg, InvitationRole.MEMBER, invitingMember);
		invitationRepository.persist(invitation);
		return invitation;
	}

	@Transactional(TxType.REQUIRES_NEW)
	void expireInvitation(Invitation invitation)
	{
		Invitation managed = invitationRepository.findById(invitation.id);
		managed.setExpiresAt(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));
	}
}
