package cn.iocoder.yudao.module.trade.dal.mysql.customization;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationConfigDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CustomizationConfigMapper extends BaseMapperX<CustomizationConfigDO> {

    /** 每租户至多一行；没有则返回 null */
    default CustomizationConfigDO selectOne() {
        List<CustomizationConfigDO> list = selectList();
        return list.isEmpty() ? null : list.get(0);
    }

}
