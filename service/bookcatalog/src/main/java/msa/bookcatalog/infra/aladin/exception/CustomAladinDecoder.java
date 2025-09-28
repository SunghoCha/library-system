package msa.bookcatalog.infra.aladin.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import lombok.RequiredArgsConstructor;
import msa.bookcatalog.infra.aladin.dto.AladinErrorDto;
import org.springframework.cloud.openfeign.support.SpringDecoder;

import java.io.IOException;
import java.lang.reflect.Type;

@RequiredArgsConstructor
public class CustomAladinDecoder implements Decoder {

    private final SpringDecoder springDecoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Object decode(Response response, Type type) throws IOException {
        // 1. 응답 본문을 문자열로 읽습니다. (주의: 이 작업은 한 번만 가능)
        String bodyStr = Util.toString(response.body().asReader(Util.UTF_8));

        // 2. 문자열을 JsonNode 객체로 파싱하여 'errorCode' 필드가 있는지 확인합니다.
        JsonNode rootNode = objectMapper.readTree(bodyStr);
        if (rootNode.has("errorCode")) {
            // 3. errorCode가 있다면, 에러 DTO로 변환하고 커스텀 예외를 던집니다.
            AladinErrorDto errorDto = objectMapper.treeToValue(rootNode, AladinErrorDto.class);
            String message = String.format("Aladin API Error >> Code: %d, Message: %s",
                    errorDto.errorCode(), errorDto.errorMessage());
            throw new AladinApiException(message);
        }

        // 4. errorCode가 없다면, 성공 응답이므로 원래 디코더(SpringDecoder)에게 위임합니다.
        //    (이미 읽어버린 body를 다시 채워서 보내야 함)
        Response newResponse = response.toBuilder().body(bodyStr, Util.UTF_8).build();
        return springDecoder.decode(newResponse, type);
    }
}