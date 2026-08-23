package app.fuggs.bot.telegram;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Downloads the raw bytes of a file previously resolved via
 * {@link TelegramClient#getFile}. This is plain {@link HttpClient} rather than
 * a declarative REST client because the download path
 * ({@code /file/bot<token>/<filePath>}) contains slashes that don't map cleanly
 * onto a JAX-RS {@code @PathParam}.
 */
@ApplicationScoped
public class TelegramFileDownloader
{
	@Inject
	TelegramConfig config;

	@ConfigProperty(name = "quarkus.rest-client.telegram.url")
	String telegramApiUrl;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	/**
	 * Downloads the file at the given Telegram-provided path.
	 *
	 * @param filePath
	 *            the {@code file_path} returned by {@code getFile}
	 * @return the raw file bytes
	 * @throws IOException
	 *             if the download fails or Telegram returns a non-200 status
	 * @throws InterruptedException
	 *             if the request is interrupted
	 */
	public byte[] download(String filePath) throws IOException, InterruptedException
	{
		String token = config.botToken().orElseThrow();
		String base = telegramApiUrl.endsWith("/") ? telegramApiUrl.substring(0, telegramApiUrl.length() - 1)
			: telegramApiUrl;
		URI uri = URI.create(base + "/file/bot" + token + "/" + filePath);

		HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
		HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200)
		{
			throw new IOException("Telegram file download failed: HTTP " + response.statusCode());
		}
		return response.body();
	}
}
