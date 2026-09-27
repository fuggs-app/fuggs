package app.fuggs.member.domain;

import app.fuggs.bommel.domain.Bommel;
import app.fuggs.organization.domain.Organization;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Member extends PanacheEntity
{
	private static final Logger LOG = LoggerFactory.getLogger(Member.class);

	/**
	 * Default region for phone numbers that are entered without a country code,
	 * e.g. "0170 1234567". Fuggs is a German association accounting tool, so
	 * members overwhelmingly have German numbers.
	 */
	private static final String DEFAULT_PHONE_REGION = "DE";

	@NotBlank
	private String firstName;

	@NotBlank
	private String lastName;

	@Email
	private String email;

	private String phone;

	@Column(unique = true)
	private String userName;

	@ManyToOne(optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@OneToMany(mappedBy = "responsibleMember", fetch = FetchType.LAZY)
	private List<Bommel> responsibleBommels = new ArrayList<>();

	/**
	 * ID of the member who invited this member (if invited).
	 */
	@Column(name = "invited_by_member_id")
	private Long invitedByMemberId;

	/**
	 * Type of invitation used to join (BETA or INVITED).
	 */
	@Column(length = 20)
	private String inviteType;

	/**
	 * When the member joined the organization.
	 */
	@Column(name = "joined_at")
	private Instant joinedAt;

	/**
	 * Keycloak user ID for authentication mapping.
	 */
	@Column(name = "keycloak_user_id", unique = true)
	private String keycloakUserId;

	/**
	 * {@link #phone} normalized to E.164 without a leading '+' (e.g.
	 * {@code 4917012345678}), matching how WhatsApp's Cloud API identifies
	 * senders. Derived automatically by {@link #setPhone(String)} - there is no
	 * separate WhatsApp number to maintain. WhatsApp has no separate chat id
	 * concept - this same number doubles as the address used to message the
	 * member back.
	 */
	@Column(name = "whatsapp_phone_e164", unique = true, length = 20)
	private String whatsappPhoneE164;

	public Long getId()
	{
		return id;
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

	public String getPhone()
	{
		return phone;
	}

	/**
	 * Sets the contact phone number and, as a side effect, derives
	 * {@link #whatsappPhoneE164} from it. The two are the same real-world
	 * number for the vast majority of members, so there is no separate WhatsApp
	 * field to fill in - see {@link #getWhatsappPhoneE164()}.
	 *
	 * @param phone
	 *            the raw phone number, in any format ("0170 1234567", "+49 170
	 *            1234567", ...)
	 */
	public void setPhone(String phone)
	{
		this.phone = phone;
		this.whatsappPhoneE164 = normalizeWhatsAppPhone(phone);
	}

	public List<Bommel> getResponsibleBommels()
	{
		return responsibleBommels;
	}

	public String getUserName()
	{
		return userName;
	}

	public void setUserName(String userName)
	{
		this.userName = userName;
	}

	public Organization getOrganization()
	{
		return organization;
	}

	public void setOrganization(Organization organization)
	{
		this.organization = organization;
	}

	public String getDisplayName()
	{
		return firstName + " " + lastName;
	}

	public Long getInvitedByMemberId()
	{
		return invitedByMemberId;
	}

	public void setInvitedByMemberId(Long invitedByMemberId)
	{
		this.invitedByMemberId = invitedByMemberId;
	}

	public String getInviteType()
	{
		return inviteType;
	}

	public void setInviteType(String inviteType)
	{
		this.inviteType = inviteType;
	}

	public Instant getJoinedAt()
	{
		return joinedAt;
	}

	public void setJoinedAt(Instant joinedAt)
	{
		this.joinedAt = joinedAt;
	}

	public String getKeycloakUserId()
	{
		return keycloakUserId;
	}

	public void setKeycloakUserId(String keycloakUserId)
	{
		this.keycloakUserId = keycloakUserId;
	}

	public String getWhatsappPhoneE164()
	{
		return whatsappPhoneE164;
	}

	/**
	 * Normalizes a phone number to E.164 without a leading '+' (as WhatsApp's
	 * Cloud API sends it), without requiring a {@code Member} instance. Used by
	 * {@link #setPhone(String)} to derive {@link #whatsappPhoneE164}, and by
	 * lookups so the comparison stays consistent with what is stored. An empty
	 * or unparseable result normalizes to {@code null} rather than raising a
	 * validation error here - the caller (e.g. {@code MemberResource}) is
	 * responsible for surfacing that as a form validation message.
	 *
	 * @param whatsappPhone
	 *            the raw phone number, in any format a member might type it in
	 *            ("0170 1234567", "+49 170 1234567", ...)
	 * @return the normalized number, or {@code null} if blank or unparseable
	 */
	public static String normalizeWhatsAppPhone(String whatsappPhone)
	{
		if (whatsappPhone == null || whatsappPhone.isBlank())
		{
			return null;
		}
		String trimmed = whatsappPhone.trim();
		PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

		// libphonenumber's "default region" only applies to a number with no
		// leading '+' - it never guesses that such a number might already
		// carry a country code. WhatsApp's own sender ids are exactly that
		// case (E.164 digits without '+', e.g. "4917012345678"), so that shape
		// has to be tried explicitly before falling back to a plain,
		// locally-formatted number such as "0170 1234567".
		PhoneNumber parsed = trimmed.startsWith("+") ? tryParse(phoneNumberUtil, trimmed, null)
			: tryParse(phoneNumberUtil, "+" + trimmed, null);
		if (parsed == null)
		{
			parsed = tryParse(phoneNumberUtil, trimmed, DEFAULT_PHONE_REGION);
		}
		if (parsed == null || !phoneNumberUtil.isValidNumber(parsed))
		{
			return null;
		}
		String e164 = phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
		return e164.startsWith("+") ? e164.substring(1) : e164;
	}

	private static PhoneNumber tryParse(PhoneNumberUtil phoneNumberUtil, String text, String region)
	{
		try
		{
			return phoneNumberUtil.parse(text, region);
		}
		catch (NumberParseException e)
		{
			LOG.debug("Could not parse phone number as E.164: {}", e.getMessage());
			return null;
		}
	}
}
