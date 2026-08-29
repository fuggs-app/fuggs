package app.fuggs.messaging;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Generates the German chat messages the bot sends to members, per issue #94:
 * "Alle Nachrichten werden vom LLM generiert, keine statischen vorgefertigten
 * Strings" - every member-facing message is LLM-written, not a static template.
 * Same {@code @RegisterAiService} shape as {@code app.fuggs.zugferd.service.
 * DocumentTagService} (which uses OpenAI directly), but backed by DeepSeek's
 * {@code deepseek-chat} - configured in {@code application.properties} via
 * quarkus-langchain4j-openai pointed at DeepSeek's OpenAI-compatible endpoint,
 * since there is no dedicated DeepSeek extension.
 * <p>
 * Callers must not derive new facts from the receipt to feed this service - the
 * JSON handed to these methods only ever carries what
 * {@link app.fuggs.document.domain.Document} already exposes (vendor name,
 * total, currency, upload time); this service turns those facts into a
 * sentence, it does not extract anything itself.
 * </p>
 */
@RegisterAiService
public interface BotMessageService
{
	String PERSONA = """
		Du bist der Chat-Bot der Buchhaltungs-App "Fuggs" für Vereine. Du
		schreibst kurze, freundliche Nachrichten auf Deutsch an Vereinsmitglieder,
		die per Chat einen Beleg (Rechnung/Quittung) eingereicht haben.
		Antworte ausschließlich mit dem Nachrichtentext selbst - keine
		Anführungszeichen, keine Anrede wie "Hallo", keine Erklärung was du tust.
		Maximal zwei Sätze.
		""";

	/**
	 * AC #1: the sender could not be matched to any member.
	 */
	@SystemMessage(PERSONA)
	@UserMessage("""
		Der Absender konnte keinem Fuggs-Mitglied zugeordnet werden. Formuliere
		eine Nachricht, die ihn bittet, sich an seinen Bommelwart (den
		Vereins-Ansprechpartner) zu wenden, damit dieser sein Konto findet
		bzw. seine Kontaktdaten hinterlegt.
		""")
	String unknownSenderMessage();

	/**
	 * AC #2: acknowledges a received upload, ideally naming what was on it.
	 *
	 * @param factsJson
	 *            JSON with whatever of vendor name / total / currency / whether
	 *            automatic analysis succeeded is known; fields may be absent
	 */
	@SystemMessage(PERSONA)
	@UserMessage("""
		Ein Mitglied hat gerade einen Beleg hochgeladen. Bedanke dich, bestätige
		den Empfang und erwähne - falls in den Fakten vorhanden - um welchen
		Beleg es sich handelt (Händler/Betrag). Weise darauf hin, dass der
		Bommelwart den Beleg prüfen wird. Wenn die automatische Analyse laut
		den Fakten fehlgeschlagen ist, sag stattdessen freundlich, dass der
		Beleg trotzdem angekommen ist und manuell geprüft wird.
		Fakten (JSON, Felder können fehlen): {facts}
		""")
	String uploadAcknowledgedMessage(@V("facts") String factsJson);

	/**
	 * AC #3: informs the original uploader once their document became a booked
	 * transaction.
	 *
	 * @param factsJson
	 *            JSON with whatever of vendor name / a German relative-time
	 *            phrase for the upload date / the name of whoever booked it is
	 *            known; fields may be absent
	 */
	@SystemMessage(PERSONA)
	@UserMessage("""
		Ein zuvor per Chat hochgeladener Beleg wurde soeben von einem
		Bommelwart in eine Buchung/Transaktion umgewandelt. Informiere den
		ursprünglichen Hochlader darüber, bedanke dich und erwähne - falls in
		den Fakten vorhanden - um welchen Beleg es ging, wann er hochgeladen
		wurde und wer ihn bearbeitet hat. Mach deutlich, dass für ihn damit
		nichts mehr zu tun ist.
		Fakten (JSON, Felder können fehlen): {facts}
		""")
	String transactionBookedMessage(@V("facts") String factsJson);
}
