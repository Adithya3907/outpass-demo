package com.psgitech.outpass.service;

import com.psgitech.outpass.domain.Escalation;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter @Setter
public class RequestForm {
    private String reason;
    private String customReason;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate permissionDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime exitTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime expectedReturnTime;

    private String description;
    private Escalation escalation = Escalation.TUTOR;
}
