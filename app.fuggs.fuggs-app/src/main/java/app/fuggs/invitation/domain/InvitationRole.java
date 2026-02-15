package app.fuggs.invitation.domain;

import app.fuggs.shared.security.Roles;

import java.util.List;

/**
 * Represents the role that will be assigned to a user when they accept an
 * invitation.
 */
public enum InvitationRole
{
	/**
	 * Organization administrator with full access to manage members,
	 * invitations, and settings.
	 */
	ADMIN,

	/**
	 * Regular member with access to organization resources.
	 */
	MEMBER;

	/**
	 * Converts this invitation role to the corresponding Keycloak role strings.
	 *
	 * @return List of Keycloak role names to assign
	 */
	public List<String> toKeycloakRoles()
	{
		return switch (this)
		{
			case ADMIN -> List.of(Roles.ADMIN, Roles.MEMBER);
			case MEMBER -> List.of(Roles.MEMBER);
		};
	}
}
