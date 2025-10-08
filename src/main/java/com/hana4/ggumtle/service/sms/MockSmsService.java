package com.hana4.ggumtle.service.sms;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import net.nurigo.sdk.message.response.SingleMessageSentResponse;

import lombok.extern.slf4j.Slf4j;

@Service
@Profile("local")
@Slf4j
public class MockSmsService implements SmsService {

	@Override
	public SingleMessageSentResponse sendOne(String userTel, String verificationCode) {
		log.info("[MOCK 꿈틀] 귀하의 인증번호는 {}입니다.", "11111");
		return null;
	}
}
