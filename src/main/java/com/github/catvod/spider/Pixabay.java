package com.github.catvod.spider;

import cn.hutool.core.util.StrUtil;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.constant.HttpHeaders;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.SSLSocketClient;
import com.google.common.base.Charsets;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Knifer
 */
public class Pixabay extends Spider {

    private static final String SITE_URL = "https://pixabay.com";

    private static final List<Filter> FILTERS = List.of(
            new Filter("order", "排序", List.of(
                    new Filter.Value("最新", "latest"),
                    new Filter.Value("编辑精选", "ec"),
                    new Filter.Value("热门", "trending"),
                    new Filter.Value("最相关", StrUtil.EMPTY)
            ))
    );

    private static final Map<String, String> headers = new HashMap<>(){{
        put(HttpHeaders.USER_AGENT, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.6822.82 Safari/537.36");
        put(HttpHeaders.ACCEPT, "*/*");
    }};

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .proxy(new java.net.Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost",1080)))
            .addNetworkInterceptor(new ConditionalHeaderRemovalInterceptor())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .sslSocketFactory(SSLSocketClient.getSSLSocketFactory(), SSLSocketClient.getX509TrustManager())
            .hostnameVerifier((SSLSocketClient.getHostnameVerifier()))
            .build();

    private void useCookie(String url) {
        Request req = new Request.Builder().url(url).headers(Headers.of(headers)).build();

        try (Response resp = client.newCall(req).execute()) {
            headers.put(HttpHeaders.Cookie, StringUtils.substringBefore(resp.headers().get("Set-Cookie"), "; path=").trim());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String homeContent(boolean filter) {
        String url = SITE_URL + "/zh/videos/";
        Document doc = Jsoup.parse(OkHttp.string(url, headers));
        Elements elms = doc.select("a[class=tag--GBLgc dark--G9rJr]");
        List<Class> classes;
        LinkedHashMap<String, List<Filter>> filters;
        List<Vod> vodList;

        if (elms.isEmpty()) {
            return StrUtil.EMPTY;
        }
        filters = new LinkedHashMap<>();
        classes = elms.stream()
                .map(e -> {
                    String typeId = e.attr("href");

                    filters.put(typeId, FILTERS);

                    return new Class(typeId, e.text());
                })
                .toList();
        vodList = parseVodList(doc);

        return Result.string(classes, vodList, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        String url = SITE_URL + "%s?order=%s&pagi=%s";
        String orderVal = StrUtil.EMPTY;
        Document doc;
        Element elm;
        int total = Integer.MAX_VALUE;
        int pageCount = Integer.MAX_VALUE;
        int page = Integer.parseInt(pg);
        int limit;
        List<Vod> vodList;

        if (filter) {
            orderVal = extend.getOrDefault("order", StrUtil.EMPTY);
        }
        url = String.format(url, tid, orderVal, pg);
        doc = Jsoup.parse(OkHttp.string(url, headers));
        // page
        elm = doc.selectFirst("h1.h1--bZ6EI");
        if (elm != null) {
            total = NumberUtils.toInt(StringUtils.substringBefore(elm.text(), " "), total);
        }
        elm = doc.selectFirst(".indicator--Nf9Sc");
        if (elm != null) {
            page = NumberUtils.toInt(StringUtils.substringBetween(elm.text(), "第", "页"), page);
            pageCount = NumberUtils.toInt(StringUtils.substringBetween(elm.text(), "共", "页"), pageCount);
        }
        vodList = parseVodList(doc);
        limit = vodList.size();

        return Result.string(page, pageCount, limit, total, vodList);
    }

    private List<Vod> parseVodList(Document doc) {
        Elements elms = doc.select("a[class=link--WHWzm]");
        List<Vod> vodList;

        if (elms.isEmpty()) {
            vodList = List.of();
        } else {
            vodList = elms.stream()
                    .map(e -> {
                        Vod vod = new Vod();
                        String vodId = e.attr("href");
                        Element img = e.selectFirst("img");

                        vod.setVodId(vodId);
                        if (img != null) {
                            vod.setVodName(img.attr("alt"));
                            vod.setVodPic(img.attr("src"));
                        }
                        vod.setVodRemarks("4K");

                        return vod;
                    })
                    .toList();
        }

        return vodList;
    }

    @Override
    public String detailContent(List<String> ids) {
        String id = ids.get(0);
        Document doc = Jsoup.parse(OkHttp.string(SITE_URL + id, headers));
        Element elm;
        Vod vod = new Vod();
        String vodId;
        String vodName;

        elm = doc.selectFirst("a.button--9NFL8");
        if (elm == null) {

            return StrUtil.EMPTY;
        }
        vodId = URLDecoder.decode(
                StringUtils.substringBetween(elm.attr("href"), "file-url=", "&"),
                Charsets.UTF_8
        );
        if (StringUtils.isBlank(vodId)) {

            return StrUtil.EMPTY;
        }
        vod.setVodId(vodId);
        elm = doc.selectFirst("h1.heading---ud1j");
        if (elm != null) {
            vodName = elm.text();
            vod.setVodName(vodName);
            vod.setVodContent(vodName);
        }
        elm = doc.selectFirst("picture.vjs-poster > img");
        if (elm != null) {
            vod.setVodPic(elm.attr("src"));
        }
        vod.setVodPlayFrom("Pixabay");
        vod.setVodPlayUrl("第1集$" + vodId);

        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        return categoryContent("/zh/videos/search/" + key + "/", pg, false, new HashMap<>());
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).string();
    }

    public static class ConditionalHeaderRemovalInterceptor implements Interceptor {
        @NotNull
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            Request.Builder requestBuilder = originalRequest.newBuilder();

            requestBuilder.removeHeader("Accept-Encoding");
            requestBuilder.removeHeader("Connection");

            return chain.proceed(requestBuilder.build());
        }
    }
}
