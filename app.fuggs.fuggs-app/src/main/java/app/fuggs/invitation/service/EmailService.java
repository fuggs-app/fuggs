package app.fuggs.invitation.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for sending invitation emails. Uses Quarkus Mailer for email
 * delivery.
 */
@ApplicationScoped
public class EmailService
{
	private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);

	@Inject
	Mailer mailer;

	@ConfigProperty(name = "app.base-url", defaultValue = "http://localhost:8080")
	String baseUrl;

	@ConfigProperty(name = "app.from-email", defaultValue = "noreply@fuggs.app")
	String fromEmail;

	@ConfigProperty(name = "app.from-name", defaultValue = "Fuggs Buchhaltung")
	String fromName;

	@ConfigProperty(name = "app.email.write-to-file", defaultValue = "false")
	boolean writeToFile;

	@ConfigProperty(name = "app.email.output-dir", defaultValue = "target/emails")
	String outputDir;

	@CheckedTemplate
	public static class Templates
	{
		private Templates()
		{
			// static
		}

		public static native TemplateInstance invitationHtml(String organizationName, String inviterName,
			String roleDisplay, String registrationUrl);

		public static native TemplateInstance invitationText(String organizationName, String inviterName,
			String roleDisplay, String registrationUrl);
	}

	/**
	 * Sends an invitation email to a new user.
	 *
	 * @param toEmail
	 *            Recipient email address
	 * @param token
	 *            Invitation token for the registration link
	 * @param organizationName
	 *            Name of the organization
	 * @param inviterName
	 *            Name of the person who sent the invitation
	 * @param role
	 *            Role that will be assigned
	 */
	public void sendInvitationEmail(String toEmail, String token, String organizationName, String inviterName,
		String role)
	{
		LOG.info("Sending invitation email: toEmail={}, organizationName={}, role={}", toEmail, organizationName, role);

		String registrationUrl = baseUrl + "/registrierung?token=" + token;
		String roleDisplay = role.equals("ADMIN") ? "Administrator" : "Mitglied";
		String subject = "Einladung zu " + organizationName;

		try
		{
			// Render HTML and text templates
			String htmlBody = Templates.invitationHtml(organizationName, inviterName, roleDisplay, registrationUrl)
				.render();
			String textBody = Templates.invitationText(organizationName, inviterName, roleDisplay, registrationUrl)
				.render();

			// Send email via SMTP
			mailer.send(Mail.withHtml(toEmail, subject, htmlBody)
				.setText(textBody)
				.setFrom(fromName + " <" + fromEmail + ">"));
			LOG.info("Invitation email sent successfully: toEmail={}", toEmail);

			// Additionally write to file if configured
			if (writeToFile)
			{
				writeEmailToFile(toEmail, subject, htmlBody, textBody);
				LOG.info("Invitation email also written to file: toEmail={}", toEmail);
			}
		}
		catch (Exception e)
		{
			LOG.error("Failed to send invitation email: toEmail={}, error={}", toEmail, e.getMessage(), e);
			// Don't throw exception - invitation should still be created even
			// if email fails
			// User can manually share the registration link
		}
	}

	/**
	 * Writes an email to an HTML file in the target directory. Used in dev mode
	 * for easier debugging - emails are sent via SMTP and additionally written
	 * to file.
	 */
	private void writeEmailToFile(String toEmail, String subject, String htmlBody, String textBody)
		throws IOException
	{
		// Create output directory if it doesn't exist
		Path dirPath = Paths.get(outputDir);
		Files.createDirectories(dirPath);

		// Generate filename: YYYY-MM-DD_HH-mm-ss_recipient_subject.html
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
		String sanitizedEmail = toEmail.replaceAll("[^a-zA-Z0-9]", "-");
		String sanitizedSubject = subject.replaceAll("[^a-zA-Z0-9äöüÄÖÜß ]", "-").replaceAll("\\s+", "-");
		String filename = String.format("%s_%s_%s.html", timestamp, sanitizedEmail, sanitizedSubject);

		Path filePath = dirPath.resolve(filename);

		// Write HTML email with metadata header
		StringBuilder content = new StringBuilder();
		content.append("<!DOCTYPE html>\n");
		content.append("<html lang=\"de\">\n");
		content.append("<head>\n");
		content.append("  <meta charset=\"UTF-8\">\n");
		content.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
		content.append("  <title>").append(subject).append("</title>\n");
		content.append("  <style>\n");
		content.append("    .email-metadata {\n");
		content.append("      background: #f4f4f4;\n");
		content.append("      border: 2px solid #0f62fe;\n");
		content.append("      padding: 1rem;\n");
		content.append("      margin-bottom: 2rem;\n");
		content.append("      font-family: monospace;\n");
		content.append("    }\n");
		content.append("    .email-metadata h2 {\n");
		content.append("      margin-top: 0;\n");
		content.append("      color: #0f62fe;\n");
		content.append("    }\n");
		content.append("    .email-metadata dl {\n");
		content.append("      display: grid;\n");
		content.append("      grid-template-columns: auto 1fr;\n");
		content.append("      gap: 0.5rem;\n");
		content.append("    }\n");
		content.append("    .email-metadata dt {\n");
		content.append("      font-weight: bold;\n");
		content.append("    }\n");
		content.append("    .email-metadata dd {\n");
		content.append("      margin: 0;\n");
		content.append("    }\n");
		content.append("  </style>\n");
		content.append("</head>\n");
		content.append("<body>\n");
		content.append("  <div class=\"email-metadata\">\n");
		content.append("    <h2>📧 Email Metadata (Dev Mode)</h2>\n");
		content.append("    <dl>\n");
		content.append("      <dt>To:</dt><dd>").append(toEmail).append("</dd>\n");
		content.append("      <dt>From:</dt><dd>").append(fromName).append(" &lt;").append(fromEmail)
			.append("&gt;</dd>\n");
		content.append("      <dt>Subject:</dt><dd>").append(subject).append("</dd>\n");
		content.append("      <dt>Timestamp:</dt><dd>").append(LocalDateTime.now()).append("</dd>\n");
		content.append("      <dt>File:</dt><dd>").append(filename).append("</dd>\n");
		content.append("    </dl>\n");
		content.append("  </div>\n");
		content.append("  <hr>\n");
		content.append(htmlBody);
		content.append("\n</body>\n");
		content.append("</html>");

		Files.writeString(filePath, content.toString());

		LOG.info("Email written to file: {}", filePath.toAbsolutePath());
	}
}
