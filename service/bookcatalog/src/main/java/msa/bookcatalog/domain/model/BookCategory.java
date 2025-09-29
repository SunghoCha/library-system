package msa.bookcatalog.domain.model;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum BookCategory {
    NOVEL(1, "소설"),
    ECONOMY(170, "경제/경영"),
    SELF_HELP(74, "자기계발"),
    HUMANITIES(656, "인문"),
    HISTORY(76, "역사/문화"),
    SCIENCE(987, "과학"),
    IT(55890, "컴퓨터/IT"),
    HOME_BEAUTY(1230, "가정/요리/뷰티"),
    KIDS(1196, "어린이"),
    UNKNOWN(-1, "알수없음");

    private final int categoryId;
    private final String categoryName;

    BookCategory(int categoryId, String categoryName) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }
    public int categoryId()   { return categoryId; }
    public String categoryName() { return categoryName; }

    private static final Map<Integer, BookCategory> BY_ID =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(
                    BookCategory::categoryId, x -> x, (a,b)->a));

    public static BookCategory fromId(Integer id) {
        if (id == null) return UNKNOWN;
        return BY_ID.getOrDefault(id, UNKNOWN);
    }
}
