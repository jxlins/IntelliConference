package com.jxl.ai.intelliconf.electronic_seal;

import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElectronicSealServiceTest {

    private final ElectronicSealService service = new ElectronicSealService();

    @Test
    void generatesTransparentPngSeal() {
        byte[] image = service.generateSeal("智能教育国际会议组委会", "电子专用章");

        assertTrue(image.length > 1000);
        assertEquals((byte) 0x89, image[0]);
        assertEquals((byte) 0x50, image[1]);
    }

    @Test
    void stampsSelectedPageAndPreservesDocument() throws Exception {
        byte[] source = blankPdf(2);
        ElectronicSealOptions options = new ElectronicSealOptions(
                "智能教育国际会议组委会", "电子专用章", "2", "bottom-right", null, null, 120f, 0.82f
        );

        byte[] stamped = service.stampPdf(source, null, options);

        assertTrue(stamped.length > source.length);
        try (PDDocument document = Loader.loadPDF(stamped)) {
            assertEquals(2, document.getNumberOfPages());
            assertEquals("Visual stamp", document.getDocumentInformation().getCustomMetadataValue("ElectronicSealType"));
            assertEquals("2", document.getDocumentInformation().getCustomMetadataValue("ElectronicSealPages"));
            assertNotNull(document.getPage(1).getResources());
        }
    }

    @Test
    void rejectsOutOfRangePage() throws Exception {
        ElectronicSealOptions options = new ElectronicSealOptions(
                "测试会议组委会", "电子专用章", "3", "bottom-right", null, null, 120f, 0.82f
        );

        ClientException exception = assertThrows(ClientException.class,
                () -> service.stampPdf(blankPdf(1), null, options));
        assertTrue(exception.getMessage().contains("页码超出范围"));
    }

    @Test
    void requiresManualCoordinatesTogether() throws Exception {
        ElectronicSealOptions options = new ElectronicSealOptions(
                "测试会议组委会", "电子专用章", "last", "manual", 50f, null, 120f, 0.82f
        );

        assertThrows(ClientException.class, () -> service.stampPdf(blankPdf(1), null, options));
    }

    private byte[] blankPdf(int pages) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pages; index++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
