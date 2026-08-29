package app.fuggs.messaging;

import java.math.BigDecimal;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import app.fuggs.document.domain.Document;
import app.fuggs.document.domain.TradeParty;
import app.fuggs.member.domain.Member;
import app.fuggs.member.repository.MemberRepository;
import app.fuggs.organization.domain.Organization;
import app.fuggs.shared.BaseOrganizationTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the three issue #94 acceptance criteria at the service level:
 * message generation (with fallback when the LLM fails) and the AC #3 proactive
 * push, without going through HTTP. {@link BotMessageService} and
 * {@link FuggsBotClient} are mocked so no real OpenAI or fuggs-bot call ever
 * happens in a test.
 */
@QuarkusTest
class BotNotificationServiceTest extends BaseOrganizationTest
{
	@Inject
	BotNotificationService botNotificationService;

	@Inject
	MemberRepository memberRepository;

	@InjectMock
	BotMessageService botMessageService;

	@InjectMock
	@RestClient
	FuggsBotClient fuggsBotClient;

	@BeforeEach
	void resetMocks()
	{
		Mockito.reset(botMessageService, fuggsBotClient);
	}

	@Test
	void unknownSenderMessage_shouldReturnLlmText()
	{
		Mockito.when(botMessageService.unknownSenderMessage())
			.thenReturn("Wir konnten dein Konto bei Fuggs nicht finden.");

		String message = botNotificationService.unknownSenderMessage();

		assertEquals("Wir konnten dein Konto bei Fuggs nicht finden.", message);
	}

	@Test
	void unknownSenderMessage_shouldFallBackWhenLlmFails()
	{
		Mockito.when(botMessageService.unknownSenderMessage()).thenThrow(new RuntimeException("OpenAI down"));

		String message = botNotificationService.unknownSenderMessage();

		assertThat(message, containsString("Bommelwart"));
	}

	@Test
	void uploadAcknowledgedMessage_shouldPassExtractedFactsToLlm()
	{
		Mockito.when(botMessageService.uploadAcknowledgedMessage(Mockito.anyString()))
			.thenReturn("Danke, dass du den Kaufland-Beleg hochgeladen hast!");
		Document document = documentFor("Kaufland", new BigDecimal("12.34"), "EUR");

		String message = botNotificationService.uploadAcknowledgedMessage(document, false);

		assertEquals("Danke, dass du den Kaufland-Beleg hochgeladen hast!", message);
		ArgumentCaptor<String> factsCaptor = ArgumentCaptor.forClass(String.class);
		Mockito.verify(botMessageService).uploadAcknowledgedMessage(factsCaptor.capture());
		assertThat(factsCaptor.getValue(), containsString("Kaufland"));
		assertThat(factsCaptor.getValue(), containsString("12.34"));
	}

	@Test
	void uploadAcknowledgedMessage_shouldFallBackWhenLlmFails()
	{
		Mockito.when(botMessageService.uploadAcknowledgedMessage(Mockito.anyString()))
			.thenThrow(new RuntimeException("OpenAI down"));
		Document document = documentFor("Kaufland", new BigDecimal("12.34"), "EUR");

		String message = botNotificationService.uploadAcknowledgedMessage(document, false);

		assertThat(message, containsString("Bommelwart"));
	}

	@Test
	void notifyTransactionBooked_shouldSkip_whenUploaderHasNoTelegramChatId()
	{
		Organization org = getOrCreateTestOrganization();
		createMemberWithUsername("no_chat_id_member", org, null);
		Document document = documentFor("Kaufland", BigDecimal.TEN, "EUR");
		document.setUploadedBy("no_chat_id_member");

		botNotificationService.notifyTransactionBooked(document, "Maria");

		Mockito.verifyNoInteractions(fuggsBotClient);
	}

	@Test
	void notifyTransactionBooked_shouldSkip_whenUploaderUnknown()
	{
		Document document = documentFor("Kaufland", BigDecimal.TEN, "EUR");
		document.setUploadedBy("nobody_with_this_username");

		botNotificationService.notifyTransactionBooked(document, "Maria");

		Mockito.verifyNoInteractions(fuggsBotClient);
	}

	@Test
	void notifyTransactionBooked_shouldPushViaTelegram_whenUploaderHasChatId()
	{
		Mockito.when(botMessageService.transactionBookedMessage(Mockito.anyString()))
			.thenReturn("Dein Kauflandbeleg wurde gerade von Maria bearbeitet. Danke, alles erledigt!");
		Organization org = getOrCreateTestOrganization();
		createMemberWithUsername("chat_id_member", org, 42424242L);
		Document document = documentFor("Kaufland", BigDecimal.TEN, "EUR");
		document.setUploadedBy("chat_id_member");

		botNotificationService.notifyTransactionBooked(document, "Maria");

		ArgumentCaptor<FuggsBotClient.NotificationRequest> requestCaptor = ArgumentCaptor.forClass(FuggsBotClient.NotificationRequest.class);
		Mockito.verify(fuggsBotClient).sendNotification(requestCaptor.capture());
		assertEquals("telegram", requestCaptor.getValue().channel());
		assertEquals("42424242", requestCaptor.getValue().recipientId());
		assertThat(requestCaptor.getValue().message(), equalTo(
			"Dein Kauflandbeleg wurde gerade von Maria bearbeitet. Danke, alles erledigt!"));
	}

	@Test
	void notifyTransactionBooked_shouldNotThrow_whenFuggsBotClientFails()
	{
		Mockito.when(botMessageService.transactionBookedMessage(Mockito.anyString())).thenReturn("egal");
		Mockito.doThrow(new RuntimeException("fuggs-bot unreachable")).when(fuggsBotClient)
			.sendNotification(Mockito.any());
		Organization org = getOrCreateTestOrganization();
		createMemberWithUsername("unreachable_bot_member", org, 1L);
		Document document = documentFor("Kaufland", BigDecimal.TEN, "EUR");
		document.setUploadedBy("unreachable_bot_member");

		botNotificationService.notifyTransactionBooked(document, "Maria");
		// No exception propagated - a booking action must never fail because a
		// member can't be notified.
	}

	private Document documentFor(String vendorName, BigDecimal total, String currencyCode)
	{
		Organization org = getOrCreateTestOrganization();
		TradeParty sender = new TradeParty();
		sender.setName(vendorName);
		sender.setOrganization(org);

		Document document = new Document();
		document.setSender(sender);
		document.setTotal(total);
		document.setCurrencyCode(currencyCode);
		document.setOrganization(org);
		return document;
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	void createMemberWithUsername(String userName, Organization org, Long telegramChatId)
	{
		Member member = new Member();
		member.setFirstName("Test");
		member.setLastName("Member");
		member.setUserName(userName);
		member.setOrganization(org);
		member.setTelegramChatId(telegramChatId);
		memberRepository.persist(member);
	}
}
