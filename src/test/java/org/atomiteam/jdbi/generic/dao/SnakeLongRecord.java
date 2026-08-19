package org.atomiteam.jdbi.generic.dao;

public class SnakeLongRecord extends LongEntity {
    private String sampleColumn;
    private Long createdAt;

    @Column("legacy_label")
    private String displayLabel;

    public String getSampleColumn() {
        return sampleColumn;
    }

    public void setSampleColumn(String sampleColumn) {
        this.sampleColumn = sampleColumn;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }
}
