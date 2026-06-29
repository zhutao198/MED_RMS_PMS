package com.zhutao.medrms.admin.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrgNode {
    private Long id;
    private String label;
    private Integer userCount;
    private List<OrgNode> children;
}
