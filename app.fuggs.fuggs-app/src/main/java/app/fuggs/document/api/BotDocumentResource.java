package app.fuggs.document.api;

import app.fuggs.document.domain.Document;
import app.fuggs.document.repository.DocumentRepository;
import app.fuggs.document.service.DocumentIntakeService;
import app.fuggs.member.domain.Member;
import app.fuggs.member.repository.MemberRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Optional;

/**
 * Internal, machine-to-machine document intake shared by every chat bot channel
 * (Telegram today; a future WhatsApp integration would call the same endpoint).
 * Deliberately not a Renarde {@code Controller} and not {@code @Authenticated}
 * - the caller has no user session, only a shared secret. Requests without a
 * matching {@code X-Bot-Secret} header are rejected.
 * <p>
 * Uses a JSON body rather than multipart/form-data. Quarkus's global CSRF
 * filter (`quarkus-rest-csrf`, active application-wide for every POST/PUT/
 * DELETE regardless of {@code @Authenticated}) enforces a token on
 * form-urlencoded and multipart bodies but explicitly skips verification for
 * any other content type - JSON is the standard escape hatch, since it can't be
 * submitted by a naive cross-site HTML form the way multipart can.
 * </p>
 * <p>
 * Sender resolution is per-{@code channel} ({@link #resolveMember}) because
 * each channel identifies members differently - Telegram by username, a future
 * WhatsApp channel by E.164 phone number. Adding a channel means one more
 * {@code case} there plus that channel's own {@code Member} column and
 * repository lookup (see {@link MemberRepository#findByTelegramUsername} for
 * the existing precedent) - nothing else in this class changes.
 * </p>
 */
@Path("/api/bot/documents")
@ApplicationScoped
public class BotDocumentResource
{
	private static final Logger LOG = LoggerFactory.getLogger(BotDocumentResource.class);

	private static final String CHANNEL_TELEGRAM = "telegram";

	@Inject
	MemberRepository memberRepository;

	@Inject
	DocumentRepository documentRepository;

	@Inject
	DocumentIntakeService intakeService;

	/**
	 * {@code Optional<String>} rather than a plain {@code String} - Quarkus's
	 * built-in converter treats an empty-string config value as "absent" and
	 * fails eager validation of a non-optional {@code String} property, which
	 * would break application startup whenever the property is unset (the
	 * default).
	 */
	@ConfigProperty(name = "fuggs.bot.shared-secret")
	Optional<String> sharedSecret;

	/**
	 * {@code channel} plus {@code senderIdentifier} is deliberately generic
	 * rather than e.g. {@code telegramUsername} - see the class Javadoc.
	 */
	public record IntakeRequest(String channel, String senderIdentifier, String fileName, String contentType,
		String fileBase64)
	{
	}

	public record IntakeResponse(Long documentId)
	{
	}

	public record ErrorResponse(String error)
	{
	}

	public record StatusResponse(String status, boolean complete, String error, String name,
		java.math.BigDecimal total, String currencyCode)
	{
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response submit(@HeaderParam("X-Bot-Secret") String secret, IntakeRequest request)
	{
		if (!isAuthorized(secret))
		{
			return unauthorized();
		}

		if (request == null || request.fileBase64() == null || request.fileName() == null
			|| request.fileName().isBlank())
		{
			return Response.status(Response.Status.BAD_REQUEST)
				.entity(new ErrorResponse("missing_file"))
				.build();
		}

		Member member = resolveMember(request.channel(), request.senderIdentifier());
		if (member == null)
		{
			LOG.info("Bot document submission rejected, unknown sender: channel={}, senderIdentifier={}",
				request.channel(), request.senderIdentifier());
			return Response.status(Response.Status.NOT_FOUND)
				.entity(new ErrorResponse("unknown_member"))
				.build();
		}

		byte[] content;
		try
		{
			content = Base64.getDecoder().decode(request.fileBase64());
		}
		catch (IllegalArgumentException e)
		{
			LOG.warn("Bot document submission rejected, invalid base64 content: member={}", member.getUserName());
			return Response.status(Response.Status.BAD_REQUEST)
				.entity(new ErrorResponse("invalid_file_encoding"))
				.build();
		}

		Document document = intakeService.intake(member.getOrganization(), member.getUserName(),
			content, request.fileName(), request.contentType());

		LOG.info("Document intake via bot: documentId={}, member={}", document.getId(),
			member.getUserName());
		return Response.ok(new IntakeResponse(document.getId())).build();
	}

	@GET
	@Path("/{id}/status")
	@Produces(MediaType.APPLICATION_JSON)
	public Response status(@HeaderParam("X-Bot-Secret") String secret, @PathParam("id") Long id)
	{
		if (!isAuthorized(secret))
		{
			return unauthorized();
		}

		Document document = documentRepository.findById(id);
		if (document == null)
		{
			return Response.status(Response.Status.NOT_FOUND).entity(new ErrorResponse("not_found")).build();
		}

		String status = document.getAnalysisStatus() != null ? document.getAnalysisStatus().name() : "PENDING";
		return Response.ok(new StatusResponse(status, document.isAnalysisComplete(),
			document.getAnalysisError(), document.getDisplayName(), document.getTotal(),
			document.getCurrencyCode())).build();
	}

	/**
	 * Resolves a sender identifier to a {@code Member}, dispatching on
	 * {@code channel} since each channel identifies members differently. An
	 * unrecognized or missing channel resolves to no member, which the caller
	 * reports as the same "unknown sender" response as an unmatched identifier
	 * - there is no separate "unsupported channel" error, since from the bot's
	 * perspective both mean the same thing: this sender can't be forwarded a
	 * document.
	 */
	private Member resolveMember(String channel, String senderIdentifier)
	{
		return switch (channel == null ? "" : channel)
		{
			case CHANNEL_TELEGRAM -> memberRepository.findByTelegramUsername(senderIdentifier);
			default -> null;
		};
	}

	/**
	 * Compares the provided header against the configured shared secret.
	 * Blank/unset on both sides is treated as a match, so a local dev setup
	 * with no {@code FUGGS_BOT_SHARED_SECRET} exported on either service still
	 * works; setting it on both sides locks the endpoint down.
	 */
	private boolean isAuthorized(String secret)
	{
		String expected = sharedSecret.orElse("");
		String provided = secret == null ? "" : secret;
		return expected.equals(provided);
	}

	private Response unauthorized()
	{
		return Response.status(Response.Status.UNAUTHORIZED).entity(new ErrorResponse("unauthorized")).build();
	}
}
