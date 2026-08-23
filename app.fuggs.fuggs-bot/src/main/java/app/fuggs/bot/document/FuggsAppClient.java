package app.fuggs.bot.document;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Client for fuggs-app's internal Telegram bot intake endpoint
 * ({@code BotDocumentResource}). Every call carries the shared secret
 * configured in {@code fuggs.app.shared-secret}, mirroring the check fuggs-app
 * performs on the other end.
 * <p>
 * Uses a JSON body rather than multipart/form-data - fuggs-app's global CSRF
 * filter enforces a token on form-urlencoded and multipart bodies for every
 * POST/PUT/DELETE, but explicitly skips other content types.
 * </p>
 */
@Path("/api/bot/documents")
@RegisterRestClient(configKey = "fuggs-app")
@ClientHeaderParam(name = "X-Bot-Secret", value = "{sharedSecret}")
public interface FuggsAppClient
{
	/**
	 * Submits a downloaded Telegram file for intake into the document/bill
	 * pipeline.
	 *
	 * @param request
	 *            the base64-encoded file plus metadata and sender username
	 * @return the created document id, or an error payload
	 */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	IntakeResponse submitDocument(IntakeRequest request);

	/**
	 * Checks the analysis status of a previously submitted document.
	 *
	 * @param id
	 *            the document id
	 * @return the current status
	 */
	@GET
	@Path("/{id}/status")
	@Produces(MediaType.APPLICATION_JSON)
	StatusResponse getStatus(@PathParam("id") Long id);

	/**
	 * Resolves the configured shared secret at call time, referenced by the
	 * class-level {@code @ClientHeaderParam} above. Must be a default (not
	 * static) method - the MicroProfile Rest Client spec resolves
	 * {@code {methodName}} header values as instance methods on the client
	 * proxy.
	 *
	 * @return the shared secret, or an empty string when unconfigured
	 */
	default String sharedSecret()
	{
		return ConfigProvider.getConfig()
			.getOptionalValue("fuggs.app.shared-secret", String.class)
			.orElse("");
	}
}
