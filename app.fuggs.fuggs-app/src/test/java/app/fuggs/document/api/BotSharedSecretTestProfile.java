package app.fuggs.document.api;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Configures a non-blank shared secret so {@link BotDocumentResourceTest} can
 * exercise the rejection path - the default (blank on both sides) always
 * matches, which would make an "unauthorized" test meaningless.
 */
public class BotSharedSecretTestProfile implements QuarkusTestProfile
{
	public static final String SECRET = "test-shared-secret";

	@Override
	public Map<String, String> getConfigOverrides()
	{
		return Map.of("fuggs.bot.shared-secret", SECRET);
	}
}
