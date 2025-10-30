package msa.common.util;

import org.springframework.util.StringUtils;

public final class IdConverter {

    private IdConverter() {
    }

    public static Long parseLongOrThrow(String idString, String name) {
        if (!StringUtils.hasText(idString)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }

        try {
            return Long.parseLong(idString);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name + " format: '" + idString + "'", e);
        }
    }

    public static String toStringOrNull(Long idLong) {
        return idLong != null ? String.valueOf(idLong) : null;
    }
}
