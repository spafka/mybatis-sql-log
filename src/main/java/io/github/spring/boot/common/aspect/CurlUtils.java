package io.github.spring.boot.common.aspect;

import static io.github.spring.boot.common.aspect.StringUtils.isBlank;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.ALL;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;
import static org.springframework.http.MediaType.parseMediaType;


import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * build curl from request
 *
 * <p>
 * Created by zhanghaolun on 16/7/4.
 * </p>
 */
@Slf4j
public abstract class CurlUtils {

    private CurlUtils() {
    }

    private static final Collection<String> RETAIN_HEADERS = Arrays.asList(ACCEPT, CONTENT_TYPE, AUTHORIZATION);


    /**
     * 安置http请求, 生成curl命令.
     *
     * @param request {@link HttpServletRequest}
     * @return curl command
     */
    public static String curl(final HttpServletRequest request) {
        final String result;
        if (request != null) {
            final MediaType contentType =
                    isNotBlank(request.getContentType()) ? parseMediaType(request.getContentType()) : ALL;
            final String headers = curlHeaders(request);
            final String parameters = curlParameters(request);

            final StringBuilder curl = new StringBuilder("curl ").append(headers).append(" ")
                    .append("-X ").append(request.getMethod()).append(" ");


            if (APPLICATION_JSON.includes(contentType) || APPLICATION_XML.includes(contentType)) {
                curl.append("--data '").append(curlBody(request)).append("' ");
            } else if (APPLICATION_FORM_URLENCODED == contentType) {
                curl.append("--data '").append(parameters).append("' ");
            } else if (isNotBlank(parameters)) {
                curl.append('?').append(parameters).append(' ');

            }
            curl.append(request.getRequestURL());
            result = curl.toString();
        } else {
            result = "";
        }
        return result;
    }

    private static boolean isNotBlank(String contentType) {
        return contentType != null && !contentType.isEmpty();
    }

    static String curlHeaders(final HttpServletRequest request) {
        @SuppressWarnings("rawtypes") final Enumeration headerNames = request.getHeaderNames();
        final StringBuilder hBuilder = new StringBuilder();
        while (headerNames.hasMoreElements()) {
            final String name = (String) headerNames.nextElement();
            final String value = request.getHeader(name);
            hBuilder.append("-H '").append(name).append(": ").append(value).append("' ");
        }
        return hBuilder.toString();
    }

    @SneakyThrows
    static String curlParameters(final HttpServletRequest request) {
        @SuppressWarnings("rawtypes") final Enumeration parameterNames = request.getParameterNames();
        final StringBuilder pBuilder = new StringBuilder();
        while (parameterNames.hasMoreElements()) {
            final String name = (String) parameterNames.nextElement();
            final String value = request.getParameter(name);
            pBuilder //
                    .append('&') //
                    .append(name) //
                    .append('=') //
                    .append(urlEncode(value));
        }
        return pBuilder.length() > 0 ? pBuilder.substring(1) : "";
    }

    @SneakyThrows
    public static String curlBody(HttpServletRequest request) {
        final Charset charset = findCharset(request);
        try {
            // read raw inputStream first. (may be has not been read, for example 404)
            final String raw = StreamUtils.copyToString(request.getInputStream(), charset);
            final String result;
            if (isBlank(raw)) { // if no content in raw inputStream, 那应该是读过了, try to read cached.

                RequestAttributes requestAttributes = (RequestContextHolder.currentRequestAttributes());
                if (requestAttributes instanceof ServletRequestAttributes) {
                    ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) requestAttributes;
                    Field field = ReflectionUtils.findField(ServletRequestAttributes.class, "request");
                    if (field != null) {
                        ReflectionUtils.makeAccessible(field);
                        request = (HttpServletRequest) ReflectionUtils.getField(field, servletRequestAttributes);

                    }
                }
                final ContentCachingRequestWrapper wrapper = findWrapper(request, ContentCachingRequestWrapper.class);
                if (wrapper != null) {
                    result = new String(((ContentCachingRequestWrapper) request).getContentAsByteArray(), charset);
                } else {
                    result = "";
                }
            } else {
                result = raw;
            }
            return result;
        } catch (final IOException ex) {
            log.warn("error reading request body.", ex);
        }
        return "";
    }


    /**
     * other ways to do this.
     * <pre>
     * {@code
     * new org.apache.commons.codec.binary.Base64().encode,encodeToString
     * io.jsonwebtoken.impl.TextCodec.BASE64.encode
     * com.fasterxml.jackson.core.Base64Variants.MIME_NO_LINEFEEDS.encode()
     * new String(org.apache.commons.codec.binary.Base64.encodeBase64(raw, UTF_8)
     * }
     * </pre>
     *
     * @param raw raw bytes
     * @return base64 string
     */
    public static String encodeBase64(final byte[] raw) {
        return java.util.Base64.getEncoder().encodeToString(raw);
    }

    /**
     * other ways to do this.
     * <pre>
     * {@code
     * org.springframework.security.crypto.codec.Base64.decode(base64String.getBytes(UTF_8.name()));
     * new sun.misc.BASE64Decoder().decodeBuffer(base64String);
     * org.apache.commons.codec.binary.Base64.decodeBase64(base64String);
     * }
     * </pre>
     *
     * @param base64String base64 string
     * @return raw bytes
     */
    @SneakyThrows
    public static byte[] decodeBase64(final String base64String) {
        return java.util.Base64.getDecoder().decode(base64String);
    }


    /**
     * other ways to do this.
     * <pre>
     * {@code
     * java.net.URLEncoder.encode(text, UTF_8.name())
     * }
     * </pre>
     *
     * @param text text to encode
     * @return encoded text
     */
    @SneakyThrows
    public static String urlEncode(final String text) {
        return text != null ? URLEncoder.encode(text, "UTF-8") : null;
    }

    @SneakyThrows
    public static String urlDecode(final String text) {
        return text != null ? URLDecoder.decode(text) : null;
    }


    public static Charset findCharset(final HttpServletRequest request) {
        final String encoding = request.getCharacterEncoding();
        final Charset result;
        if (!isNotBlank(encoding)) {
            result = UTF_8;
        } else {
            result = Charset.forName(encoding);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T extends HttpServletRequestWrapper> T findWrapper( //
                                                                       final ServletRequest request, final Class<T> type //
    ) {
        final T result;
        if (request != null) {
            if (type.isAssignableFrom(request.getClass())) {
                result = (T) request;
            } else {
                if (HttpServletRequestWrapper.class.isAssignableFrom(request.getClass())) {
                    return findWrapper(((HttpServletRequestWrapper) request).getRequest(), type);
                } else {
                    result = null;
                }
            }
        } else {
            result = null;
        }
        return result;
    }
}