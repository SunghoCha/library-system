package msa.bookcatalog.adapter.out.client.kakao;

import lombok.RequiredArgsConstructor;

import java.util.List;

//@Service
@RequiredArgsConstructor
public class KaKaoService {

    private final KakaoBookClient kakaoBookClient;

    public List<KakaoBookResponse.Document> search(String keyword) {
        KakaoBookResponse response = kakaoBookClient.searchBooks(keyword, "accuracy", 1, 10, "title");
        return response.getDocuments();
    }
}
