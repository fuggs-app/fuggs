package app.fuggs.bot.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import app.fuggs.bot.telegram.model.TelegramUser;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(WireMockTestProfile.class)
@ConnectWireMock
class TelegramConnectivityServiceTest
{
	private static final String GET_ME_PATH = "/bot" + WireMockTestProfile.TEST_TOKEN + "/getMe";

	WireMock wireMock;

	@Inject
	TelegramConnectivityService connectivityService;

	@Test
	void shouldReturnBotIdentityWhenTokenIsValid()
	{
		wireMock.register(get(urlPathEqualTo(GET_ME_PATH))
			.willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{
						"ok": true,
						"result": {
							"id": 8123456789,
							"is_bot": true,
							"first_name": "Fuggs Belege",
							"username": "fuggs_belege_bot"
						}
					}
					""")));

		TelegramUser bot = connectivityService.whoAmI();

		assertThat(bot.username(), is("fuggs_belege_bot"));
		assertThat(bot.id(), is(8123456789L));
		assertThat(bot.isBot(), is(true));
	}

	@Test
	void shouldFailWithTelegramDescriptionWhenTokenIsRejected()
	{
		wireMock.register(get(urlPathEqualTo(GET_ME_PATH))
			.willReturn(aResponse()
				.withStatus(401)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{"ok": false, "error_code": 401, "description": "Unauthorized"}
					""")));

		// A 401 surfaces as a client WebApplicationException rather than the
		// parsed envelope, so assert on the message only.
		Exception thrown = assertThrows(Exception.class, () -> connectivityService.whoAmI());
		assertThat(thrown.getMessage(), containsString("401"));
	}
}
