package app.fuggs.bot.whatsapp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import app.fuggs.bot.whatsapp.model.WhatsAppPhoneNumberInfo;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(WhatsAppWireMockTestProfile.class)
@ConnectWireMock
class WhatsAppConnectivityServiceTest
{
	private static final String PHONE_NUMBER_PATH = "/" + WhatsAppWireMockTestProfile.TEST_PHONE_NUMBER_ID;

	WireMock wireMock;

	@Inject
	WhatsAppConnectivityService connectivityService;

	@Test
	void shouldReturnPhoneNumberIdentityWhenCredentialsAreValid()
	{
		wireMock.register(get(urlPathEqualTo(PHONE_NUMBER_PATH))
			.willReturn(aResponse()
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{
						"id": "1234567890",
						"display_phone_number": "+1 555 651 6652",
						"verified_name": "Fuggs Belege"
					}
					""")));

		WhatsAppPhoneNumberInfo phoneNumber = connectivityService.whoAmI();

		assertThat(phoneNumber.verifiedName(), is("Fuggs Belege"));
		assertThat(phoneNumber.displayPhoneNumber(), is("+1 555 651 6652"));
	}

	@Test
	void shouldFailWithGraphApiDescriptionWhenTokenIsRejected()
	{
		wireMock.register(get(urlPathEqualTo(PHONE_NUMBER_PATH))
			.willReturn(aResponse()
				.withStatus(401)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{"error": {"message": "Invalid OAuth access token", "code": 190}}
					""")));

		Exception thrown = assertThrows(Exception.class, () -> connectivityService.whoAmI());
		assertThat(thrown.getMessage(), containsString("401"));
	}
}
