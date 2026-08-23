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
	void shouldFindMemberByNormalizedTelegramUsername()
	{
		Organization org = getOrCreateTestOrganization();
		Long memberId = createMemberWithTelegramUsername(org, "@Hugo_Mueller");

		Member byLowercase = memberRepository.findByTelegramUsername("hugo_mueller");
		Member byUppercase = memberRepository.findByTelegramUsername("HUGO_MUELLER");
		Member byLeadingAt = memberRepository.findByTelegramUsername("@hugo_mueller");
		Member byPadded = memberRepository.findByTelegramUsername("  hugo_mueller  ");

		assertEquals(memberId, byLowercase.getId());
		assertEquals(memberId, byUppercase.getId());
		assertEquals(memberId, byLeadingAt.getId());
		assertEquals(memberId, byPadded.getId());
	}

	@Test
	void shouldReturnNullForUnknownTelegramUsername()
	{
		assertNull(memberRepository.findByTelegramUsername("someone_who_does_not_exist"));
	}

	@Test
	void shouldReturnNullForBlankOrMissingTelegramUsername()
	{
		assertNull(memberRepository.findByTelegramUsername(null));
		assertNull(memberRepository.findByTelegramUsername(""));
		assertNull(memberRepository.findByTelegramUsername("   "));
	}

	@Test
	void shouldNormalizeToNullWhenSettingABlankTelegramUsername()
	{
		Member member = new Member();
		member.setTelegramUsername("  @  ");
		assertNull(member.getTelegramUsername());
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	Long createMemberWithTelegramUsername(Organization org, String telegramUsername)
	{
		Member member = new Member();
		member.setFirstName("Hugo");
		member.setLastName("Müller");
		member.setUserName("hugo.mueller." + System.nanoTime());
		member.setTelegramUsername(telegramUsername);
		member.setOrganization(org);
		memberRepository.persist(member);
		return member.getId();
	}
}
