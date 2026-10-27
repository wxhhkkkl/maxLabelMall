package cn.iocoder.yudao.module.product.enums.software;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * 软件版本状态枚举（013）
 *
 * 草稿：已登记元数据但尚未确认文件可用，不可被客户端或服务支持页消费，也不可设为当前发布；
 * 已发布：可被设为当前发布；
 * 已下架：从所有对外入口消失，但文件仍可下载（FR-345）。
 */
@AllArgsConstructor
@Getter
public enum SoftwareVersionStatusEnum implements ArrayValuable<Integer> {

    PUBLISHED(0, "已发布"),
    DRAFT(1, "草稿"),
    UNPUBLISHED(2, "已下架"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(SoftwareVersionStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static SoftwareVersionStatusEnum parse(Integer status) {
        return firstMatch(value -> value.getStatus().equals(status), values());
    }

}
