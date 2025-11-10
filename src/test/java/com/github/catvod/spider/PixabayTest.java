package com.github.catvod.spider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author Knifer
 */
public class PixabayTest {

    private static Pixabay spider;

    @BeforeAll
    static void init() {
        spider = new Pixabay();
    }

    @Test
    void homeContent() {
        System.out.println(spider.homeContent(false));
    }

    @Test
    void categoryContent() {
        System.out.println(spider.categoryContent(
                "/zh/videos/search/%e9%a3%8e%e6%99%af/",
                "1",
                true,
                new LinkedHashMap<>() {{
                    put("order", "ec");
                }}
        ));
    }

    @Test
    void detailContent() {
        spider.detailContent(
                List.of("/zh/videos/moraine-lake-banff-national-park-292827/")
        );
    }

    @Test
    void searchContent() {
        System.out.println(spider.searchContent("火山", false, "1"));
    }

    @Test
    void playerContent() {
        System.out.println(spider.playerContent(
                "第1集",
                "https://cdn.pixabay.com/video/2024/03/12/203871-922675715_large.mp4",
                List.of()
        ));
    }
}
