package msa.bookloan.domain.model;

public enum BookType {
    NEW_RELEASE("신간"),
    POPULAR("인기"),
    STANDARD("일반"),
    UNKNOWN("알수없음");

    private final String displayName;
    BookType(String displayName) { this.displayName = displayName; }
    public String code() { return name(); }
    public String displayName() { return displayName; }

    /*
     * @param code 대문자/소문자 구분 없는 BookType 코드 문자열
     * @return 매핑되는 BookType Enum. 유효하지 않은 코드일 경우 UNKNOWN을 반환합니다.
     */
    public static BookType from(String code) {
        if (code == null || code.trim().isEmpty()) {
            return UNKNOWN;
        }
        try {
            // 대소문자 구분 없이 비교하기 위해 toUpperCase() 사용
            return BookType.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            // BookLoan 서비스가 알지 못하는 새로운 BookType 코드가 들어온 경우
            // 예외를 발생시키지 않고 UNKNOWN으로 처리하여 안정성을 높입니다.
            return UNKNOWN;
        }
    }
}

