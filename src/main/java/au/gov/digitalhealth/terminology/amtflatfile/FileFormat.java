package au.gov.digitalhealth.terminology.amtflatfile;

public enum FileFormat {
    TSV("tsv"), CSV("csv");

    private static final String DOUBLEQUOTE = "\"";
    private final String value;

    FileFormat(String value) {
        this.value = value;
    }

    public String getFilePath(String outputFilePath) {
        return outputFilePath + "." + this.getExtension();
    }

    public String getExtension() {
        return this.value;
    }

    public String getMimeType() {
        return "text/" + this.value;
    }

    public String getDelimiter() {
        if (this.value.equals("csv")) {
            return ",";
        } else if (this.value.equals("tsv")) {
            return "\t";
        }
        return "";
    }

    public String getFieldQuote() {
        return DOUBLEQUOTE;
    }


    public static FileFormat fromValue(String value) {
        for (FileFormat kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown File Format: " + value);
    }
}
