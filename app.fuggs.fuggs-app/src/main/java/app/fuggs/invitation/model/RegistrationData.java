package app.fuggs.invitation.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for user registration.
 */
public class RegistrationData
{
	@NotBlank(message = "Vorname ist erforderlich")
	private String firstName;

	@NotBlank(message = "Nachname ist erforderlich")
	private String lastName;

	@NotBlank(message = "E-Mail ist erforderlich")
	@Email(message = "Ungültige E-Mail-Adresse")
	private String email;

	@NotBlank(message = "Passwort ist erforderlich")
	@Size(min = 8, message = "Passwort muss mindestens 8 Zeichen lang sein")
	private String password;

	/**
	 * Optional: Only required for founder registration (InviteType.FOUNDER)
	 */
	private String organizationName;

	public RegistrationData()
	{
	}

	public RegistrationData(String firstName, String lastName, String email, String password)
	{
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;
		this.password = password;
	}

	public String getFirstName()
	{
		return firstName;
	}

	public void setFirstName(String firstName)
	{
		this.firstName = firstName;
	}

	public String getLastName()
	{
		return lastName;
	}

	public void setLastName(String lastName)
	{
		this.lastName = lastName;
	}

	public String getEmail()
	{
		return email;
	}

	public void setEmail(String email)
	{
		this.email = email;
	}

	public String getPassword()
	{
		return password;
	}

	public void setPassword(String password)
	{
		this.password = password;
	}

	public String getOrganizationName()
	{
		return organizationName;
	}

	public void setOrganizationName(String organizationName)
	{
		this.organizationName = organizationName;
	}
}
