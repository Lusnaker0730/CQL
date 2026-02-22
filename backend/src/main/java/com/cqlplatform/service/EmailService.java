package com.cqlplatform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@cqlplatform.com}")
    private String fromAddress;

    public void sendPasswordResetEmail(String toEmail, String username, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("台灣CQL概念平台 - Password Reset");
            helper.setText(buildResetEmailHtml(username, resetLink), true);

            mailSender.send(message);
            log.info("Password reset email sent to user: {}", username);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to user: {}", username, e);
        }
    }

    private String buildResetEmailHtml(String username, String resetLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="margin:0;padding:0;font-family:Arial,sans-serif;background-color:#f4f7f9;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;margin:40px auto;">
                    <tr>
                        <td style="background:linear-gradient(135deg,#0D7377,#1B3A5C);padding:32px;text-align:center;border-radius:12px 12px 0 0;">
                            <h1 style="color:white;margin:0;font-size:24px;">台灣CQL概念平台</h1>
                            <p style="color:rgba(255,255,255,0.8);margin:8px 0 0;font-size:14px;">Password Reset Request</p>
                        </td>
                    </tr>
                    <tr>
                        <td style="background:white;padding:32px;border-radius:0 0 12px 12px;box-shadow:0 2px 8px rgba(0,0,0,0.1);">
                            <p style="color:#333;font-size:16px;">Hi <strong>%s</strong>,</p>
                            <p style="color:#555;font-size:14px;line-height:1.6;">
                                We received a request to reset your password. Click the button below to create a new password.
                                This link will expire in <strong>30 minutes</strong>.
                            </p>
                            <table width="100%%" cellpadding="0" cellspacing="0" style="margin:24px 0;">
                                <tr>
                                    <td align="center">
                                        <a href="%s" style="display:inline-block;padding:14px 32px;background:linear-gradient(135deg,#0D7377,#1B3A5C);color:white;text-decoration:none;border-radius:8px;font-weight:bold;font-size:16px;">
                                            Reset Password
                                        </a>
                                    </td>
                                </tr>
                            </table>
                            <p style="color:#888;font-size:12px;line-height:1.6;">
                                If you didn't request this, you can safely ignore this email. Your password will remain unchanged.
                            </p>
                            <hr style="border:none;border-top:1px solid #eee;margin:24px 0;">
                            <p style="color:#aaa;font-size:11px;text-align:center;">
                                台灣CQL概念平台 &mdash; Clinical Quality Language
                            </p>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(username, resetLink);
    }
}
