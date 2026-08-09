package com.verifiedai.problem.infrastructure.preprocessing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.problem-assets.preprocessing")
public record ProblemAssetPreprocessingProperties(
    String processorName,
    String processorVersion,
    String configurationVersion,
    long maxSourceBytes,
    long maxDecodePixels,
    int ocrOptimizedMaxLongEdge,
    int thumbnailMaxLongEdge,
    float jpegQuality,
    int minCropPixels,
    double blurVarianceWarning,
    double glareCellSaturationRatio,
    double glareOverallSaturationMax,
    double contrastStdDevWarning,
    int resolutionMinLongEdge
) {
    public ProblemAssetPreprocessingProperties {
        processorName = blankToDefault(processorName, "DOCUMENT_PREPROCESSOR");
        processorVersion = blankToDefault(processorVersion, "1.0");
        configurationVersion = blankToDefault(configurationVersion, "capture-quality-v1");
        maxSourceBytes = maxSourceBytes <= 0 ? 20L * 1024L * 1024L : maxSourceBytes;
        maxDecodePixels = maxDecodePixels <= 0 ? 36_000_000L : maxDecodePixels;
        ocrOptimizedMaxLongEdge = ocrOptimizedMaxLongEdge <= 0 ? 2400 : ocrOptimizedMaxLongEdge;
        thumbnailMaxLongEdge = thumbnailMaxLongEdge <= 0 ? 360 : thumbnailMaxLongEdge;
        jpegQuality = jpegQuality <= 0 || jpegQuality > 1 ? 0.92F : jpegQuality;
        minCropPixels = minCropPixels <= 0 ? 32 : minCropPixels;
        blurVarianceWarning = blurVarianceWarning <= 0 ? 90.0D : blurVarianceWarning;
        glareCellSaturationRatio = glareCellSaturationRatio <= 0 || glareCellSaturationRatio > 1 ? 0.30D : glareCellSaturationRatio;
        glareOverallSaturationMax = glareOverallSaturationMax <= 0 || glareOverallSaturationMax > 1 ? 0.60D : glareOverallSaturationMax;
        contrastStdDevWarning = contrastStdDevWarning <= 0 ? 28.0D : contrastStdDevWarning;
        resolutionMinLongEdge = resolutionMinLongEdge <= 0 ? 900 : resolutionMinLongEdge;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
