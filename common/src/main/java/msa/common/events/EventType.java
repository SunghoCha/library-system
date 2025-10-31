package msa.common.events;

public interface EventType {

    /**
     * 이벤트/커맨드 타입 문자열을 String으로 반환
     * ex) bookcatalog.created, member.check 등..
     */
    String getValue();
}
