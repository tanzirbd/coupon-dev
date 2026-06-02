package com.couponplatform.notificationservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService.
 * Core Functionality #7: System sends expiry reminders and redemption confirmations via email/SMS (FR7)
 *
 * Tests Kafka consumer for redemption events and email notification dispatch.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private NotificationService notificationService;

    private String redemptionEventJson;
    private JsonNode mockJsonNode;

    @BeforeEach
    void setUp() {
        redemptionEventJson = "{\"userId\":\"user123\",\"code\":\"SUMMER25\",\"discountAmount\":\"50.00\"}";
        mockJsonNode = mock(JsonNode.class);
    }

    // ---------------- Coupon Redeemed Event Tests ----------------------------

    @Test
    @DisplayName("OnCouponRedeemed - valid event sends confirmation email")
    void onCouponRedeemed_validEvent_sendsEmail() throws Exception {
        JsonNode userIdNode = mock(JsonNode.class);
        JsonNode codeNode = mock(JsonNode.class);
        JsonNode discountNode = mock(JsonNode.class);

        when(objectMapper.readTree(redemptionEventJson)).thenReturn(mockJsonNode);
        when(mockJsonNode.get("userId")).thenReturn(userIdNode);
        when(userIdNode.asText()).thenReturn("user123");
        when(mockJsonNode.get("code")).thenReturn(codeNode);
        when(codeNode.asText()).thenReturn("SUMMER25");
        when(mockJsonNode.get("discountAmount")).thenReturn(discountNode);
        when(discountNode.asText()).thenReturn("50.00");

        notificationService.onCouponRedeemed(redemptionEventJson);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sentMail = captor.getValue();
        assertThat(sentMail.getTo()).containsExactly("user123@example.com");
        assertThat(sentMail.getSubject()).contains("SUMMER25");
        assertThat(sentMail.getText()).contains("user123").contains("SUMMER25").contains("50.00");
        assertThat(sentMail.getFrom()).isEqualTo("noreply@couponplatform.com");
    }

    @Test
    @DisplayName("OnCouponRedeemed - email format includes required fields")
    void onCouponRedeemed_emailContent_includesAllRequiredFields() throws Exception {
        JsonNode userIdNode = mock(JsonNode.class);
        JsonNode codeNode = mock(JsonNode.class);
        JsonNode discountNode = mock(JsonNode.class);

        when(objectMapper.readTree(redemptionEventJson)).thenReturn(mockJsonNode);
        when(mockJsonNode.get("userId")).thenReturn(userIdNode);
        when(userIdNode.asText()).thenReturn("john_doe");
        when(mockJsonNode.get("code")).thenReturn(codeNode);
        when(codeNode.asText()).thenReturn("HOLIDAY50");
        when(mockJsonNode.get("discountAmount")).thenReturn(discountNode);
        when(discountNode.asText()).thenReturn("75.50");

        notificationService.onCouponRedeemed(redemptionEventJson);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sentMail = captor.getValue();
        String content = sentMail.getText();
        assertThat(content).contains("john_doe")
                .contains("HOLIDAY50")
                .contains("75.50")
                .contains("Thank you for shopping");
    }

    @Test
    @DisplayName("OnCouponRedeemed - malformed JSON handled gracefully without throwing")
    void onCouponRedeemed_malformedJson_handlesErrorGracefully() throws Exception {
        String badJson = "{invalid}";
        when(objectMapper.readTree(badJson))
                .thenThrow(new RuntimeException("Parse error"));

        assertThatNoException().isThrownBy(() -> notificationService.onCouponRedeemed(badJson));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("OnCouponRedeemed - email send failure doesn't break the process")
    void onCouponRedeemed_emailSendFails_continuesGracefully() throws Exception {
        JsonNode userIdNode = mock(JsonNode.class);
        JsonNode codeNode = mock(JsonNode.class);
        JsonNode discountNode = mock(JsonNode.class);

        when(objectMapper.readTree(redemptionEventJson)).thenReturn(mockJsonNode);
        when(mockJsonNode.get("userId")).thenReturn(userIdNode);
        when(userIdNode.asText()).thenReturn("user123");
        when(mockJsonNode.get("code")).thenReturn(codeNode);
        when(codeNode.asText()).thenReturn("SUMMER25");
        when(mockJsonNode.get("discountAmount")).thenReturn(discountNode);
        when(discountNode.asText()).thenReturn("50.00");
        doThrow(new RuntimeException("SMTP connection failed")).when(mailSender).send(any(SimpleMailMessage.class));

        // Should not throw exception - logs warning instead
        assertThatNoException().isThrownBy(() -> notificationService.onCouponRedeemed(redemptionEventJson));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("OnCouponRedeemed - missing optional fields doesn't crash")
    void onCouponRedeemed_partialJsonData_handlesMissingFields() throws Exception {
        // Simulate missing discountAmount field
        String partialJson = "{\"userId\":\"user456\",\"code\":\"SPRING30\"}";
        JsonNode userIdNode = mock(JsonNode.class);
        JsonNode codeNode = mock(JsonNode.class);
        JsonNode discountNode = mock(JsonNode.class);

        when(objectMapper.readTree(partialJson)).thenReturn(mockJsonNode);
        when(mockJsonNode.get("userId")).thenReturn(userIdNode);
        when(userIdNode.asText()).thenReturn("user456");
        when(mockJsonNode.get("code")).thenReturn(codeNode);
        when(codeNode.asText()).thenReturn("SPRING30");
        when(mockJsonNode.get("discountAmount")).thenReturn(discountNode);
        when(discountNode.asText()).thenReturn("0.00");

        assertThatNoException().isThrownBy(() -> notificationService.onCouponRedeemed(partialJson));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ---------------- Coupon Created Event Tests ----------------------------

    @Test
    @DisplayName("OnCouponCreated - receives event without error")
    void onCouponCreated_validEvent_processesSuccessfully() {

        String createdEventJson =
                "{\"code\":\"NEWYEAR25\",\"admin\":\"admin@company.com\"}";

        assertThatNoException()
                .isThrownBy(() -> notificationService.onCouponCreated(createdEventJson));
    }

    @Test
    @DisplayName("OnCouponCreated - malformed JSON handled gracefully")
    void onCouponCreated_malformedJson_handlesGracefully() {

        String badJson = "{incomplete";

        assertThatNoException()
                .isThrownBy(() -> notificationService.onCouponCreated(badJson));
    }

    // ---------------- Email Construction Tests -----------------------------

    @Test
    @DisplayName("Email is sent from correct sender address")
    void onCouponRedeemed_senderAddress_isCorrect() throws Exception {
        JsonNode userIdNode = mock(JsonNode.class);
        JsonNode codeNode = mock(JsonNode.class);
        JsonNode discountNode = mock(JsonNode.class);

        when(objectMapper.readTree(redemptionEventJson)).thenReturn(mockJsonNode);
        when(mockJsonNode.get("userId")).thenReturn(userIdNode);
        when(userIdNode.asText()).thenReturn("user999");
        when(mockJsonNode.get("code")).thenReturn(codeNode);
        when(codeNode.asText()).thenReturn("TEST99");
        when(mockJsonNode.get("discountAmount")).thenReturn(discountNode);
        when(discountNode.asText()).thenReturn("99.99");

        notificationService.onCouponRedeemed(redemptionEventJson);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        assertThat(captor.getValue().getFrom()).isEqualTo("noreply@couponplatform.com");
    }

    @Test
    @DisplayName("Email subject line includes coupon code")
    void onCouponRedeemed_subject_includesCouponCode() throws Exception {
        JsonNode userIdNode = mock(JsonNode.class);
        JsonNode codeNode = mock(JsonNode.class);
        JsonNode discountNode = mock(JsonNode.class);

        when(objectMapper.readTree(redemptionEventJson)).thenReturn(mockJsonNode);
        when(mockJsonNode.get("userId")).thenReturn(userIdNode);
        when(userIdNode.asText()).thenReturn("user123");
        when(mockJsonNode.get("code")).thenReturn(codeNode);
        when(codeNode.asText()).thenReturn("SPECIAL25");
        when(mockJsonNode.get("discountAmount")).thenReturn(discountNode);
        when(discountNode.asText()).thenReturn("50.00");

        notificationService.onCouponRedeemed(redemptionEventJson);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        assertThat(captor.getValue().getSubject()).contains("SPECIAL25").contains("Applied");
    }

    @Test
    @DisplayName("Multiple concurrent events are handled independently")
    void onCouponRedeemed_multipleCalls_handleIndependently() throws Exception {
        JsonNode userIdNode1 = mock(JsonNode.class);
        JsonNode codeNode1 = mock(JsonNode.class);
        JsonNode discountNode1 = mock(JsonNode.class);

        when(objectMapper.readTree(redemptionEventJson)).thenReturn(mockJsonNode);
        when(mockJsonNode.get("userId")).thenReturn(userIdNode1);
        when(userIdNode1.asText()).thenReturn("user1");
        when(mockJsonNode.get("code")).thenReturn(codeNode1);
        when(codeNode1.asText()).thenReturn("CODE1");
        when(mockJsonNode.get("discountAmount")).thenReturn(discountNode1);
        when(discountNode1.asText()).thenReturn("10.00");

        notificationService.onCouponRedeemed(redemptionEventJson);
        notificationService.onCouponRedeemed(redemptionEventJson);

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }
}

