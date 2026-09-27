package app.fuggs.bot.document;

/**
 * Result of submitting one attachment to fuggs-app's document/bill pipeline,
 * independent of which channel (WhatsApp today, a future second channel, ...)
 * produced it.
 * <p>
 * Per issue #94, every member-facing message must be LLM-generated - so
 * {@link Success}, {@link AnalysisFailed} and {@link UnknownSender} carry the
 * ready-to-send text fuggs-app already generated (it owns the business facts:
 * vendor, member, Bommelwart), rather than raw data a channel adapter would
 * have to word itself. {@link StillProcessing} and {@link SubmissionFailed} are
 * pure transport/infra conditions - the poll budget ran out, or fuggs-app
 * couldn't be reached at all - so a channel adapter falls back to its own
 * static text for those, consistent with the documented LLM-fallback decision
 * in {@code docs/plan-whatsapp-bot.md}.
 * </p>
 */
public sealed interface IntakeOutcome
{
	/**
	 * The document was created and the analysis flow finished without error.
	 *
	 * @param message
	 *            LLM-generated acknowledgement text from fuggs-app
	 */
	record Success(String message) implements IntakeOutcome
	{
	}

	/**
	 * The document was created, but automatic analysis (ZugFerd/AI) failed. The
	 * document still exists and is visible in fuggs-app for manual review.
	 *
	 * @param message
	 *            LLM-generated acknowledgement text from fuggs-app
	 */
	record AnalysisFailed(String message) implements IntakeOutcome
	{
	}

	/**
	 * The sender identifier fuggs-app received does not match any member.
	 *
	 * @param message
	 *            LLM-generated text from fuggs-app
	 */
	record UnknownSender(String message) implements IntakeOutcome
	{
	}

	/**
	 * The analysis flow had not finished by the time the poll budget ran out.
	 * The document exists and analysis continues in the background.
	 */
	record StillProcessing() implements IntakeOutcome
	{
	}

	/**
	 * The submission itself failed - fuggs-app was unreachable, rejected the
	 * request for a reason other than an unknown sender, or the shared secret
	 * was rejected.
	 */
	record SubmissionFailed() implements IntakeOutcome
	{
	}
}
