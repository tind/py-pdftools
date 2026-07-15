package dev.pypdftools.nativeapi;

/** Stable status values returned by native ABI version 1. */
public final class NativeStatus {
    public static final int SUCCESS = 0;
    public static final int INVALID_PDF = 1;
    public static final int INVALID_OCR_DATA = 2;
    public static final int PAGE_MISMATCH = 3;
    public static final int PDF_PASSWORD_REQUIRED = 4;
    public static final int PDF_PERMISSION = 5;
    public static final int FONT_ERROR = 6;
    public static final int PDF_PROCESSING = 7;

    private NativeStatus() {}
}
