package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_SHARE_FILE_STORAGE_FAILED;

/**
 * 共享内容的文件存储封装：上传走 infra FileApi，读取返回 5 分钟预签名地址。
 */
@Component
@Slf4j
public class ShareFileStorage {

    private static final String DIRECTORY = "maxlabel-share";
    private static final int PRESIGN_SECONDS = 300;

    @Resource
    private FileApi fileApi;

    public String store(byte[] content, String name, String mimeType) {
        try {
            return fileApi.createFile(content, name, DIRECTORY, mimeType);
        } catch (Exception exception) {
            log.error("[store][name({}) 文件保存失败]", name, exception);
            throw exception(MEMBER_SHARE_FILE_STORAGE_FAILED);
        }
    }

    /**
     * 返回短时下载地址；存储后端不支持预签名时退回原地址。
     */
    public String presign(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        try {
            return fileApi.presignGetUrl(fileUrl, PRESIGN_SECONDS).getCheckedData();
        } catch (Exception exception) {
            log.warn("[presign][url({}) 预签名失败，退回原地址]", fileUrl, exception);
            return fileUrl;
        }
    }

}
