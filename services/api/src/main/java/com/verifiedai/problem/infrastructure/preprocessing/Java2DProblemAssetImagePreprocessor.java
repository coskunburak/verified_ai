package com.verifiedai.problem.infrastructure.preprocessing;

import com.verifiedai.problem.domain.model.ProblemAssetDerivativeKind;
import com.verifiedai.problem.domain.model.ProblemAssetPreprocessingSignalType;
import com.verifiedai.problem.domain.model.ProblemAssetQualityOutcome;
import com.verifiedai.problem.domain.model.ProblemAssetQualitySeverity;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

@Component
public class Java2DProblemAssetImagePreprocessor {
    private static final int HISTOGRAM_BUCKETS = 256;
    private static final double CROP_AREA_WARNING_THRESHOLD = 0.35D;

    private final ProblemAssetPreprocessingProperties properties;

    Java2DProblemAssetImagePreprocessor(ProblemAssetPreprocessingProperties properties) {
        this.properties = properties;
    }

    public ProblemAssetImagePreprocessingResult process(
        byte[] sourceBytes,
        double cropX,
        double cropY,
        double cropWidth,
        double cropHeight
    ) {
        BufferedImage decoded = decode(sourceBytes);
        int orientation = JpegExifOrientation.read(sourceBytes);
        BufferedImage oriented = applyOrientation(decoded, orientation);
        long pixels = (long) oriented.getWidth() * oriented.getHeight();
        if (pixels > properties.maxDecodePixels()) {
            throw failure(ApiErrorCode.ASSET_DIMENSIONS_UNSUPPORTED, "IMAGE_DIMENSIONS_UNSUPPORTED", "Decoded image exceeds preprocessing pixel limit");
        }

        CropPixels crop = cropPixels(oriented.getWidth(), oriented.getHeight(), cropX, cropY, cropWidth, cropHeight);
        BufferedImage cropped = copyRgb(oriented.getSubimage(crop.x(), crop.y(), crop.width(), crop.height()));
        QualityMeasurements measurements = measure(cropped, crop, cropX, cropY, cropWidth, cropHeight);
        List<ProblemAssetImagePreprocessingResult.QualitySignal> signals = qualitySignals(measurements);
        boolean warning = signals.stream().anyMatch(signal -> signal.severity() == ProblemAssetQualitySeverity.WARNING);
        ProblemAssetQualityOutcome outcome = warning ? ProblemAssetQualityOutcome.WARNING : ProblemAssetQualityOutcome.PASS;

        ContrastResult contrast = normalizeContrastIfNeeded(cropped, measurements);
        BufferedImage ocrImage = resizeToLongEdge(contrast.image(), properties.ocrOptimizedMaxLongEdge());
        BufferedImage thumbnail = resizeToLongEdge(contrast.image(), properties.thumbnailMaxLongEdge());

        List<ProblemAssetImagePreprocessingResult.DerivativeImage> derivatives = List.of(
            new ProblemAssetImagePreprocessingResult.DerivativeImage(
                ProblemAssetDerivativeKind.OCR_OPTIMIZED,
                encodeJpeg(ocrImage),
                ocrImage.getWidth(),
                ocrImage.getHeight(),
                ocrImage.getWidth() != contrast.image().getWidth() || ocrImage.getHeight() != contrast.image().getHeight()
            ),
            new ProblemAssetImagePreprocessingResult.DerivativeImage(
                ProblemAssetDerivativeKind.THUMBNAIL,
                encodeJpeg(thumbnail),
                thumbnail.getWidth(),
                thumbnail.getHeight(),
                thumbnail.getWidth() != contrast.image().getWidth() || thumbnail.getHeight() != contrast.image().getHeight()
            )
        );

        return new ProblemAssetImagePreprocessingResult(
            oriented.getWidth(),
            oriented.getHeight(),
            orientation != 1,
            false,
            contrast.normalized(),
            outcome,
            derivatives,
            signals
        );
    }

