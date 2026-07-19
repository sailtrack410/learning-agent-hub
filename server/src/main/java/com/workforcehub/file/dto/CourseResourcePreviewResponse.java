package com.workforcehub.file.dto;

public record CourseResourcePreviewResponse(
    Long attachmentRefId,
    String fileName,
    String previewStatus,
    String previewUrl,
    String downloadUrl,
    String errorMessage
) {}
