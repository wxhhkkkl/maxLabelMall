package cn.iocoder.yudao.module.member.service.labelshop;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopCate;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopCompany;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSession;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSts;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * LabelShop 只读客户端。
 *
 * <p>只允许调用 {@link #READONLY_PROCS} 中的过程；密码只在登录时使用，不落库、不打印。</p>
 */
@Slf4j
@Component
public class LabelshopClient {

    private static final Set<String> READONLY_PROCS = Set.of(
            "CodingV.Template.GetCateTree",
            "CodingV.Template.GetCateList",
            "CodingV.Template.GetTemplate",
            "CodingV.Account.AliyunSTSToken");

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LabelshopImportProperties properties;
    private final LabelshopHttp http;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LabelshopClient(LabelshopImportProperties properties, LabelshopHttp http) {
        this.properties = properties;
        this.http = http;
    }

    /**
     * 登录并返回本次会话（token / uid / 公司列表）。
     */
    public LabelshopSession login(String account, String password) {
        String mid = resolveMid();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mid", mid);
        body.put("mname", hostname());
        body.put("pwd", DigestUtil.md5Hex(password).toUpperCase(Locale.ROOT));
        body.put("sid", "1");
        body.put("try", 2);
        body.put("uname", account);
        String bodyJson = toJson(body, "登录");
        String url = base() + "/LabelShop/Login/?d=" + encode(account);
        log.info("LabelShop 登录 mid={} mname={} ua={}", mid, hostname(), buildUserAgent(mid));
        LabelshopHttp.Result result = http.postJson(url, bodyJson,
                Map.of("Content-Type", "application/json", "Accept", "*/*",
                        "User-Agent", buildUserAgent(mid)));
        if (result.status() != 200) {
            log.warn("LabelShop 登录 HTTP {} body={}", result.status(), snippet(result.body()));
            throw new LabelshopLoginException("历史系统登录失败（HTTP " + result.status() + "）");
        }
        JsonNode root = parse(result.body(), "登录");
        JsonNode datas = root.path("datas");
        String token = datas.path("token").asText("");
        if (StrUtil.isBlank(token)) {
            token = result.cookies().getOrDefault("cvstoken", "");
        }
        long uid = datas.path("id").asLong(0);
        if (uid <= 0) {
            String cookieUid = result.cookies().get("cvsuid");
            if (StrUtil.isNumeric(cookieUid)) {
                uid = Long.parseLong(cookieUid);
            }
        }
        if (StrUtil.isBlank(token) || uid <= 0) {
            throw new LabelshopLoginException("历史系统登录失败 code="
                    + root.path("code").asText("") + " message=" + root.path("message").asText(""));
        }
        LabelshopSession session = new LabelshopSession();
        session.setToken(token);
        session.setUid(uid);
        session.setMid(mid);
        session.setCompanies(parseCompanies(datas));
        return session;
    }

    public List<LabelshopCate> getCateTree(LabelshopSession session, int type, int mode) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("@MODE", mode);
        params.put("@TYPE", type);
        params.put("@UID", session.getUid());
        JsonNode root = exec(session, "CodingV.Template.GetCateTree", params);
        List<LabelshopCate> list = new ArrayList<>();
        for (JsonNode item : array(root.get("datas"))) {
            Long cateId = longValue(find(item, "Cate_ID", "CATE_ID"));
            if (cateId == null) {
                continue;
            }
            // CATE0（0）与 LabelShop5（-1）也是合法根分类，不能按 <=0 过滤
            Long ownType = longValue(find(item, "Own_Type", "OWN_TYPE"));
            list.add(new LabelshopCate(cateId, longValue(find(item, "Parent_ID", "PARENT_ID")),
                    textValue(find(item, "Name", "NAME")),
                    ownType == null ? null : ownType.intValue()));
        }
        return list;
    }

    public List<LabelshopTemplate> getCateList(LabelshopSession session, long cateId, int ownType) {
        return getCateList(session, cateId, ownType, session.getUid());
    }

    /**
     * 按指定 UID 读取列表；企业 / 组织模板用公司 ID 作为 @UID。
     */
    public List<LabelshopTemplate> getCateList(LabelshopSession session, long cateId, int ownType, long uid) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("@CATEID", cateId);
        params.put("@OWNTYPE", ownType);
        params.put("@UID", uid);
        JsonNode root = exec(session, "CodingV.Template.GetCateList", params);
        List<LabelshopTemplate> list = new ArrayList<>();
        for (JsonNode item : array(root.get("datas"))) {
            String ref = textValue(find(item, "Tmpl_Uuid", "TMPL_UUID", "Tmpl_UUID"));
            if (StrUtil.isBlank(ref)) {
                continue;
            }
            list.add(new LabelshopTemplate(ref, textValue(find(item, "Tmpl_Name", "TMPL_NAME"))));
        }
        return list;
    }

    /**
     * 读取单个模板正文（.lsdx XML）。
     */
    public String getTemplate(LabelshopSession session, String ref, int ownType) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("@TMPLID", ref);
        params.put("@OWNTYPE", ownType);
        params.put("@UID", session.getUid());
        params.put("@MODE", 0);
        JsonNode root = exec(session, "CodingV.Template.GetTemplate", params);
        JsonNode data = root.path("datas").path("data");
        if (data.isArray() && !data.isEmpty()) {
            data = data.get(0);
        }
        if (!data.isObject()) {
            throw new LabelshopTemplateException("返回中没有 data");
        }
        String label = textValue(find(data, "LABEL", "Label"));
        if (StrUtil.isBlank(label)) {
            throw new LabelshopTemplateException("LABEL 为空");
        }
        return label;
    }

    /**
     * 企业 / 组织模板下载所需的 STS 临时凭据。
     */
    public LabelshopSts getSts(LabelshopSession session) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("@NAME", session.getMid());
        params.put("@DURA", 28800);
        JsonNode root = exec(session, "CodingV.Account.AliyunSTSToken", params);
        JsonNode data = root.path("datas");
        if (data.isArray() && !data.isEmpty()) {
            data = data.get(0);
        }
        if (!data.isObject()) {
            throw new LabelshopUpstreamException("STS 接口没有返回凭据");
        }
        LabelshopSts sts = new LabelshopSts();
        sts.setAccessKeyId(textValue(find(data, "AccessKeyId")));
        sts.setAccessKeySecret(textValue(find(data, "AccessKeySecret")));
        sts.setSecurityToken(textValue(find(data, "SecurityToken")));
        sts.setBucketEndpoint(textValue(find(data, "BucketEndpoint")));
        sts.setBucketTmpl(textValue(find(data, "BucketTmpl")));
        sts.setBucketImage(textValue(find(data, "BucketImage")));
        if (StrUtil.isBlank(sts.getAccessKeyId()) || StrUtil.isBlank(sts.getAccessKeySecret())
                || StrUtil.isBlank(sts.getSecurityToken())) {
            throw new LabelshopUpstreamException("STS 接口没有返回凭据");
        }
        return sts;
    }

    /**
     * 用 STS 凭据读取 OSS 对象（企业模板 JSON）。
     */
    public byte[] getOssObject(LabelshopSts sts, String bucket, String key) {
        String endpoint = StrUtil.blankToDefault(sts.getBucketEndpoint(), "oss-cn-beijing.aliyuncs.com");
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
        String canonical = "x-oss-security-token:" + sts.getSecurityToken()
                + "\n/" + bucket + "/" + key;
        String toSign = "GET\n\n\n" + date + "\n" + canonical;
        String signature = Base64.getEncoder().encodeToString(
                hmacSha1(sts.getAccessKeySecret(), toSign));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Date", date);
        headers.put("x-oss-security-token", sts.getSecurityToken());
        headers.put("Authorization", "OSS " + sts.getAccessKeyId() + ":" + signature);
        headers.put("User-Agent", buildUserAgent(resolveMid()));
        String url = "https://" + bucket + "." + endpoint + "/" + encodePath(key);
        LabelshopHttp.Result result = http.get(url, headers);
        if (result.status() != 200) {
            throw new LabelshopTemplateException("OSS JSON HTTP " + result.status());
        }
        return result.bytes();
    }

    // ======================= 内部 =======================

    private JsonNode exec(LabelshopSession session, String proc, Map<String, Object> params) {
        if (!READONLY_PROCS.contains(proc)) {
            throw new LabelshopUpstreamException("拒绝调用非只读过程：" + proc);
        }
        String procParams = toJson(params, proc);
        String time = LocalDateTime.now().format(TIME_FORMAT);
        String t = String.valueOf(Instant.now().getEpochSecond());
        String sign = DigestUtil.md5Hex("executetype=0&proc=" + proc
                + "&procparams=" + procParams + "&time=" + time + session.getToken())
                .toUpperCase(Locale.ROOT);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("executetype", "0");
        body.put("proc", proc);
        body.put("procparams", procParams);
        body.put("returntype", "2");
        body.put("sign", sign);
        body.put("sql", "");
        body.put("t", t);
        body.put("time", time);
        body.put("token", session.getToken());
        String url = base() + "/LabelShop/Exec/?p=" + encode(proc) + "&i=" + session.getUid();
        LabelshopHttp.Result result = http.postJson(url, toJson(body, proc), headers(session));
        if (result.status() == 401 || result.status() == 403) {
            throw new LabelshopSessionExpiredException("历史系统会话失效（HTTP " + result.status() + "）");
        }
        if (result.status() != 200) {
            log.warn("LabelShop {} HTTP {} params={} body={}", proc, result.status(), procParams,
                    snippet(result.body()));
            throw new LabelshopUpstreamException(proc + " HTTP " + result.status());
        }
        JsonNode root = parse(result.body(), proc);
        int code = root.path("code").asInt(0);
        String message = root.path("message").asText("");
        if (code != 0) {
            if (isSessionExpired(code, message)) {
                throw new LabelshopSessionExpiredException("历史系统会话失效：" + message);
            }
            throw new LabelshopUpstreamException(proc + " code=" + code + " message=" + message);
        }
        return root;
    }

    private Map<String, String> headers(LabelshopSession session) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "*/*");
        headers.put("User-Agent", buildUserAgent(session.getMid()));
        headers.put("Cookie", "cvstoken=" + session.getToken() + "; cvsuid=" + session.getUid());
        return headers;
    }

    private List<LabelshopCompany> parseCompanies(JsonNode datas) {
        JsonNode raw = datas.get("companys");
        if (raw == null || raw.isNull()) {
            raw = datas.get("companies");
        }
        List<LabelshopCompany> list = new ArrayList<>();
        if (raw == null || !raw.isArray()) {
            return list;
        }
        for (JsonNode item : raw) {
            if (!item.isObject()) {
                continue;
            }
            Long id = longValue(find(item, "id", "uid", "com_uid", "comuid", "companyid",
                    "company_id", "com_id", "comid", "cid", "orgid", "org_id"));
            if (id == null || id <= 0) {
                continue;
            }
            String name = textValue(find(item, "name", "companyname", "company_name", "com_name",
                    "comname", "cname", "shortname", "title", "orgname", "org_name"));
            list.add(new LabelshopCompany(id, StrUtil.blankToDefault(name, "组织" + id)));
        }
        return list;
    }

    /** 上游非 200 时只保留可读片段进日志，避免把整页错误/凭证打出来 */
    private static String snippet(String body) {
        String text = StrUtil.nullToEmpty(body).replaceAll("\\s+", " ").trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private String resolveMid() {
        if (StrUtil.isNotBlank(properties.getMid())) {
            return properties.getMid().trim();
        }
        return UUID.nameUUIDFromBytes(("labelshop:" + hostname()).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "maxlabel";
        }
    }

    private String buildUserAgent(String mid) {
        return "LabelShop/" + properties.getUaShort() + " (" + properties.getUaOs()
                + "; M/" + StrUtil.nullToEmpty(mid).toLowerCase(Locale.ROOT) + ") Version/1.1.0."
                + properties.getUaBuild() + " language/zh-CN";
    }

    private String base() {
        String value = StrUtil.blankToDefault(properties.getBaseUrl(), "https://o.codingv.com/v1");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String toJson(Object value, String label) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new LabelshopUpstreamException(label + " 请求序列化失败");
        }
    }

    private JsonNode parse(String body, String label) {
        try {
            JsonNode root = objectMapper.readTree(StrUtil.nullToEmpty(body));
            if (root == null || !root.isObject()) {
                throw new LabelshopUpstreamException(label + " 返回不是 JSON 对象");
            }
            return root;
        } catch (LabelshopUpstreamException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LabelshopUpstreamException(label + " 返回不是 JSON");
        }
    }

    private static boolean isSessionExpired(int code, String message) {
        if (code == 401 || code == 403) {
            return true;
        }
        String text = StrUtil.nullToEmpty(message);
        return text.contains("登录") || text.contains("未登录") || text.contains("过期")
                || text.toLowerCase(Locale.ROOT).contains("token");
    }

    private static Iterable<JsonNode> array(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return node;
    }

    private static JsonNode find(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            for (String key : keys) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static Long longValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        try {
            return Long.parseLong(node.asText());
        } catch (Exception ex) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodePath(String key) {
        StringBuilder sb = new StringBuilder();
        for (String part : key.split("/", -1)) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(encode(part));
        }
        return sb.toString();
    }

    private static byte[] hmacSha1(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new LabelshopUpstreamException("OSS 签名失败");
        }
    }

}
