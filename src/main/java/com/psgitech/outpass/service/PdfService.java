package com.psgitech.outpass.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.psgitech.outpass.config.OutpassProperties;
import com.psgitech.outpass.domain.OutpassRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.ITemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PdfService {
    private final ITemplateEngine engine;
    private final QrService qr;
    private final OutpassProperties props;
    private final Fmt fmt;

    public byte[] outpassPdf(OutpassRequest r) {
        try {
            Context ctx = new Context();
            ctx.setVariable("r", r);
            ctx.setVariable("fmt", fmt);
            ctx.setVariable("collegeName", props.getCollegeName());
            ctx.setVariable("logo", "data:image/png;base64," + Base64.getEncoder().encodeToString(readLogo()));
            if (r.getQrToken() != null) {
                ctx.setVariable("qr", "data:image/png;base64," + Base64.getEncoder().encodeToString(qr.png(r.getQrToken(), 360)));
            }
            String html = engine.process("pdf/outpass", ctx);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            b.withHtmlContent(html, null);
            b.toStream(out);
            b.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF generation failed", e);
        }
    }

    private byte[] readLogo() throws Exception {
        try (InputStream in = new ClassPathResource("static/img/psgitech-logo.png").getInputStream()) {
            return in.readAllBytes();
        }
    }
}