    private BufferedImage decode(byte[] sourceBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (image == null) {
                throw failure(ApiErrorCode.ASSET_FORMAT_INVALID, "IMAGE_DECODE_FAILED", "Uploaded image could not be decoded");
            }
            if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw failure(ApiErrorCode.ASSET_DIMENSIONS_UNSUPPORTED, "IMAGE_DIMENSIONS_UNSUPPORTED", "Decoded image dimensions are invalid");
            }
            return image;
        } catch (ProblemAssetImagePreprocessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(ApiErrorCode.ASSET_FORMAT_INVALID, "IMAGE_DECODE_FAILED", "Uploaded image could not be decoded");
        }
    }

    private CropPixels cropPixels(int width, int height, double cropX, double cropY, double cropWidth, double cropHeight) {
        if (cropX < 0 || cropY < 0 || cropWidth <= 0 || cropHeight <= 0 || cropX + cropWidth > 1 || cropY + cropHeight > 1) {
            throw failure(ApiErrorCode.ASSET_CROP_INVALID, "CROP_INVALID", "Crop metadata is outside the image bounds");
        }
        int x = Math.max(0, Math.min(width - 1, (int) Math.floor(cropX * width)));
        int y = Math.max(0, Math.min(height - 1, (int) Math.floor(cropY * height)));
        int cropPixelWidth = Math.max(1, (int) Math.round(cropWidth * width));
        int cropPixelHeight = Math.max(1, (int) Math.round(cropHeight * height));
        if (x + cropPixelWidth > width) {
            cropPixelWidth = width - x;
        }
        if (y + cropPixelHeight > height) {
            cropPixelHeight = height - y;
        }
        if (cropPixelWidth < properties.minCropPixels() || cropPixelHeight < properties.minCropPixels()) {
            throw failure(ApiErrorCode.ASSET_CROP_INVALID, "CROP_TOO_SMALL", "Crop metadata leaves too few pixels for preprocessing");
        }
        return new CropPixels(x, y, cropPixelWidth, cropPixelHeight);
    }

    private QualityMeasurements measure(
        BufferedImage image,
        CropPixels crop,
        double cropX,
        double cropY,
        double cropWidth,
        double cropHeight
    ) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] histogram = new int[HISTOGRAM_BUCKETS];
        double sum = 0;
        double sumSquares = 0;
        int saturated = 0;
        int total = width * height;
        int gridSize = 8;
        int[] gridTotals = new int[gridSize * gridSize];
        int[] gridSaturated = new int[gridSize * gridSize];
        double[] gray = new double[total];

        for (int y = 0; y < height; y += 1) {
            for (int x = 0; x < width; x += 1) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                int luma = luminance(red, green, blue);
                gray[y * width + x] = luma;
                histogram[luma] += 1;
                sum += luma;
                sumSquares += (double) luma * luma;
                int cellX = Math.min(gridSize - 1, x * gridSize / Math.max(1, width));
                int cellY = Math.min(gridSize - 1, y * gridSize / Math.max(1, height));
                int cell = cellY * gridSize + cellX;
                gridTotals[cell] += 1;
                if (red > 240 && green > 240 && blue > 240 && luma > 245 && Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) < 22) {
                    saturated += 1;
                    gridSaturated[cell] += 1;
                }
            }
        }

        double mean = sum / total;
        double variance = Math.max(0, (sumSquares / total) - (mean * mean));
        double stdDev = Math.sqrt(variance);
        double maxCellSaturationRatio = 0;
        for (int index = 0; index < gridTotals.length; index += 1) {
            if (gridTotals[index] > 0) {
                maxCellSaturationRatio = Math.max(maxCellSaturationRatio, (double) gridSaturated[index] / gridTotals[index]);
            }
        }

        return new QualityMeasurements(
            crop,
            cropWidth * cropHeight,
            cropX,
            cropY,
            cropWidth,
            cropHeight,
            stdDev,
            percentile(histogram, total, 0.02D),
            percentile(histogram, total, 0.98D),
            blurVariance(gray, width, height),
            maxCellSaturationRatio,
            (double) saturated / total,
            Math.max(width, height)
        );
    }

    private List<ProblemAssetImagePreprocessingResult.QualitySignal> qualitySignals(QualityMeasurements measurements) {
        List<ProblemAssetImagePreprocessingResult.QualitySignal> signals = new ArrayList<>();
        signals.add(new ProblemAssetImagePreprocessingResult.QualitySignal(
            ProblemAssetPreprocessingSignalType.BLUR,
            measurements.blurVariance() < properties.blurVarianceWarning() ? ProblemAssetQualitySeverity.WARNING : ProblemAssetQualitySeverity.PASS,
            measurements.blurVariance(),
            properties.blurVarianceWarning(),
            measurements.blurVariance() < properties.blurVarianceWarning() ? "CAPTURE_BLUR_WARNING" : "CAPTURE_BLUR_PASS"
        ));
        boolean glareWarning = measurements.maxCellSaturationRatio() >= properties.glareCellSaturationRatio()
            && measurements.overallSaturationRatio() <= properties.glareOverallSaturationMax();
        signals.add(new ProblemAssetImagePreprocessingResult.QualitySignal(
            ProblemAssetPreprocessingSignalType.GLARE,
            glareWarning ? ProblemAssetQualitySeverity.WARNING : ProblemAssetQualitySeverity.PASS,
            measurements.maxCellSaturationRatio(),
            properties.glareCellSaturationRatio(),
            glareWarning ? "CAPTURE_GLARE_WARNING" : "CAPTURE_GLARE_PASS"
        ));
        boolean cropWarning = measurements.cropAreaRatio() < CROP_AREA_WARNING_THRESHOLD;
        signals.add(new ProblemAssetImagePreprocessingResult.QualitySignal(
            ProblemAssetPreprocessingSignalType.CROP_FRAMING,
            cropWarning ? ProblemAssetQualitySeverity.WARNING : ProblemAssetQualitySeverity.PASS,
            measurements.cropAreaRatio(),
            CROP_AREA_WARNING_THRESHOLD,
            cropWarning ? "CAPTURE_CROP_FRAMING_WARNING" : "CAPTURE_CROP_FRAMING_PASS"
        ));
        signals.add(new ProblemAssetImagePreprocessingResult.QualitySignal(
            ProblemAssetPreprocessingSignalType.CONTRAST_READABILITY,
            measurements.stdDev() < properties.contrastStdDevWarning() ? ProblemAssetQualitySeverity.WARNING : ProblemAssetQualitySeverity.PASS,
            measurements.stdDev(),
            properties.contrastStdDevWarning(),
            measurements.stdDev() < properties.contrastStdDevWarning() ? "CAPTURE_CONTRAST_WARNING" : "CAPTURE_CONTRAST_PASS"
        ));
        signals.add(new ProblemAssetImagePreprocessingResult.QualitySignal(
            ProblemAssetPreprocessingSignalType.RESOLUTION,
            measurements.longEdge() < properties.resolutionMinLongEdge() ? ProblemAssetQualitySeverity.WARNING : ProblemAssetQualitySeverity.PASS,
            measurements.longEdge(),
            properties.resolutionMinLongEdge(),
            measurements.longEdge() < properties.resolutionMinLongEdge() ? "CAPTURE_RESOLUTION_WARNING" : "CAPTURE_RESOLUTION_PASS"
        ));
        signals.sort(Comparator.comparing(signal -> signal.signalType().name()));
        return signals;
    }

    private ContrastResult normalizeContrastIfNeeded(BufferedImage image, QualityMeasurements measurements) {
        boolean lowContrast = measurements.stdDev() < properties.contrastStdDevWarning();
        boolean narrowReadableRange = measurements.p98() - measurements.p02() < 150;
        if (!lowContrast && !narrowReadableRange) {
            return new ContrastResult(image, false);
        }
        int low = Math.max(0, Math.min(245, measurements.p02()));
        int high = Math.max(low + 10, Math.min(255, measurements.p98()));
        double scale = 255D / (high - low);
        BufferedImage normalized = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y += 1) {
            for (int x = 0; x < image.getWidth(); x += 1) {
                int rgb = image.getRGB(x, y);
                int red = stretch((rgb >> 16) & 0xFF, low, scale);
                int green = stretch((rgb >> 8) & 0xFF, low, scale);
                int blue = stretch(rgb & 0xFF, low, scale);
                normalized.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        return new ContrastResult(normalized, true);
    }

    private BufferedImage resizeToLongEdge(BufferedImage image, int maxLongEdge) {
        int longEdge = Math.max(image.getWidth(), image.getHeight());
        if (longEdge <= maxLongEdge) {
            return image;
        }
        double scale = (double) maxLongEdge / longEdge;
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw failure(ApiErrorCode.ASSET_DERIVATIVE_GENERATION_FAILED, "JPEG_ENCODER_UNAVAILABLE", "JPEG encoder is unavailable");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(properties.jpegQuality());
            writer.setOutput(output);
            writer.write(null, new IIOImage(copyRgb(image), null, null), parameters);
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw failure(ApiErrorCode.ASSET_DERIVATIVE_GENERATION_FAILED, "JPEG_ENCODING_FAILED", "Derived JPEG could not be encoded");
        } finally {
            writer.dispose();
        }
    }

    private static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return copyRgb(image);
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int targetWidth = orientation >= 5 && orientation <= 8 ? height : width;
        int targetHeight = orientation >= 5 && orientation <= 8 ? width : height;
        AffineTransform transform = new AffineTransform();
        switch (orientation) {
            case 2 -> {
                transform.scale(-1, 1);
                transform.translate(-width, 0);
            }
            case 3 -> {
                transform.translate(width, height);
                transform.rotate(Math.PI);
            }
            case 4 -> {
                transform.scale(1, -1);
                transform.translate(0, -height);
            }
            case 5 -> {
                transform.rotate(Math.PI / 2);
                transform.scale(1, -1);
            }
            case 6 -> {
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
            }
            case 7 -> {
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
                transform.scale(-1, 1);
            }
            case 8 -> {
                transform.translate(0, width);
                transform.rotate(-Math.PI / 2);
            }
            default -> {
            }
        }
        BufferedImage normalized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        new AffineTransformOp(transform, AffineTransformOp.TYPE_BICUBIC).filter(copyRgb(image), normalized);
        return normalized;
    }

    private static BufferedImage copyRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static double blurVariance(double[] gray, int width, int height) {
        if (width < 3 || height < 3) {
            return 0;
        }
        int step = Math.max(1, Math.max(width, height) / 600);
        double sum = 0;
        double sumSquares = 0;
        long count = 0;
        for (int y = 1; y < height - 1; y += step) {
            for (int x = 1; x < width - 1; x += step) {
                double center = gray[y * width + x] * -4;
                double laplacian = center
                    + gray[y * width + (x - 1)]
                    + gray[y * width + (x + 1)]
                    + gray[(y - 1) * width + x]
                    + gray[(y + 1) * width + x];
                sum += laplacian;
                sumSquares += laplacian * laplacian;
                count += 1;
            }
        }
        double mean = sum / Math.max(1, count);
        return Math.max(0, (sumSquares / Math.max(1, count)) - (mean * mean));
    }

    private static int percentile(int[] histogram, int total, double percentile) {
        int target = Math.max(1, (int) Math.round(total * percentile));
        int cumulative = 0;
        for (int index = 0; index < histogram.length; index += 1) {
            cumulative += histogram[index];
            if (cumulative >= target) {
                return index;
            }
        }
        return histogram.length - 1;
    }

    private static int stretch(int value, int low, double scale) {
        return Math.max(0, Math.min(255, (int) Math.round((value - low) * scale)));
    }

    private static int luminance(int red, int green, int blue) {
        return (int) Math.round((0.2126D * red) + (0.7152D * green) + (0.0722D * blue));
    }

    private static ProblemAssetImagePreprocessingException failure(ApiErrorCode apiErrorCode, String failureCode, String message) {
        return new ProblemAssetImagePreprocessingException(apiErrorCode, failureCode, message);
    }

    private record CropPixels(int x, int y, int width, int height) {
    }

    @SuppressWarnings("unused")
    private record QualityMeasurements(
        CropPixels cropPixels,
        double cropAreaRatio,
        double cropX,
        double cropY,
        double cropWidth,
        double cropHeight,
        double stdDev,
        int p02,
        int p98,
        double blurVariance,
        double maxCellSaturationRatio,
        double overallSaturationRatio,
        int longEdge
    ) {
    }

    private record ContrastResult(BufferedImage image, boolean normalized) {
    }

    private static final class JpegExifOrientation {
        private static final int ORIENTATION_TAG = 0x0112;

        private JpegExifOrientation() {
        }

        static int read(byte[] bytes) {
            if (bytes.length < 4 || unsignedShort(bytes, 0, false) != 0xFFD8) {
                return 1;
            }
            int offset = 2;
            while (offset + 4 < bytes.length) {
                if ((bytes[offset] & 0xFF) != 0xFF) {
                    return 1;
                }
                int marker = bytes[offset + 1] & 0xFF;
                offset += 2;
                if (marker == 0xDA || marker == 0xD9) {
                    return 1;
                }
                if (offset + 2 > bytes.length) {
                    return 1;
                }
                int length = unsignedShort(bytes, offset, false);
                if (length < 2 || offset + length > bytes.length) {
                    return 1;
                }
                if (marker == 0xE1 && length >= 14 && hasExifHeader(bytes, offset + 2)) {
                    return readTiffOrientation(bytes, offset + 8, length - 8);
                }
                offset += length;
            }
            return 1;
        }

        private static int readTiffOrientation(byte[] bytes, int tiffStart, int tiffLength) {
            if (tiffStart + 8 > bytes.length) {
                return 1;
            }
            boolean littleEndian;
            int byteOrder = unsignedShort(bytes, tiffStart, false);
            if (byteOrder == 0x4949) {
                littleEndian = true;
            } else if (byteOrder == 0x4D4D) {
                littleEndian = false;
            } else {
                return 1;
            }
            if (unsignedShort(bytes, tiffStart + 2, littleEndian) != 42) {
                return 1;
            }
            long ifdOffset = unsignedInt(bytes, tiffStart + 4, littleEndian);
            int ifdStart = tiffStart + (int) ifdOffset;
            if (ifdOffset < 8 || ifdStart + 2 > tiffStart + tiffLength || ifdStart + 2 > bytes.length) {
                return 1;
            }
            int entries = unsignedShort(bytes, ifdStart, littleEndian);
            int entryStart = ifdStart + 2;
            for (int index = 0; index < entries; index += 1) {
                int entryOffset = entryStart + (index * 12);
                if (entryOffset + 12 > bytes.length || entryOffset + 12 > tiffStart + tiffLength) {
                    return 1;
                }
                int tag = unsignedShort(bytes, entryOffset, littleEndian);
                int type = unsignedShort(bytes, entryOffset + 2, littleEndian);
                long count = unsignedInt(bytes, entryOffset + 4, littleEndian);
                if (tag == ORIENTATION_TAG && type == 3 && count == 1) {
                    int value = unsignedShort(bytes, entryOffset + 8, littleEndian);
                    return value >= 1 && value <= 8 ? value : 1;
                }
            }
            return 1;
        }

        private static boolean hasExifHeader(byte[] bytes, int offset) {
            return offset + 6 <= bytes.length
                && bytes[offset] == 'E'
                && bytes[offset + 1] == 'x'
                && bytes[offset + 2] == 'i'
                && bytes[offset + 3] == 'f'
                && bytes[offset + 4] == 0
                && bytes[offset + 5] == 0;
        }

        private static int unsignedShort(byte[] bytes, int offset, boolean littleEndian) {
            if (littleEndian) {
                return ((bytes[offset + 1] & 0xFF) << 8) | (bytes[offset] & 0xFF);
            }
            return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
        }

        private static long unsignedInt(byte[] bytes, int offset, boolean littleEndian) {
            if (littleEndian) {
                return ((long) (bytes[offset + 3] & 0xFF) << 24)
                    | ((long) (bytes[offset + 2] & 0xFF) << 16)
                    | ((long) (bytes[offset + 1] & 0xFF) << 8)
                    | (bytes[offset] & 0xFF);
            }
            return ((long) (bytes[offset] & 0xFF) << 24)
                | ((long) (bytes[offset + 1] & 0xFF) << 16)
                | ((long) (bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
        }
    }
}
