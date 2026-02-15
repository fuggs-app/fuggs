package app.fuggs.invitation.model;

import app.fuggs.invitation.domain.InvitationRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for creating a new invitation.
 */
public class InvitationData
{
	@NotBlank(message = "Email ist erforderlich")
	@Email(message = "Ungültige E-Mail-Adresse")
	private String email;

	@NotNull(message = "Rolle ist erforderlich")
	private InvitationRole role;

	public InvitationData()
	{
	}

	public InvitationData(String email, InvitationRole role)
	{
		this.email = email;
		this.role = role;
	}

	public String getEmail()
	{
		return email;
	}

	public void setEmail(String email)
	{
		this.email = email;
	}

	public InvitationRole getRole()
	{
		return role;
	}

	public void setRole(InvitationRole role)
	{
		this.role = role;
	}
}
