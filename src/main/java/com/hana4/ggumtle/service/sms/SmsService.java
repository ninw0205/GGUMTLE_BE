package com.hana4.ggumtle.service.sms;

import net.nurigo.sdk.message.response.SingleMessageSentResponse;

public interface SmsService {
	SingleMessageSentResponse sendOne(String userTel, String verificationCode);
}
