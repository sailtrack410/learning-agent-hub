package com.workforcehub.file;

import com.workforcehub.file.service.OfficePdfConverter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "LIBREOFFICE_BIN", matches = ".+")
class OfficePdfConverterRealIntegrationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void shouldConvertRealDocxWithLibreOffice() throws Exception {
        Path source = tempDirectory.resolve("课程讲义.docx");
        try (XWPFDocument document = new XWPFDocument(); var output = Files.newOutputStream(source)) {
            document.createParagraph().createRun().setText("Java Web 应用开发课程讲义");
            document.write(output);
        }

        Path pdf = new OfficePdfConverter(System.getenv("LIBREOFFICE_BIN"), 30)
            .convert(source, tempDirectory, tempDirectory.resolve("profile"));

        assertTrue(Files.size(pdf) > 100);
        assertTrue(new String(Files.readAllBytes(pdf), 0, 4).equals("%PDF"));
    }
}
