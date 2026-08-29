package app.fuggs.bot.document;

import java.util.Base64;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;

/**
 * Channel-agnostic core of the bot's document intake: submits a downloaded
 * attachment to fuggs-app and waits for the analysis flow to finish, without
 * knowing or caring whether the attachment came from Telegram, a future
 * WhatsApp integration, or anything else.
 * <p>
 * A channel adapter (e.g. {@code TelegramPoller}) is responsible only for
 * transport - receiving updates, downloading bytes, sending the reply - and for
 * wording the {@link IntakeOutcome} this returns into channel-appropriate text.
 * </p>
 */
@ApplicationScoped
public class ReceiptIntakeService
{
	private static final Logger LOG = LoggerFactory.getLogger(ReceiptIntakeService.class);

	private static final int STATUS_POLL_INTERVAL_MS = 2000;
	private static final int STATUS_POLL_MAX_ATTEMPTS = 15;

	@RestClient
	FuggsAppClient fuggsAppClient;

	/**
	 * Submits the attachment and blocks (via polling) until fuggs-app's
	 * analysis flow completes or the poll budget runs out.
	 *
	 * @param channel
	 *            the sending channel's identifier, e.g. {@code "telegram"} -
	 *            must match a case {@code BotDocumentResource} on fuggs-app
	 *            knows how to resolve to a {@code Member}
	 * @param senderIdentifier
	 *            the sender's identity within that channel (a Telegram
	 *            username, a future WhatsApp E.164 phone number, ...)
	 * @param content
	 *            the raw file bytes
	 * @param fileName
	 *            the original filename
	 * @param contentType
	 *            the MIME type
	 * @return the outcome of the submission
	 */
	public IntakeOutcome submit(String channel, String senderIdentifier, byte[] content, String fileName,
		String contentType)
	{
		try
		{
			String fileBase64 = Base64.getEncoder().encodeToString(content);
			IntakeResponse response = fuggsAppClient.submitDocument(
				new IntakeRequest(channel, senderIdentifier, fileName, contentType, fileBase64));
			return awaitAnalysisOutcome(response.documentId());
		}
		catch (WebApplicationException e)
		{
			int status = e.getResponse() != null ? e.getResponse().getStatus() : -1;
			if (status == 404)
			{
				return new IntakeOutcome.UnknownSender();
			}
			LOG.error("fuggs-app rejected document submission: channel={}, status={}", channel, status, e);
			return new IntakeOutcome.SubmissionFailed();
		}
		catch (Exception e)
		{
			LOG.error("Failed to submit document to fuggs-app: channel={}, error={}", channel, e.getMessage(), e);
			return new IntakeOutcome.SubmissionFailed();
		}
	}

	private IntakeOutcome awaitAnalysisOutcome(Long documentId)
	{
		for (int attempt = 0; attempt < STATUS_POLL_MAX_ATTEMPTS; attempt++)
		{
			StatusResponse status;
			try
			{
				status = fuggsAppClient.getStatus(documentId);
			}
			catch (Exception e)
			{
				LOG.error("Failed to check document status: documentId={}, error={}", documentId, e.getMessage(),
					e);
				return new IntakeOutcome.SubmissionFailed();
			}

			if (status.complete())
			{
				boolean analysisFailed = status.error() != null && !status.error().isBlank();
				return analysisFailed ? new IntakeOutcome.AnalysisFailed()
					: new IntakeOutcome.Success(status.name(), status.total(), status.currencyCode());
			}

			sleep(STATUS_POLL_INTERVAL_MS);
		}

		LOG.info("Document analysis still running after polling budget: documentId={}", documentId);
		return new IntakeOutcome.StillProcessing();
	}

	private void sleep(long millis)
	{
		try
		{
			Thread.sleep(millis);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}
}
