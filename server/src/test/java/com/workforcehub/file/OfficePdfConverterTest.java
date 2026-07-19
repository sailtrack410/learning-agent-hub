package com.workforcehub.file;

import com.workforcehub.file.service.OfficePdfConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficePdfConverterTest {
    @TempDir
    Path tempDirectory;

    @Test
    void shouldRunConfiguredConverterAndReturnGeneratedPdf() throws Exception {
        Path converter = tempDirectory.resolve("fake-soffice.sh");
        Files.writeString(converter, """
            #!/bin/sh
            previous=""
            output=""
            for argument in "$@"; do
              if [ "$previous" = "--outdir" ]; then output="$argument"; fi
              previous="$argument"
            done
            source_name=$(basename "$previous")
            base_name=${source_name%.*}
            printf '%%PDF-1.4\n' > "$output/$base_name.pdf"
            """);
        converter.toFile().setExecutable(true);
        Path source = tempDirectory.resolve("source.docx");
        Files.writeString(source, "test");

        Path pdf = new OfficePdfConverter(converter.toString(), 5)
            .convert(source, tempDirectory, tempDirectory.resolve("profile"));

        assertEquals("source.pdf", pdf.getFileName().toString());
        assertTrue(Files.size(pdf) > 0);
    }
}
