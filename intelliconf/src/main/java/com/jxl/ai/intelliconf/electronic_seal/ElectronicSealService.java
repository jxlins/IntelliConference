package com.jxl.ai.intelliconf.electronic_seal;

import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import com.jxl.ai.intelliconf.common.convention.exception.ServiceException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.blend.BlendMode;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Service
public class ElectronicSealService {

    static final long MAX_PDF_BYTES = 25L * 1024 * 1024;
    static final long MAX_SEAL_IMAGE_BYTES = 4L * 1024 * 1024;
    static final int MAX_PAGES = 500;
    private static final int MAX_IMAGE_EDGE = 2048;
    private static final Color SEAL_RED = new Color(210, 22, 35);
    private static final Set<String> POSITIONS = Set.of(
            "bottom-right", "bottom-left", "top-right", "top-left", "center", "manual"
    );

    public byte[] generateSeal(String name, String centerText) {
        validateText(name, "请输入印章环形名称", 40);
        validateText(defaultCenterText(centerText), "请输入印章中心文字", 12);
        try {
            return pngBytes(createSeal(name.trim(), defaultCenterText(centerText)));
        } catch (IOException ex) {
            throw new ServiceException("生成印章图片失败");
        }
    }

    public byte[] stampPdf(byte[] pdfBytes, byte[] customSealImage, ElectronicSealOptions options) {
        validatePdf(pdfBytes);
        ElectronicSealOptions validated = validateOptions(options, customSealImage != null);
        byte[] sealBytes = customSealImage == null
                ? generateSeal(validated.name(), validated.centerText())
                : normalizeSealImage(customSealImage);

        try (PDDocument document = Loader.loadPDF(pdfBytes);
             ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(pdfBytes.length, 8192))) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new ClientException("输入 PDF 没有页面");
            }
            if (pageCount > MAX_PAGES) {
                throw new ClientException("PDF 页数不能超过 " + MAX_PAGES + " 页");
            }

            int[] range = resolvePageRange(validated.page(), pageCount);
            PDImageXObject seal = PDImageXObject.createFromByteArray(document, sealBytes, "electronic-seal");
            for (int pageIndex = range[0]; pageIndex <= range[1]; pageIndex++) {
                PDPage page = document.getPage(pageIndex);
                float[] point = resolvePosition(page.getCropBox(), validated);
                try (PDPageContentStream stream = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    PDExtendedGraphicsState state = new PDExtendedGraphicsState();
                    state.setNonStrokingAlphaConstant(validated.opacity());
                    state.setBlendMode(BlendMode.MULTIPLY);
                    stream.saveGraphicsState();
                    stream.setGraphicsStateParameters(state);
                    stream.drawImage(seal, point[0], point[1], validated.size(), validated.size());
                    stream.restoreGraphicsState();
                }
            }
            document.getDocumentInformation().setCustomMetadataValue("ElectronicSealType", "Visual stamp");
            document.getDocumentInformation().setCustomMetadataValue("ElectronicSealPages", validated.page());
            document.save(output);
            return output.toByteArray();
        } catch (ClientException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ClientException("PDF 处理失败，请确认文件未损坏且未加密");
        }
    }

    private ElectronicSealOptions validateOptions(ElectronicSealOptions options, boolean customImageProvided) {
        if (options == null) {
            throw new ClientException("缺少盖章参数");
        }
        String name = trimToNull(options.name());
        String centerText = defaultCenterText(options.centerText());
        if (!customImageProvided) {
            validateText(name, "未上传自定义印章时，请填写印章环形名称", 40);
            validateText(centerText, "请输入印章中心文字", 12);
        }

        String page = StringUtils.hasText(options.page()) ? options.page().trim().toLowerCase(Locale.ROOT) : "last";
        if (!"last".equals(page) && !"all".equals(page)) {
            int pageNumber = parsePositiveInt(page, "指定页码必须是正整数");
            page = String.valueOf(pageNumber);
        }

        String position = StringUtils.hasText(options.position())
                ? options.position().trim().toLowerCase(Locale.ROOT)
                : "bottom-right";
        if (!POSITIONS.contains(position)) {
            throw new ClientException("不支持的印章位置");
        }
        if (options.size() < 36 || options.size() > 600 || !Float.isFinite(options.size())) {
            throw new ClientException("印章大小必须在 36 到 600 点之间");
        }
        if (options.opacity() <= 0 || options.opacity() > 1 || !Float.isFinite(options.opacity())) {
            throw new ClientException("印章透明度必须大于 0 且不超过 1");
        }
        if ("manual".equals(position)) {
            if (options.x() == null || options.y() == null
                    || !Float.isFinite(options.x()) || !Float.isFinite(options.y())) {
                throw new ClientException("手动位置必须同时填写 X 和 Y 坐标");
            }
        }
        return new ElectronicSealOptions(name, centerText, page, position,
                options.x(), options.y(), options.size(), options.opacity());
    }

    private void validatePdf(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new ClientException("请选择 PDF 文件");
        }
        if (pdfBytes.length > MAX_PDF_BYTES) {
            throw new ClientException("PDF 文件不能超过 25 MB");
        }
        if (pdfBytes.length < 5 || pdfBytes[0] != '%' || pdfBytes[1] != 'P'
                || pdfBytes[2] != 'D' || pdfBytes[3] != 'F' || pdfBytes[4] != '-') {
            throw new ClientException("上传文件不是有效的 PDF");
        }
    }

    private byte[] normalizeSealImage(byte[] imageBytes) {
        if (imageBytes.length == 0 || imageBytes.length > MAX_SEAL_IMAGE_BYTES) {
            throw new ClientException("自定义印章图片不能超过 4 MB");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (input == null) {
                throw new ClientException("自定义印章图片格式不正确");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ClientException("自定义印章图片格式不正确");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_IMAGE_EDGE || height > MAX_IMAGE_EDGE) {
                    throw new ClientException("自定义印章图片宽高不能超过 2048 像素");
                }
                BufferedImage image = reader.read(0);
                return pngBytes(image);
            } finally {
                reader.dispose();
            }
        } catch (ClientException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ClientException("读取自定义印章图片失败");
        }
    }

    private int[] resolvePageRange(String page, int pageCount) {
        if ("all".equals(page)) {
            return new int[]{0, pageCount - 1};
        }
        if ("last".equals(page)) {
            return new int[]{pageCount - 1, pageCount - 1};
        }
        int pageNumber = parsePositiveInt(page, "指定页码必须是正整数");
        if (pageNumber > pageCount) {
            throw new ClientException("页码超出范围，PDF 共 " + pageCount + " 页");
        }
        return new int[]{pageNumber - 1, pageNumber - 1};
    }

    private float[] resolvePosition(PDRectangle box, ElectronicSealOptions options) {
        float margin = 36f;
        float x;
        float y;
        switch (options.position()) {
            case "top-left" -> {
                x = margin;
                y = box.getHeight() - options.size() - margin;
            }
            case "top-right" -> {
                x = box.getWidth() - options.size() - margin;
                y = box.getHeight() - options.size() - margin;
            }
            case "bottom-left" -> {
                x = margin;
                y = margin;
            }
            case "center" -> {
                x = (box.getWidth() - options.size()) / 2f;
                y = (box.getHeight() - options.size()) / 2f;
            }
            case "manual" -> {
                x = options.x();
                y = options.y();
            }
            default -> {
                x = box.getWidth() - options.size() - margin;
                y = margin;
            }
        }

        x += box.getLowerLeftX();
        y += box.getLowerLeftY();
        if (x < box.getLowerLeftX() || y < box.getLowerLeftY()
                || x + options.size() > box.getUpperRightX()
                || y + options.size() > box.getUpperRightY()) {
            throw new ClientException("印章超出页面范围，请调整位置、坐标或大小");
        }
        return new float[]{x, y};
    }

    private BufferedImage createSeal(String name, String centerText) {
        int canvas = 800;
        BufferedImage image = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(new Color(255, 255, 255, 0));
            graphics.fillRect(0, 0, canvas, canvas);
            graphics.setColor(SEAL_RED);
            graphics.setStroke(new BasicStroke(22f));
            graphics.drawOval(45, 45, 710, 710);
            graphics.setStroke(new BasicStroke(5f));
            graphics.drawOval(70, 70, 660, 660);
            drawArcText(graphics, name, chooseFont(Font.BOLD, name.codePointCount(0, name.length()) > 16 ? 48 : 60),
                    400d, 405d, 275d);
            drawStar(graphics, 400d, 390d, 112d, 47d);
            Font centerFont = chooseFont(Font.BOLD, centerText.codePointCount(0, centerText.length()) > 7 ? 48 : 58);
            graphics.setFont(centerFont);
            FontMetrics metrics = graphics.getFontMetrics();
            graphics.drawString(centerText, 400 - metrics.stringWidth(centerText) / 2, 625);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void drawArcText(Graphics2D graphics, String value, Font font,
                             double centerX, double centerY, double radius) {
        int[] characters = value.trim().codePoints().toArray();
        double span = Math.min(205d, Math.max(90d, (characters.length - 1) * 12.8d));
        double start = 270d - span / 2d;
        double step = characters.length == 1 ? 0d : span / (characters.length - 1);
        graphics.setFont(font);
        for (int index = 0; index < characters.length; index++) {
            String character = new String(Character.toChars(characters[index]));
            double angle = Math.toRadians(start + step * index);
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            AffineTransform previous = graphics.getTransform();
            graphics.translate(x, y);
            graphics.rotate(angle + Math.PI / 2d);
            FontMetrics metrics = graphics.getFontMetrics();
            graphics.drawString(character, -metrics.stringWidth(character) / 2f, metrics.getAscent() / 2.8f);
            graphics.setTransform(previous);
        }
    }

    private void drawStar(Graphics2D graphics, double centerX, double centerY, double outer, double inner) {
        Path2D.Double path = new Path2D.Double();
        for (int index = 0; index < 10; index++) {
            double angle = Math.toRadians(-90 + index * 36);
            double radius = index % 2 == 0 ? outer : inner;
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            if (index == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        graphics.fill(path);
    }

    private Font chooseFont(int style, int size) {
        Set<String> availableFonts = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
        ));
        for (String candidate : Arrays.asList("Microsoft YaHei", "SimSun", "Noto Sans CJK SC", "Dialog")) {
            if (availableFonts.contains(candidate)) {
                return new Font(candidate, style, size);
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    private byte[] pngBytes(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", output)) {
                throw new IOException("PNG writer unavailable");
            }
            return output.toByteArray();
        }
    }

    private String defaultCenterText(String centerText) {
        return StringUtils.hasText(centerText) ? centerText.trim() : "电子专用章";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateText(String value, String message, int maxCodePoints) {
        if (!StringUtils.hasText(value)) {
            throw new ClientException(message);
        }
        String text = value.trim();
        if (text.codePointCount(0, text.length()) > maxCodePoints) {
            throw new ClientException("文字长度不能超过 " + maxCodePoints + " 个字符");
        }
    }

    private int parsePositiveInt(String value, String message) {
        try {
            int number = Integer.parseInt(value);
            if (number < 1) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException ex) {
            throw new ClientException(message);
        }
    }
}
