package msa.bookcatalog.infra.kakao;

import lombok.Data;

import java.util.List;

@Data
public class KakaoBookResponse {
    private Meta meta;
    private List<Document> documents;

    @Data
    public static class Meta {
        private int total_count;
        private int pageable_count;
        private boolean is_end;
    }

    @Data
    public static class Document {
        private String title;
        private String contents;
        private String url;
        private String isbn;
        private String datetime;
        private List<String> authors;
        private String publisher;
        private List<String> translators;
        private int price;
        private int sale_price;
        private String thumbnail;
        private String status;
    }
}
