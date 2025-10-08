package com.hana4.ggumtle.service.sms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;

@Service
@Profile("prod")
public class RealSmsService implements SmsService {

	@Value("${sms.api.sender}")
	private String sender;

	final DefaultMessageService messageService;

	public RealSmsService(@Value("${sms.api.key}") String apiKey,
		@Value("${sms.api.secret}") String apiSecret) {
		this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
	}

	public SingleMessageSentResponse sendOne(String userTel, String verificationCode) {
		Message message = new Message();
		message.setFrom(sender);
		message.setTo(userTel);
		message.setText("[꿈틀] 귀하의 인증번호는 " + verificationCode + " 입니다.");

		return this.messageService.sendOne(new SingleMessageSendingRequest(message));
	}
}
