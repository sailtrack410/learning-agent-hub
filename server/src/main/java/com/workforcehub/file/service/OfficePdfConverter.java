package com.workforcehub.file.service;

import com.workforcehub.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class OfficePdfConverter {
    private final String command;
    private final Duration timeout;

    public OfficePdfConverter(@Value("${workforce.file.preview.libreoffice-command:soffice}") String command,
                              @Value("${workforce.file.preview.timeout-seconds:120}") long timeoutSeconds) {
        this.command = command;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public Path convert(Path source, Path outputDirectory, Path profileDirectory) {
        try {
            Process process = new ProcessBuilder(
                command,
                "-env:UserInstallation=" + profileDirectory.toUri(),
                "--headless",
                "--convert-to", "pdf",
                "--outdir", outputDirectory.toString(),
                source.toString()
            ).redirectErrorStream(true).redirectOutput(outputDirectory.resolve("conversion.log").toFile()).start();
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BizException(422, "文件预览转换超时");
            }
            Path pdf = outputDirectory.resolve(baseName(source.getFileName().toString()) + ".pdf");
            if (process.exitValue() != 0 || !Files.isRegularFile(pdf)) {
                throw new BizException(422, "文件预览转换失败");
            }
            return pdf;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(422, "文件预览转换服务不可用");
        }
    }

    private String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
