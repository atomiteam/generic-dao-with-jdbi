package org.atomiteam.jdbi.generic.dao;

/** Strategy used to map Java property names to database column names. */
public enum ColumnNaming {
    IDENTITY {
        @Override
        public String toColumnName(String propertyName) {
            return propertyName;
        }
    },
    /** Existing case-sensitive PostgreSQL/DSQL columns, without a custom codec. */
    QUOTED_IDENTITY {
        @Override
        public String toColumnName(String propertyName) {
            if (propertyName == null || !propertyName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid column property: " + propertyName);
            }
            return "\"" + propertyName + "\"";
        }
    },
    /** Case-preserving MySQL columns without requiring ANSI_QUOTES session settings. */
    BACKTICK_IDENTITY {
        @Override
        public String toColumnName(String propertyName) {
            if (propertyName == null || !propertyName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid column property: " + propertyName);
            }
            return "`" + propertyName + "`";
        }
    },
    SNAKE_CASE {
        @Override
        public String toColumnName(String propertyName) {
            if (propertyName == null || propertyName.isEmpty()) {
                return propertyName;
            }
            String normalized =
                    propertyName
                            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                            .replaceAll("([a-z0-9])([A-Z])", "$1_$2");
            return normalized.toLowerCase();
        }
    };

    public abstract String toColumnName(String propertyName);
}
