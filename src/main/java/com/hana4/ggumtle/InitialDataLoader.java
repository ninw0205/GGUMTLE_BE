package com.hana4.ggumtle;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.hana4.ggumtle.model.entity.advertisement.Advertisement;
import com.hana4.ggumtle.model.entity.advertisement.AdvertisementAdType;
import com.hana4.ggumtle.model.entity.advertisement.AdvertisementLocationType;
import com.hana4.ggumtle.model.entity.advertisement.AdvertisementProductType;
import com.hana4.ggumtle.model.entity.bucket.Bucket;
import com.hana4.ggumtle.model.entity.bucket.BucketHowTo;
import com.hana4.ggumtle.model.entity.bucket.BucketStatus;
import com.hana4.ggumtle.model.entity.bucket.BucketTagType;
import com.hana4.ggumtle.model.entity.dreamAccount.DreamAccount;
import com.hana4.ggumtle.model.entity.group.Group;
import com.hana4.ggumtle.model.entity.group.GroupCategory;
import com.hana4.ggumtle.model.entity.groupMember.GroupMember;
import com.hana4.ggumtle.model.entity.portfolioTemplate.PortfolioTemplate;
import com.hana4.ggumtle.model.entity.post.Post;
import com.hana4.ggumtle.model.entity.post.PostType;
import com.hana4.ggumtle.model.entity.user.User;
import com.hana4.ggumtle.model.entity.user.UserRole;
import com.hana4.ggumtle.repository.AdvertisementRepository;
import com.hana4.ggumtle.repository.BucketRepository;
import com.hana4.ggumtle.repository.DreamAccountRepository;
import com.hana4.ggumtle.repository.GroupMemberRepository;
import com.hana4.ggumtle.repository.GroupRepository;
import com.hana4.ggumtle.repository.PortfolioTemplateRepository;
import com.hana4.ggumtle.repository.PostRepository;
import com.hana4.ggumtle.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class InitialDataLoader implements ApplicationRunner {
	private final PortfolioTemplateRepository portfolioTemplateRepository;
	private final AdvertisementRepository advertisementRepository;
	private final BucketRepository bucketRepository;
	private final UserRepository userRepository;
	private final DreamAccountRepository dreamAccountRepository;
	private final BCryptPasswordEncoder passwordEncoder;
	private final PostRepository postRepository;
	private final GroupRepository groupRepository;
	private final GroupMemberRepository groupMemberRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) throws Exception {
		User recommendUser;
		if (userRepository.count() == 0) {
			recommendUser = User.builder()
				.password(passwordEncoder.encode("recommendedUser123!"))
				.name("추천버킷관리자")
				.permission((short)3)
				.role(UserRole.USER)
				.nickname("꿈틀추천")
				.birthDate(LocalDateTime.of(1990, 1, 1, 0, 0, 0, 0))
				.gender("m")
				.tel("123456789")
				.build();
			recommendUser = userRepository.save(recommendUser);
		} else {
			recommendUser = userRepository.findFirstByOrderByIdAsc();
		}

		User savedUser = userRepository.findUserByTel(recommendUser.getTel())
			.orElseThrow(() -> new RuntimeException("Failed to retrieve saved user"));

		Group group;

		if (groupRepository.count() == 0) {
			group = Group.builder()
				.name("테스트그룹")
				.description("테스트용 그룹입니다.")
				.category(GroupCategory.TRAVEL)
				.build();
			groupRepository.save(group);
		} else {
			group = groupRepository.findFirstByOrderByIdAsc();
		}

		if (groupMemberRepository.count() == 0 || groupMemberRepository.findById(1L).orElse(null).getGroup() != group) {
			GroupMember groupMember = GroupMember.builder()
				.group(group)
				.user(recommendUser)
				.build();
			groupMemberRepository.save(groupMember);
		}

		if (postRepository.count() < 200000) {
			postRepository.deleteAll();
			postRepository.flush();
			List<Post> posts = new ArrayList<>();
			for (int i = 0; i < 200000; i++) {
				posts.add(Post.builder()
					.user(recommendUser)
					.group(group)
					.content("test용 글입니다. 번호는 " + i)
					.postType(PostType.POST)
					.build());
			}
			postRepository.saveAll(posts);
		}

		if (portfolioTemplateRepository.count() == 0) {
			PortfolioTemplate conservative = PortfolioTemplate.builder()
				.name("CONSERVATIVE")
				.depositWithdrawalRatio(BigDecimal.ZERO)
				.savingTimeDepositRatio(new BigDecimal("0.70"))
				.investmentRatio(new BigDecimal("0.20"))
				.foreignCurrencyRatio(BigDecimal.ZERO)
				.pensionRatio(new BigDecimal("0.10"))
				.etcRatio(BigDecimal.ZERO)
				.build();

			PortfolioTemplate moderatelyConservative = PortfolioTemplate.builder()
				.name("MODERATELY_CONSERVATIVE")
				.depositWithdrawalRatio(BigDecimal.ZERO)
				.savingTimeDepositRatio(new BigDecimal("0.50"))
				.investmentRatio(new BigDecimal("0.30"))
				.foreignCurrencyRatio(new BigDecimal("0.05"))
				.pensionRatio(new BigDecimal("0.15"))
				.etcRatio(BigDecimal.ZERO)
				.build();

			PortfolioTemplate balanced = PortfolioTemplate.builder()
				.name("BALANCED")
				.depositWithdrawalRatio(BigDecimal.ZERO)
				.savingTimeDepositRatio(new BigDecimal("0.30"))
				.investmentRatio(new BigDecimal("0.40"))
				.foreignCurrencyRatio(new BigDecimal("0.10"))
				.pensionRatio(new BigDecimal("0.15"))
				.etcRatio(new BigDecimal("0.05"))
				.build();

			PortfolioTemplate growthOriented = PortfolioTemplate.builder()
				.name("MODERATELY_AGGRESSIVE")
				.depositWithdrawalRatio(BigDecimal.ZERO)
				.savingTimeDepositRatio(new BigDecimal("0.20"))
				.investmentRatio(new BigDecimal("0.50"))
				.foreignCurrencyRatio(new BigDecimal("0.15"))
				.pensionRatio(new BigDecimal("0.10"))
				.etcRatio(new BigDecimal("0.05"))
				.build();

			PortfolioTemplate aggressive = PortfolioTemplate.builder()
				.name("AGGRESSIVE")
				.depositWithdrawalRatio(BigDecimal.ZERO)
				.savingTimeDepositRatio(new BigDecimal("0.10"))
				.investmentRatio(new BigDecimal("0.60"))
				.foreignCurrencyRatio(new BigDecimal("0.15"))
				.pensionRatio(new BigDecimal("0.05"))
				.etcRatio(new BigDecimal("0.10"))
				.build();

			portfolioTemplateRepository.saveAll(
				Arrays.asList(conservative, moderatelyConservative, balanced, growthOriented, aggressive));
		}

		if (advertisementRepository.count() == 0) {
			// 메인페이지용 광고

			Advertisement main1 = Advertisement.builder()
				.productType(AdvertisementProductType.INVESTMENT)
				.productName("미래에셋TIGER200중공업증권상장지수투자신탁(주식)")
				.locationType(AdvertisementLocationType.MAIN)
				.adType(AdvertisementAdType.HANA)
				// .security("수익증권 ETF") // 종목구분
				.riskRating("매우높은위험") // 위험등급
				.yield("78.64%") // 수익률
				.link("https://www.tigeretf.com/ko/product/search/detail/index.do?ksdFund=KR7139230007")
				.build();

			Advertisement main2 = Advertisement.builder()
				.productType(AdvertisementProductType.PENSION)
				.productName("삼성글로벌메타버스증권자투자신탁UH(주식)Cpe(퇴직연금)")
				.locationType(AdvertisementLocationType.MAIN)
				.adType(AdvertisementAdType.HANA)
				.riskRating("높은위험") // 위험등급
				.yield("51.93%") // 수익률
				.link("https://www.samsungfund.com/fund/product/view.do?id=K55105DK2904")
				.build();

			Advertisement main3 = Advertisement.builder()
				.productType(AdvertisementProductType.INVESTMENT)
				.productName("신한디딤글로벌EMP증권투자신탁[혼합-재간접형](종류C-re)")
				.locationType(AdvertisementLocationType.MAIN)
				.adType(AdvertisementAdType.HANA)
				.riskRating("보통위험") // 위험등급
				.yield("3.60%") // 수익률
				.link("https://www.shinhanfund.com/ko/pc/fund/view?fundCd=270879")
				.build();

			Advertisement main4 = Advertisement.builder()
				.productType(AdvertisementProductType.INVESTMENT)
				.productName("미래에셋하나1Q퇴직연금증권자투자신탁1호(채권혼합)C-P2e")
				.locationType(AdvertisementLocationType.MAIN)
				.riskRating("낮은위험") // 위험등급
				.yield("9.22%") // 수익률
				.link("https://investments.miraeasset.com/fund/view.do?fundGb=2&fundCd=484660")
				.build();

			Advertisement main5 = Advertisement.builder()
				.productType(AdvertisementProductType.SAVING_TIME_DEPOSIT)
				.productName("OSB저축은행 정기예금 1년 DB")
				.locationType(AdvertisementLocationType.MAIN)
				.adType(AdvertisementAdType.HANA)
				.riskRating("매우낮은위험") // 위험등급
				.yield("3.60%") // 수익률
				.link("https://www.osb.co.kr/ib20/mnu/HOM00214")
				.build();

			Advertisement community1 = Advertisement.builder()
				.locationType(AdvertisementLocationType.COMMUNITY)
				.adType(AdvertisementAdType.INVESTMENT)
				.bannerImageUrl(
					"https://ggumtlebucket.s3.ap-northeast-2.amazonaws.com/hanainvest.png")
				.link("https://www.hanaw.com/main/main/index.cmd")
				.build();

			Advertisement community2 = Advertisement.builder()
				.locationType(AdvertisementLocationType.COMMUNITY)
				.adType(AdvertisementAdType.EDUCATION)
				.bannerImageUrl(
					"https://ggumtlebucket.s3.ap-northeast-2.amazonaws.com/udemy.png")
				.link(
					"https://www.udemy.com/")
				.build();

			Advertisement community3 = Advertisement.builder()
				.locationType(AdvertisementLocationType.COMMUNITY)
				.adType(AdvertisementAdType.HOBBY)
				.bannerImageUrl(
					"https://ggumtlebucket.s3.ap-northeast-2.amazonaws.com/somssidang.png")
				.link("https://www.sssd.co.kr/main")
				.build();

			Advertisement community4 = Advertisement.builder()
				.locationType(AdvertisementLocationType.COMMUNITY)
				.adType(AdvertisementAdType.RETIREMENT)
				.bannerImageUrl(
					"https://ggumtlebucket.s3.ap-northeast-2.amazonaws.com/silvertown.png")
				.link("http://www.signumhaus.com/")
				.build();

			Advertisement community5 = Advertisement.builder()
				.locationType(AdvertisementLocationType.COMMUNITY)
				.adType(AdvertisementAdType.TRAVEL)
				.bannerImageUrl(
					"https://ggumtlebucket.s3.ap-northeast-2.amazonaws.com/hanatour.png")
				.link("https://www.hanatour.com/")
				.build();

			advertisementRepository.saveAll(
				Arrays.asList(main1, main2, main3, main4, main5, community1, community2, community3, community4,
					community5));
		}

		if (bucketRepository.findByIsRecommendedTrue().isEmpty()) {

			DreamAccount dreamAccount = DreamAccount.builder()
				.user(savedUser)
				.balance(BigDecimal.ZERO)
				.total(BigDecimal.ZERO)
				.build();
			dreamAccount = dreamAccountRepository.save(dreamAccount);

			List<Bucket> recommendedBuckets = new ArrayList<>();

			// DO - 하고 싶다 (10개)
			recommendedBuckets.addAll(Arrays.asList(
				Bucket.builder()
					.title("번지점프하기")
					.tagType(BucketTagType.DO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("200000"))
					.followers(150L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("스카이다이빙 도전")
					.tagType(BucketTagType.DO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("500000"))
					.followers(300L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("마라톤 완주하기")
					.tagType(BucketTagType.DO)
					.howTo(BucketHowTo.EFFORT)
					.followers(250L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("책 출간하기")
					.tagType(BucketTagType.DO)
					.howTo(BucketHowTo.EFFORT)
					.followers(400L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("서핑 배우기")
					.tagType(BucketTagType.DO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("1000000"))
					.followers(180L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("단편영화 찍기")
					.tagType(BucketTagType.DO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("5000000"))
					.followers(350L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("스쿠버다이빙 자격증 따기")
					.tagType(BucketTagType.DO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("800000"))
					.followers(200L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("오로라 보기")
					.tagType(BucketTagType.DO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("3000000"))
					.followers(450L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("사막에서 별보기")
					.tagType(BucketTagType.DO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("2500000"))
					.followers(280L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("철인 3종 경기 완주하기")
					.tagType(BucketTagType.DO)
					.howTo(BucketHowTo.EFFORT)
					.followers(320L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build()
			));

			// BE - 되고 싶다 (10개)
			recommendedBuckets.addAll(Arrays.asList(
				Bucket.builder()
					.title("파일럿 되기")
					.tagType(BucketTagType.BE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("50000000"))
					.followers(500L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("요리사 되기")
					.tagType(BucketTagType.BE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("20000000"))
					.followers(350L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("CEO 되기")
					.tagType(BucketTagType.BE)
					.howTo(BucketHowTo.EFFORT)
					.followers(600L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("프로게이머 되기")
					.tagType(BucketTagType.BE)
					.howTo(BucketHowTo.EFFORT)
					.followers(420L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("유튜버 되기")
					.tagType(BucketTagType.BE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("5000000"))
					.followers(550L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("작가 되기")
					.tagType(BucketTagType.BE)
					.howTo(BucketHowTo.EFFORT)
					.followers(380L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("프로포토그래퍼 되기")
					.tagType(BucketTagType.BE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("10000000"))
					.followers(320L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("바리스타 되기")
					.tagType(BucketTagType.BE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("3000000"))
					.followers(280L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("프로서퍼 되기")
					.tagType(BucketTagType.BE)
					.howTo(BucketHowTo.EFFORT)
					.followers(310L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("소믈리에 되기")
					.tagType(BucketTagType.BE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("15000000"))
					.followers(290L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build()
			));

			// HAVE - 가지고 싶다 (10개)
			recommendedBuckets.addAll(Arrays.asList(
				Bucket.builder()
					.title("테슬라 모델 S 구매")
					.tagType(BucketTagType.HAVE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("150000000"))
					.followers(400L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("나만의 카페 오픈")
					.tagType(BucketTagType.HAVE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("200000000"))
					.followers(480L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("요트 구매")
					.tagType(BucketTagType.HAVE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("500000000"))
					.followers(350L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("제주도에 집 구매")
					.tagType(BucketTagType.HAVE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("300000000"))
					.followers(520L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("명품시계 구매")
					.tagType(BucketTagType.HAVE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("20000000"))
					.followers(280L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("캠핑카 구매")
					.tagType(BucketTagType.HAVE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("80000000"))
					.followers(420L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("프라이빗 홈 짐")
					.tagType(BucketTagType.HAVE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("30000000"))
					.followers(300L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("나만의 서재")
					.tagType(BucketTagType.HAVE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("20000000"))
					.followers(340L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("최신형 카메라")
					.tagType(BucketTagType.HAVE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("5000000"))
					.followers(260L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("나만의 텃밭")
					.tagType(BucketTagType.HAVE)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("50000000"))
					.followers(380L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build()
			));

			// GO - 가고 싶다 (10개)
			recommendedBuckets.addAll(Arrays.asList(
				Bucket.builder()
					.title("몰디브 여행")
					.tagType(BucketTagType.GO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("5000000"))
					.followers(450L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("세계일주")
					.tagType(BucketTagType.GO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("50000000"))
					.followers(600L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("남극 여행")
					.tagType(BucketTagType.GO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("20000000"))
					.followers(380L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("산티아고 순례길")
					.tagType(BucketTagType.GO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("8000000"))
					.followers(420L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("아프리카 사파리")
					.tagType(BucketTagType.GO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("15000000"))
					.followers(340L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("에베레스트 베이스캠프")
					.tagType(BucketTagType.GO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("10000000"))
					.followers(480L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("그랜드캐니언 트레킹")
					.tagType(BucketTagType.GO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("7000000"))
					.followers(320L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("마추픽추 방문")
					.tagType(BucketTagType.GO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("8000000"))
					.followers(520L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("북극광 여행")
					.tagType(BucketTagType.GO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("12000000"))
					.followers(440L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("사하라 사막 여행")
					.tagType(BucketTagType.GO)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("9000000"))
					.followers(380L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build()
			));

			// LEARN - 10개
			recommendedBuckets.addAll(Arrays.asList(
				Bucket.builder()
					.title("피아노 배우기")
					.tagType(BucketTagType.LEARN)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("2400000"))
					.followers(300L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("프로그래밍 마스터하기")
					.tagType(BucketTagType.LEARN)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("5000000"))
					.followers(450L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("와인 소믈리에 자격증")
					.tagType(BucketTagType.LEARN)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("3000000"))
					.followers(280L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("외국어 5개 마스터")
					.tagType(BucketTagType.LEARN)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("10000000"))
					.followers(520L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("서핑 마스터하기")
					.tagType(BucketTagType.LEARN)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("3000000"))
					.followers(340L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("프로 사진작가 과정")
					.tagType(BucketTagType.LEARN)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("4000000"))
					.followers(380L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("클래식 기타 배우기")
					.tagType(BucketTagType.LEARN)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("2000000"))
					.followers(260L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("제과제빵 자격증")
					.tagType(BucketTagType.LEARN)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("3500000"))
					.followers(420L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("플라워 아트 과정")
					.tagType(BucketTagType.LEARN)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("2500000"))
					.followers(290L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build(),
				Bucket.builder()
					.title("드론 조종 자격증")
					.tagType(BucketTagType.LEARN)
					.howTo(BucketHowTo.MONEY)
					.goalAmount(new BigDecimal("1500000"))
					.followers(310L)
					.isRecommended(true)
					.isDueSet(false)
					.status(BucketStatus.DOING)
					.dreamAccount(dreamAccount)
					.user(savedUser)
					.build()
			));

			bucketRepository.saveAll(recommendedBuckets);
		}

	}
}
