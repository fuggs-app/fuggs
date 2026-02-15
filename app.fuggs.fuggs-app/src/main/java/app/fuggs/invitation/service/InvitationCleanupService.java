package app.fuggs.invitation.service;

import app.fuggs.invitation.domain.Invitation;
import app.fuggs.invitation.domain.InvitationStatus;
import app.fuggs.invitation.repository.InvitationRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled service to automatically mark expired invitations. Runs daily to
 * check for pending invitations that have passed their expiration date.
 */
@ApplicationScoped
public class InvitationCleanupService
{
	private static final Logger LOG = LoggerFactory.getLogger(InvitationCleanupService.class);

	@Inject
	InvitationRepository invitationRepository;

	/**
	 * Marks expired invitations as EXPIRED. Runs daily at 2:00 AM.
	 */
	@Scheduled(cron = "0 0 2 * * ?")
	@Transactional
	public void markExpiredInvitations()
	{
		LOG.info("Starting scheduled cleanup of expired invitations");

		// Find all pending invitations
		List<Invitation> pendingInvitations = invitationRepository.list("status", InvitationStatus.PENDING);

		int expiredCount = 0;
		Instant now = Instant.now();

		for (Invitation invitation : pendingInvitations)
		{
			if (now.isAfter(invitation.getExpiresAt()))
			{
				invitation.setStatus(InvitationStatus.EXPIRED);
				invitationRepository.persist(invitation);
				expiredCount++;

				LOG.debug("Marked invitation as expired: id={}, email={}, expiresAt={}", invitation.id,
					invitation.getEmail(), invitation.getExpiresAt());
			}
		}

		LOG.info("Invitation cleanup complete: checked={}, expired={}", pendingInvitations.size(), expiredCount);
	}
}
