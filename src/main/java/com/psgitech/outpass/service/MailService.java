package com.psgitech.outpass.service;

import com.psgitech.outpass.config.OutpassProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** E-mail runs asynchronously so it can never slow down the gate or an approval click. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {
    private final ObjectProvider<JavaMailSender> sender;
    private final OutpassProperties props;

    @Async
    public void send(String to, String subject, String body) {
        if (!props.isMailEnabled()) return;
        JavaMailSender s = sender.getIfAvailable();
        if (s == null) return;
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(props.getMailFrom());
            m.setTo(to);
            m.setSubject("[PSGiTech Outpass] " + subject);
            m.setText(body);
            s.send(m);
        } catch (Exception ex) {
            log.warn("Could not send e-mail to {}: {}", to, ex.getMessage());
        }
    }
}
