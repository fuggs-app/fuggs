package app.fuggs.bot.document;

import java.math.BigDecimal;

/**
 * Result of submitting one attachment to fuggs-app's document/bill pipeline,
 * independent of which channel (Telegram, a future WhatsApp, ...) produced it.
 * <p>
 * {@link ReceiptIntakeService} only decides <em>what happened</em>; wording it
 * into a reply is each channel adapter's job, since tone and language may
 * differ per channel (and per {@code docs/plan-telegram-bot.md} /
 * {@code docs/plan-whatsapp-bot.md}, eventually per-channel LLM-generated text
 * rather than a static string at all).
 * </p>
 */
public sealed interface IntakeOutcome
{
	/**
	 * The document was created and the analysis flow finished without error.
	 *
	 * @param vendorName
	 *            the extracted vendor/sender name, or {@code null} if none was
	 *            found
	 * @param total
	 *            the extracted total amount, or {@code null} if none was found
	 * @param currencyCode
	 *            the currency of {@code total}, or {@code null}
	 */
	record Success(String vendorName, BigDecimal total, String currencyCode) implements IntakeOutcome
	{
	}

	/**
	 * The document was created, but automatic analysis (ZugFerd/AI) failed. The
	 * document still exists and is visible in fuggs-app for manual review.
	 */
	record AnalysisFailed() implements IntakeOutcome
	{
	}

	/**
	 * The sender identifier fuggs-app received does not match any member.
	 */
	record UnknownSender() implements IntakeOutcome
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
