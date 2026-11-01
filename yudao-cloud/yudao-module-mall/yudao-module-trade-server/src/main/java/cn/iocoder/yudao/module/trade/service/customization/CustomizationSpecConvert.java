package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.module.trade.controller.app.customization.vo.AppCustomizationFileVO;
import cn.iocoder.yudao.module.trade.controller.app.customization.vo.AppCustomizationSpecReqVO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationFileBO;
import cn.iocoder.yudao.module.trade.service.customization.bo.CustomizationSpecBO;

import java.util.ArrayList;
import java.util.List;

/**
 * 定制规格 VO → BO 转换（014）
 */
public final class CustomizationSpecConvert {

    private CustomizationSpecConvert() {
    }

    public static CustomizationSpecBO toSpec(AppCustomizationSpecReqVO vo) {
        CustomizationSpecBO spec = new CustomizationSpecBO();
        spec.setMaterialId(vo.getMaterialId());
        spec.setWidth(vo.getWidth());
        spec.setHeight(vo.getHeight());
        spec.setQuantity(vo.getQuantity());
        spec.setMode(vo.getMode());
        spec.setForm(vo.getForm());
        spec.setSheetSpec(vo.getSheetSpec());
        spec.setFinishing(vo.getFinishing());
        spec.setTearLine(Boolean.TRUE.equals(vo.getTearLine()));
        // 模切收费：圆角 或 撕裂线（直角不额外收）
        spec.setDieCut(Boolean.TRUE.equals(vo.getDieCut())
                || Integer.valueOf(1).equals(vo.getFinishing())
                || Boolean.TRUE.equals(vo.getTearLine()));
        spec.setRowCount(vo.getRowCount());
        spec.setColumnCount(vo.getColumnCount());
        spec.setRowGap(vo.getRowGap());
        spec.setColumnGap(vo.getColumnGap());
        spec.setAxisDiameter(vo.getAxisDiameter());
        spec.setLabelCount(vo.getLabelCount());
        spec.setTemplateName(vo.getTemplateName());

        spec.setUrgent(Boolean.TRUE.equals(vo.getUrgent()));
        spec.setRemark(vo.getRemark());
        if (vo.getFiles() != null) {
            List<CustomizationFileBO> files = new ArrayList<>(vo.getFiles().size());
            for (AppCustomizationFileVO fileVO : vo.getFiles()) {
                CustomizationFileBO file = new CustomizationFileBO();
                file.setName(fileVO.getName());
                file.setUrl(fileVO.getUrl());
                file.setSize(fileVO.getSize());
                file.setType(fileVO.getType());
                files.add(file);
            }
            spec.setFiles(files);
        }
        return spec;
    }

}
