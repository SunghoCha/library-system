package msa.bookcatalog.infra.kakao;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "kakaoBookClient", url = "https://dapi.kakao.com", configuration = KakaoFeignConfig.class)
public interface KakaoBookClient {

    @GetMapping("/v3/search/book")
    KakaoBookResponse searchBooks(
            @RequestParam("query") String query,
            @RequestParam(value = "sort", defaultValue = "accuracy") String sort,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "target", defaultValue = "title") String target
    );
}
