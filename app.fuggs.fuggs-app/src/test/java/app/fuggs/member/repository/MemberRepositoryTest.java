package app.fuggs.member.repository;

import app.fuggs.member.domain.Member;
import app.fuggs.organization.domain.Organization;
import app.fuggs.shared.BaseOrganizationTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class MemberRepositoryTest extends BaseOrganizationTest
{
	@Inject
	MemberRepository memberRepository;

	@Test
	void shouldFindMemberByNormalizedWhatsAppPhone()
	{
		Organization org = getOrCreateTestOrganization();
		Long memberId = createMemberWithWhatsAppPhone(org, "0170 1234567");

		Member byE164 = memberRepository.findByWhatsAppPhoneE164("491701234567");
		Member byPlusPrefixed = memberRepository.findByWhatsAppPhoneE164("+49 170 1234567");
		Member byLocalFormat = memberRepository.findByWhatsAppPhoneE164("0170 1234567");

		assertEquals(memberId, byE164.getId());
		assertEquals(memberId, byPlusPrefixed.getId());
		assertEquals(memberId, byLocalFormat.getId());
	}

	@Test
	void shouldReturnNullForUnknownWhatsAppPhone()
	{
		assertNull(memberRepository.findByWhatsAppPhoneE164("4917099999999"));
	}

	@Test
	void shouldReturnNullForBlankOrMissingWhatsAppPhone()
	{
		assertNull(memberRepository.findByWhatsAppPhoneE164(null));
		assertNull(memberRepository.findByWhatsAppPhoneE164(""));
		assertNull(memberRepository.findByWhatsAppPhoneE164("   "));
	}

	@Test
	void shouldDeriveNullWhatsAppPhoneWhenPhoneIsUnparseable()
	{
		Member member = new Member();
		member.setPhone("not a phone number");
		assertNull(member.getWhatsappPhoneE164());
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	Long createMemberWithWhatsAppPhone(Organization org, String phone)
	{
		Member member = new Member();
		member.setFirstName("Hugo");
		member.setLastName("Müller");
		member.setUserName("hugo.mueller.whatsapp." + System.nanoTime());
		member.setPhone(phone);
		member.setOrganization(org);
		memberRepository.persist(member);
		return member.getId();
	}
}
