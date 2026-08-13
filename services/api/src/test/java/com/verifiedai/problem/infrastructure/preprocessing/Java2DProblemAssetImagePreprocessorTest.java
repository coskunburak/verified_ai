package com.verifiedai.problem.infrastructure.preprocessing;

import com.verifiedai.problem.domain.model.asset.ProblemAssetDerivativeKind;
import com.verifiedai.problem.domain.model.asset.ProblemAssetQualitySeverity;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class Java2DProblemAssetImagePreprocessorTest {
    private final Java2DProblemAssetImagePreprocessor preprocessor = new Java2DProblemAssetImagePreprocessor(properties());

    @Test
    void sharpEquationImageProducesOcrDerivativeThumbnailAndQualitySignals() {
        ProblemAssetImagePreprocessingResult result = preprocessor.process(equationJpeg(1200, 900), 0, 0, 1, 1);

        assertThat(result.sourceWidth()).isEqualTo(1200);
        assertThat(result.sourceHeight()).isEqualTo(900);
        assertThat(result.perspectiveApplied()).isFalse();
        assertThat(result.derivatives()).hasSize(2);
        assertThat(result.derivatives())
            .extracting(ProblemAssetImagePreprocessingResult.DerivativeImage::kind)
            .containsExactly(ProblemAssetDerivativeKind.OCR_OPTIMIZED, ProblemAssetDerivativeKind.THUMBNAIL);
        assertThat(result.derivatives())
            .filteredOn(derivative -> derivative.kind() == ProblemAssetDerivativeKind.THUMBNAIL)
            .singleElement()
            .satisfies(derivative -> assertThat(Math.max(derivative.width(), derivative.height())).isLessThanOrEqualTo(360));
        assertThat(result.qualitySignals()).hasSize(5);
    }

    @Test
    void localizedWhitePatchRaisesGlareWarningWithoutTreatingWhitePageAsGlare() {
        ProblemAssetImagePreprocessingResult whitePage = preprocessor.process(equationJpeg(1200, 900), 0, 0, 1, 1);
        ProblemAssetImagePreprocessingResult glare = preprocessor.process(glareJpeg(1200, 900), 0, 0, 1, 1);

        assertThat(signalSeverity(whitePage).get("GLARE")).isEqualTo(ProblemAssetQualitySeverity.PASS);
        assertThat(signalSeverity(glare).get("GLARE")).isEqualTo(ProblemAssetQualitySeverity.WARNING);
    }

    @Test
    void tightCropAndLowResolutionReturnWarningsButStillProduceDerivative() {
        ProblemAssetImagePreprocessingResult result = preprocessor.process(lowContrastJpeg(480, 360), 0.40, 0.40, 0.20, 0.20);

        assertThat(result.qualityOutcome().name()).isEqualTo("WARNING");
        assertThat(signalSeverity(result).get("CROP_FRAMING")).isEqualTo(ProblemAssetQualitySeverity.WARNING);
        assertThat(signalSeverity(result).get("RESOLUTION")).isEqualTo(ProblemAssetQualitySeverity.WARNING);
        assertThat(result.derivatives())
            .filteredOn(derivative -> derivative.kind() == ProblemAssetDerivativeKind.OCR_OPTIMIZED)
            .singleElement()
            .satisfies(derivative -> assertThat(derivative.bytes()).isNotEmpty());
    }

    @Test
    void malformedBytesAreRejectedBeforeDerivativeGeneration() {
        assertThatThrownBy(() -> preprocessor.process("not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, 0, 1, 1))
            .isInstanceOf(ProblemAssetImagePreprocessingException.class)
            .extracting(exception -> ((ProblemAssetImagePreprocessingException) exception).apiErrorCode())
            .isEqualTo(ApiErrorCode.ASSET_FORMAT_INVALID);
    }

    @Test
    void exifOrientationsAreNormalizedBeforeCropProcessing() {
        byte[] source = equationJpeg(640, 480);

        for (int orientation = 1; orientation <= 8; orientation += 1) {
            ProblemAssetImagePreprocessingResult result = preprocessor.process(jpegWithExifOrientation(source, orientation), 0, 0, 1, 1);

            if (orientation >= 5) {
                assertThat(result.sourceWidth()).isEqualTo(480);
                assertThat(result.sourceHeight()).isEqualTo(640);
            } else {
                assertThat(result.sourceWidth()).isEqualTo(640);
                assertThat(result.sourceHeight()).isEqualTo(480);
            }
            assertThat(result.orientationNormalized()).isEqualTo(orientation != 1);
        }
    }

    @Test
    void contrastNormalizationPreservesDarkMathematicalMarksInDerivedImage() throws Exception {
        ProblemAssetImagePreprocessingResult result = preprocessor.process(lowContrastJpeg(1200, 900), 0, 0, 1, 1);
        byte[] ocrBytes = result.derivatives()
            .stream()
            .filter(derivative -> derivative.kind() == ProblemAssetDerivativeKind.OCR_OPTIMIZED)
            .findFirst()
            .orElseThrow()
            .bytes();
        BufferedImage derived = ImageIO.read(new ByteArrayInputStream(ocrBytes));

        assertThat(countDarkPixels(derived)).isGreaterThan(200);
        assertThat(result.contrastNormalized()).isTrue();
    }

    private Map<String, ProblemAssetQualitySeverity> signalSeverity(ProblemAssetImagePreprocessingResult result) {
        return result.qualitySignals()
            .stream()
            .collect(Collectors.toMap(signal -> signal.signalType().name(), ProblemAssetImagePreprocessingResult.QualitySignal::severity));
    }

    private static int countDarkPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y += 1) {
            for (int x = 0; x < image.getWidth(); x += 1) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                if ((red + green + blue) / 3 < 90) {
                    count += 1;
                }
            }
        }
        return count;
    }

    private static byte[] equationJpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            drawEquation(graphics, width, height, Color.BLACK, 96);
        } finally {
            graphics.dispose();
        }
        return jpeg(image);
    }

    private static byte[] glareJpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(235, 235, 235));
            graphics.fillRect(0, 0, width, height);
            drawEquation(graphics, width, height, Color.BLACK, 96);
            graphics.setColor(Color.WHITE);
            graphics.fillOval(width - 360, 80, 260, 220);
        } finally {
            graphics.dispose();
        }
        return jpeg(image);
    }

    private static byte[] lowContrastJpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(224, 224, 224));
            graphics.fillRect(0, 0, width, height);
            drawEquation(graphics, width, height, new Color(145, 145, 145), Math.max(32, width / 14));
        } finally {
            graphics.dispose();
        }
        return jpeg(image);
    }

    private static void drawEquation(Graphics2D graphics, int width, int height, Color color, int fontSize) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(Math.max(2, fontSize / 18)));
        graphics.setFont(new Font("Serif", Font.BOLD, fontSize));
        graphics.drawString("x^2 + 3x = 10", Math.max(20, width / 10), height / 2);
        graphics.drawLine(Math.max(20, width / 10), height / 2 + fontSize / 2, Math.min(width - 40, width / 10 + fontSize * 7), height / 2 + fontSize / 2);
        graphics.drawString("2x - 5", Math.max(20, width / 10), height / 2 + fontSize + 40);
    }

    private static byte[] jpeg(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpeg", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] jpegWithExifOrientation(byte[] jpeg, int orientation) {
        byte[] segment = exifOrientationSegment(orientation);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(jpeg.length + segment.length)) {
            output.write(jpeg, 0, 2);
            output.write(segment);
            output.write(jpeg, 2, jpeg.length - 2);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] exifOrientationSegment(int orientation) {
        return new byte[] {
            (byte) 0xFF, (byte) 0xE1,
            0x00, 0x22,
            'E', 'x', 'i', 'f', 0x00, 0x00,
            'M', 'M',
            0x00, 0x2A,
            0x00, 0x00, 0x00, 0x08,
            0x00, 0x01,
            0x01, 0x12,
            0x00, 0x03,
            0x00, 0x00, 0x00, 0x01,
            0x00, (byte) orientation,
            0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        };
    }

    private static ProblemAssetPreprocessingProperties properties() {
        return new ProblemAssetPreprocessingProperties(
            "DOCUMENT_PREPROCESSOR",
            "1.0",
            "capture-quality-v1",
            20L * 1024L * 1024L,
            36_000_000L,
            2400,
            360,
            0.92F,
            32,
            90.0D,
            0.30D,
            0.60D,
            28.0D,
            900
        );
    }
}
