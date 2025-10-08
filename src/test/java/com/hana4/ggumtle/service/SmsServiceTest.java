package com.hana4.ggumtle.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.model.MessageType;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;

import com.hana4.ggumtle.service.sms.RealSmsService;
import com.hana4.ggumtle.service.sms.SmsService;

class SmsServiceTest {

	@Mock
	private DefaultMessageService messageService;

	private SmsService smsService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		smsService = new RealSmsService("test-api-key", "test-api-secret");
		ReflectionTestUtils.setField(smsService, "messageService", messageService);
		ReflectionTestUtils.setField(smsService, "sender", "testSender");
	}

	@Test
	void testSendOne() {
		// Given
		String userTel = "01012345678";
		String verificationCode = "123456";
		SingleMessageSentResponse mockResponse = new SingleMessageSentResponse(
			"groupId",  // groupId
			"to",      // to
			"from",    // from
			MessageType.SMS,  // type
			"success", // statusMessage
			"KR",     // country
			"msgId",  // messageId
			"2000",   // statusCode
			"accId"   // accountId
		);

		when(messageService.sendOne(any(SingleMessageSendingRequest.class))).thenReturn(mockResponse);

		// When
		SingleMessageSentResponse response = smsService.sendOne(userTel, verificationCode);

		// Then
		assertEquals("2000", response.getStatusCode());
		assertEquals("success", response.getStatusMessage());

		verify(messageService, times(1)).sendOne(argThat(request -> {
			Message message = request.getMessage();
			assert message.getFrom() != null;
			if (!message.getFrom().equals("testSender"))
				return false;
			assert message.getTo() != null;
			if (!message.getTo().equals(userTel))
				return false;
			assert message.getText() != null;
			return message.getText().equals("[꿈틀] 귀하의 인증번호는 " + verificationCode + " 입니다.");
		}));
	}
}
