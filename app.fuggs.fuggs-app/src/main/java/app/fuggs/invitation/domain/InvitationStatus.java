package app.fuggs.invitation.domain;

/**
 * Represents the status of an invitation in its lifecycle.
 */
public enum InvitationStatus
{
	/**
	 * Invitation has been sent and is awaiting acceptance.
	 */
	PENDING,

	/**
	 * Invitation has been accepted and user account has been created.
	 */
	ACCEPTED,

	/**
	 * Invitation has expired and can no longer be used.
	 */
	EXPIRED,

	/**
	 * Invitation has been cancelled by the inviter or admin.
	 */
	CANCELLED
}
