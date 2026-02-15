package app.fuggs.invitation.service;

import app.fuggs.invitation.domain.Invitation;
import app.fuggs.invitation.domain.InvitationRole;
import app.fuggs.invitation.domain.InvitationStatus;
import app.fuggs.invitation.repository.InvitationRepository;
import app.fuggs.member.domain.Member;
import app.fuggs.organization.domain.Organization;
import app.fuggs.shared.BaseOrganizationTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class InvitationServiceTest extends BaseOrganizationTest
{
	@Inject
	InvitationService invitationService;

	@Inject
	InvitationRepository invitationRepository;

	private Organization testOrg;
	private Member invitingMember;

	@BeforeEach
	void setupTestData()
	{
		testOrg = getOrCreateTestOrganization();
		invitingMember = createTestMember("inviter", testOrg);
		deleteAllInvitations();
	}

	@Test
	void shouldCreateInvitation()
	{
		Invitation invitation = invitationService.createInvitation(
			"test@example.com",
			InvitationRole.MEMBER,
			testOrg,
			invitingMember);

		assertNotNull(invitation);
		assertNotNull(invitation.id);
		assertEquals("test@example.com", invitation.getEmail());
		assertEquals(InvitationRole.MEMBER, invitation.getRole());
		assertEquals(InvitationStatus.PENDING, invitation.getStatus());
		assertNotNull(invitation.getToken());
		assertNotNull(invitation.getExpiresAt());
	}

	@Test
	void shouldValidateValidToken()
	{
		Invitation created = createTestInvitation("valid@example.com", InvitationRole.ADMIN);

		Invitation validated = invitationService.validateToken(created.getToken());

		assertNotNull(validated);
		assertEquals(created.id, validated.id);
	}

	@Test
	void shouldRejectInvalidToken()
	{
		assertThrows(IllegalArgumentException.class, () -> {
			invitationService.validateToken("invalid-token-12345");
		});
	}

	@Test
	void shouldRejectExpiredToken()
	{
		Invitation invitation = createTestInvitation("expired@example.com", InvitationRole.MEMBER);
		expireInvitation(invitation);

		assertThrows(IllegalArgumentException.class, () -> {
			invitationService.validateToken(invitation.getToken());
		});

		// Verify status was updated to EXPIRED
		Invitation updated = invitationRepository.findById(invitation.id);
		assertEquals(InvitationStatus.EXPIRED, updated.getStatus());
	}

	@Test
	void shouldRejectAlreadyUsedToken()
	{
		Invitation invitation = createTestInvitation("used@example.com", InvitationRole.ADMIN);
		acceptInvitation(invitation);

		assertThrows(IllegalArgumentException.class, () -> {
			invitationService.validateToken(invitation.getToken());
		});
	}

	@Test
	void shouldAcceptInvitation()
	{
		Invitation invitation = createTestInvitation("accept@example.com", InvitationRole.MEMBER);

		invitationService.acceptInvitation(invitation);

		Invitation updated = invitationRepository.findById(invitation.id);
		assertEquals(InvitationStatus.ACCEPTED, updated.getStatus());
		assertNotNull(updated.getAcceptedAt());
	}

	@Test
	void shouldCancelPendingInvitation()
	{
		Invitation invitation = createTestInvitation("cancel@example.com", InvitationRole.ADMIN);

		invitationService.cancelInvitation(invitation);

		Invitation updated = invitationRepository.findById(invitation.id);
		assertEquals(InvitationStatus.CANCELLED, updated.getStatus());
	}

	@Test
	void shouldNotCancelNonPendingInvitation()
	{
		Invitation invitation = createTestInvitation("accepted@example.com", InvitationRole.MEMBER);
		acceptInvitation(invitation);

		assertThrows(IllegalStateException.class, () -> {
			invitationService.cancelInvitation(invitation);
		});
	}

	// Helper methods

	@Transactional(TxType.REQUIRES_NEW)
	void deleteAllInvitations()
	{
		invitationRepository.deleteAll();
	}

	@Transactional(TxType.REQUIRES_NEW)
	Invitation createTestInvitation(String email, InvitationRole role)
	{
		Invitation invitation = new Invitation(email, testOrg, role, invitingMember);
		invitationRepository.persist(invitation);
		return invitation;
	}

	@Transactional(TxType.REQUIRES_NEW)
	void acceptInvitation(Invitation invitation)
	{
		Invitation managed = invitationRepository.findById(invitation.id);
		managed.setStatus(InvitationStatus.ACCEPTED);
		managed.setAcceptedAt(Instant.now());
	}

	@Transactional(TxType.REQUIRES_NEW)
	void expireInvitation(Invitation invitation)
	{
		Invitation managed = invitationRepository.findById(invitation.id);
		managed.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
	}
}
