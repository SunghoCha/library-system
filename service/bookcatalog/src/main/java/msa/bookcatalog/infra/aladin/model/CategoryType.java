package msa.bookcatalog.infra.aladin.model;

public enum CategoryType {
    NOVEL_POETRY_DRAMA   (1,     "소설/시/희곡"),
    HUMANITIES           (16034, "인문학"),
    EDUCATION            (8412,  "교육"),
    SELF_DEVELOPMENT     (336,   "자기계발"),
    BUSINESS             (170,   "경제/경영"),
    COOKING              (1230,  "요리"),
    HEALTH_MEDICINE      (55890, "건강/의학"),
    TRAVEL               (51377, "여행"),
    NATURAL_SCIENCE      (8260,  "자연과학"),
    SOCIAL_SCIENCE       (8259,  "사회과학"),
    RELIGION             (17436, "종교"),
    HISTORY              (8517,  "역사"),
    ART                  (8986,  "예술"),
    FOREIGN_LANGUAGE     (1322,  "외국어"),
    CHILDREN             (1108,  "어린이"),
    PHILOSOPHY           (8516,  "철학");  // 새로 추가된 항목

    private final int id;
    private final String displayName;

    CategoryType(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
