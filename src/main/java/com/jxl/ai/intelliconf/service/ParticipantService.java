package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.dto.req.ParticipantSaveReqDTO;
import com.jxl.ai.intelliconf.dto.req.ParticipantUpdateReqDTO;
import com.jxl.ai.intelliconf.dto.resp.ParticipantImportRespDTO;
import com.jxl.ai.intelliconf.dto.resp.ParticipantPageRespDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 参会者服务接口
 */
public interface ParticipantService {

    /**
     * 批量导入参会者（Excel）
     *
     * @param file   Excel 文件
     * @param confId 会议ID
     * @return 导入结果统计
     */
    ParticipantImportRespDTO importParticipants(MultipartFile file, Long confId);

    /**
     * 分页查询会议参会者列表
     *
     * @param confId   会议ID
     * @param keyword  搜索关键词（姓名、邮箱、机构）
     * @param current  当前页码
     * @param size     每页大小
     * @return 分页结果
     */
    ParticipantPageRespDTO getParticipantList(Long confId, String keyword, Long current, Long size);

    /**
     * 添加单个参会者
     *
     * @param reqDTO 参会者信息
     */
    void addParticipant(ParticipantSaveReqDTO reqDTO);

    /**
     * 修改参会者信息
     *
     * @param reqDTO 参会者信息
     */
    void updateParticipant(ParticipantUpdateReqDTO reqDTO);

    /**
     * 删除参会者
     *
     * @param memberId conf_member 表的 ID
     */
    void removeParticipant(Long memberId);
}
