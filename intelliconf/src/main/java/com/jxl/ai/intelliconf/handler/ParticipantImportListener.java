package com.jxl.ai.intelliconf.handler;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.jxl.ai.intelliconf.dto.req.ParticipantImportExcelDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
public class ParticipantImportListener extends AnalysisEventListener<ParticipantImportExcelDTO> {

    private static final int BATCH_SIZE = 500;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    @Getter
    private final List<ParticipantImportExcelDTO> cachedDataList = new ArrayList<>(BATCH_SIZE);

    @Getter
    private final List<ParticipantImportExcelDTO> allDataList = new ArrayList<>();

    @Getter
    private int skippedCount = 0;

    @Override
    public void invoke(ParticipantImportExcelDTO data, AnalysisContext context) {
        if (!isValidData(data)) {
            skippedCount++;
            log.warn("Skip invalid participant row: rowIndex={}, data={}",
                    context.readRowHolder().getRowIndex() + 1, data);
            return;
        }

        cachedDataList.add(data);
        allDataList.add(data);
        if (cachedDataList.size() >= BATCH_SIZE) {
            cachedDataList.clear();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("Participant Excel analysed: valid={}, skipped={}", allDataList.size(), skippedCount);
    }

    private boolean isValidData(ParticipantImportExcelDTO data) {
        if (data == null) {
            return false;
        }

        String name = data.getName() == null ? "" : data.getName().trim();
        String email = data.getEmail() == null ? "" : data.getEmail().trim();

        // Supports both formats:
        // 1. name/email/institution columns
        // 2. one email address per row
        if (email.isEmpty() && EMAIL_PATTERN.matcher(name).matches()) {
            email = name;
            name = email.substring(0, email.indexOf('@'));
        }

        if (name.isEmpty() || email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
            return false;
        }

        data.setName(name);
        data.setEmail(email.toLowerCase(Locale.ROOT));
        if (data.getInstitution() != null) {
            data.setInstitution(data.getInstitution().trim());
        }
        return true;
    }
}
