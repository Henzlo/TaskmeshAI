package com.taskmesh.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@Slf4j
public class PdfExtractionService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 10000;

    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        boolean isPdf = "application/pdf".equalsIgnoreCase(contentType)
            || (filename != null && filename.toLowerCase().endsWith(".pdf"));

        if (!isPdf) {
            throw new IllegalArgumentException("Only PDF files are supported");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File too large — max 10MB");
        }

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            String text = new PDFTextStripper().getText(document);

            if (text == null || text.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "Could not extract text — the PDF may be scanned/image-based. Please paste the text manually.");
            }

            int pageCount = document.getNumberOfPages();
            String extracted = text.length() > MAX_TEXT_LENGTH
                ? text.substring(0, MAX_TEXT_LENGTH)
                : text;

            log.info("PDF EXTRACTED | filename={} | pages={} | chars={}",
                filename, pageCount, extracted.length());

            return extracted;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            log.error("PDF extraction failed | filename={} | error={}", filename, e.getMessage(), e);
            throw new IllegalArgumentException("Failed to read PDF file: " + e.getMessage());
        }
    }
}
