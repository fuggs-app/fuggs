package app.fuggs.bot.whatsapp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Downloads the raw bytes of a media file at the short-lived URL previously
 * resolved via {@link WhatsAppClient#getMediaUrl}. This is plain
 * {@link HttpClient} rather than a declarative REST client because the URL is
 * an arbitrary, fully-qualified location handed to us by Meta, not a fixed path
 * template.
 */
@ApplicationScoped
public class WhatsAppMediaDownloader
{
	@Inject
	WhatsAppConfig config;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	/**
	 * Downloads the file at the given Meta-provided, bearer-token-protected
	 * URL.
	 *
	 * @param mediaUrl
	 *            the {@code url} returned by {@code GET /{media-id}}
	 * @return the raw file bytes
	 * @throws IOException
	 *             if the download fails or Meta returns a non-200 status
	 * @throws InterruptedException
	 *             if the request is interrupted
	 */
	public byte[] download(String mediaUrl) throws IOException, InterruptedException
	{
		HttpRequest request = HttpRequest.newBuilder(URI.create(mediaUrl))
			.header("Authorization", config.bearerToken())
			.GET()
			.build();
		HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200)
		{
			throw new IOException("WhatsApp media download failed: HTTP " + response.statusCode());
		}
		return response.body();
	}
}
