package com.jxl.ai.intelliconf.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 参会者分页响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantPageRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 参会者列表
     */
    private List<ParticipantRespDTO> records;

    /**
     * 当前页码
     */
    private Long current;

    /**
     * 每页大小
     */
    private Long size;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 总页数
     */
    private Long pages;
}
