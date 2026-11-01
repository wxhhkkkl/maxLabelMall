package cn.iocoder.yudao.module.trade.service.customization;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationMaterialDO;
import cn.iocoder.yudao.module.trade.enums.customization.CustomizationModeEnum;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationFileBO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationSpecBO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_FILE_INVALID;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_SPEC_OUT_OF_RANGE;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationConstants.FILE_ALLOWED_EXTENSIONS;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationConstants.FILE_MAX_COUNT;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationConstants.FILE_MAX_SIZE;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationConstants.FILE_MIN_COUNT;

/**
 * 定制规格与设计文件校验（014 / FR-358~FR-367）
 */
@Component
public class CustomizationSpecValidator {

    @Value("${yudao.customization.file-domain:https://img.yuwangchenfa.com}")
    private String fileDomain;

    public void validate(CustomizationSpecBO spec, CustomizationMaterialDO material) {
        if (spec == null || spec.getWidth() == null || spec.getHeight() == null
                || spec.getQuantity() == null) {
            throw exception(CUSTOMIZATION_SPEC_OUT_OF_RANGE);
        }
        if (CustomizationModeEnum.nameOf(spec.getMode()).isEmpty()) {
            throw exception(CUSTOMIZATION_SPEC_OUT_OF_RANGE);
        }
        // 建单时成品形态 / 行列必填（报价阶段允许先不填）
        if (spec.getForm() != null || spec.getRowCount() != null || spec.getColumnCount() != null) {
            if (spec.getForm() == null || (spec.getForm() != 1 && spec.getForm() != 2)
                    || spec.getRowCount() == null || spec.getRowCount() < 1
                    || spec.getColumnCount() == null || spec.getColumnCount() < 1) {
                throw exception(CUSTOMIZATION_SPEC_OUT_OF_RANGE);
            }
        }
        if (spec.getWidth() < material.getMinWidth() || spec.getWidth() > material.getMaxWidth()
                || spec.getHeight() < material.getMinHeight() || spec.getHeight() > material.getMaxHeight()
                || spec.getQuantity() < material.getMinQuantity() || spec.getQuantity() > material.getMaxQuantity()) {
            throw exception(CUSTOMIZATION_SPEC_OUT_OF_RANGE);
        }
        if (spec.getRemark() != null && spec.getRemark().length() > 500) {
            throw exception(CUSTOMIZATION_SPEC_OUT_OF_RANGE);
        }
    }

    public void validateFiles(List<CustomizationFileBO> files) {
        if (files == null || files.size() < FILE_MIN_COUNT || files.size() > FILE_MAX_COUNT) {
            throw exception(CUSTOMIZATION_FILE_INVALID);
        }
        for (CustomizationFileBO file : files) {
            if (file == null || StrUtil.isBlank(file.getUrl())) {
                throw exception(CUSTOMIZATION_FILE_INVALID);
            }
            if (StrUtil.isNotBlank(fileDomain) && !file.getUrl().startsWith(fileDomain)) {
                throw exception(CUSTOMIZATION_FILE_INVALID);
            }
            String extension = extensionOf(file);
            if (!FILE_ALLOWED_EXTENSIONS.contains(extension)) {
                throw exception(CUSTOMIZATION_FILE_INVALID);
            }
            if (file.getSize() != null && file.getSize() > FILE_MAX_SIZE) {
                throw exception(CUSTOMIZATION_FILE_INVALID);
            }
        }
    }

    /** 优先取文件名，其次取 URL 的路径部分 */
    static String extensionOf(CustomizationFileBO file) {
        String name = StrUtil.isNotBlank(file.getName()) ? file.getName() : file.getUrl();
        int query = name.indexOf('?');
        if (query >= 0) {
            name = name.substring(0, query);
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

}
