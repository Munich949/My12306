package com.dlnu.index12306.framework.starter.database.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.util.Date;

/**
 * 数据持久层基础属性
 */
@Data
public class BaseDO {

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)    // 在插入操作时进行填充
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE) // 在插入和更新操作时进行填充
    private Date updateTime;

    /**
     * 修改标志
     */
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
