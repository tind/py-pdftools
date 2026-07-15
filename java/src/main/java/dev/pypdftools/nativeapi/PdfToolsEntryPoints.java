package dev.pypdftools.nativeapi;

import dev.pypdftools.error.FontException;
import dev.pypdftools.error.InvalidOcrDataException;
import dev.pypdftools.error.InvalidPdfException;
import dev.pypdftools.error.PageMismatchException;
import dev.pypdftools.error.PdfPasswordRequiredException;
import dev.pypdftools.error.PdfPermissionException;
import dev.pypdftools.error.PdfProcessingException;
import dev.pypdftools.inspection.PdfInspectionOperation;
import dev.pypdftools.ocr.OcrTextLayerOperation;
import java.nio.charset.StandardCharsets;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

/** C entry points exported by the GraalVM shared library. */
public final class PdfToolsEntryPoints {
    public static final int ABI_VERSION = 1;

    private PdfToolsEntryPoints() {}

    @CEntryPoint(name = "pdftools_abi_version", publishAs = CEntryPoint.Publish.SymbolOnly)
    public static int abiVersion(IsolateThread thread) {
        return ABI_VERSION;
    }

    @CEntryPoint(name = "pdftools_inspect_pdf", publishAs = CEntryPoint.Publish.SymbolOnly)
    public static int inspectPdf(
            IsolateThread thread,
            CCharPointer pdfData,
            UnsignedWord pdfLength,
            NativeBufferPointer output) {
        if (output.isNull()) {
            return NativeErrors.fail(
                    NativeStatus.PDF_PROCESSING,
                    "native inspection output pointer must not be null");
        }
        clearOutput(output);
        NativeErrors.clear();

        try {
            byte[] pdf = copyInput(pdfData, pdfLength, "PDF");
            writeOutput(PdfInspectionOperation.inspect(pdf), output);
            return NativeStatus.SUCCESS;
        } catch (PdfPasswordRequiredException error) {
            return NativeErrors.fail(NativeStatus.PDF_PASSWORD_REQUIRED, error);
        } catch (InvalidPdfException error) {
            return NativeErrors.fail(NativeStatus.INVALID_PDF, error);
        } catch (OutOfMemoryError error) {
            return NativeErrors.fail(
                    NativeStatus.PDF_PROCESSING,
                    "native inspection ran out of memory");
        } catch (Throwable error) {
            return NativeErrors.fail(NativeStatus.PDF_PROCESSING, error);
        }
    }

    @CEntryPoint(
            name = "pdftools_add_ocr_text_layer",
            publishAs = CEntryPoint.Publish.SymbolOnly)
    public static int addOcrTextLayer(
            IsolateThread thread,
            CCharPointer pdfData,
            UnsignedWord pdfLength,
            CCharPointer requestData,
            UnsignedWord requestLength,
            NativeBufferPointer output) {
        if (output.isNull()) {
            return NativeErrors.fail(
                    NativeStatus.PDF_PROCESSING,
                    "native OCR output pointer must not be null");
        }
        clearOutput(output);
        NativeErrors.clear();

        byte[] pdf;
        try {
            pdf = copyInput(pdfData, pdfLength, "PDF");
        } catch (IllegalArgumentException error) {
            return NativeErrors.fail(NativeStatus.INVALID_PDF, error);
        }
        byte[] request;
        try {
            request = copyInput(requestData, requestLength, "OCR request");
        } catch (IllegalArgumentException error) {
            return NativeErrors.fail(NativeStatus.INVALID_OCR_DATA, error);
        }

        try {
            writeOutput(OcrTextLayerOperation.transform(pdf, request), output);
            return NativeStatus.SUCCESS;
        } catch (PageMismatchException error) {
            return NativeErrors.fail(NativeStatus.PAGE_MISMATCH, error);
        } catch (InvalidOcrDataException error) {
            return NativeErrors.fail(NativeStatus.INVALID_OCR_DATA, error);
        } catch (PdfPasswordRequiredException error) {
            return NativeErrors.fail(NativeStatus.PDF_PASSWORD_REQUIRED, error);
        } catch (InvalidPdfException error) {
            return NativeErrors.fail(NativeStatus.INVALID_PDF, error);
        } catch (PdfPermissionException error) {
            return NativeErrors.fail(NativeStatus.PDF_PERMISSION, error);
        } catch (FontException error) {
            return NativeErrors.fail(NativeStatus.FONT_ERROR, error);
        } catch (PdfProcessingException error) {
            return NativeErrors.fail(NativeStatus.PDF_PROCESSING, error);
        } catch (OutOfMemoryError error) {
            return NativeErrors.fail(
                    NativeStatus.PDF_PROCESSING,
                    "native OCR transformation ran out of memory");
        } catch (Throwable error) {
            return NativeErrors.fail(NativeStatus.PDF_PROCESSING, error);
        }
    }

    @CEntryPoint(name = "pdftools_free_buffer", publishAs = CEntryPoint.Publish.SymbolOnly)
    public static void freeBuffer(IsolateThread thread, CCharPointer buffer) {
        if (buffer.isNonNull()) {
            UnmanagedMemory.free(buffer);
        }
    }

    @CEntryPoint(name = "pdftools_last_error", publishAs = CEntryPoint.Publish.SymbolOnly)
    public static int lastError(
            IsolateThread thread,
            CCharPointer buffer,
            UnsignedWord capacity,
            NativeSizePointer requiredSize) {
        if (requiredSize.isNull()) {
            return NativeErrors.fail(
                    NativeStatus.PDF_PROCESSING,
                    "required error-size pointer must not be null");
        }

        byte[] message = NativeErrors.current().getBytes(StandardCharsets.UTF_8);
        requiredSize.setValue(WordFactory.unsigned(message.length + 1));
        if (capacity.equal(0)) {
            return NativeStatus.SUCCESS;
        }
        if (buffer.isNull()) {
            return NativeErrors.fail(
                    NativeStatus.PDF_PROCESSING,
                    "error buffer must not be null when capacity is non-zero");
        }

        int writableCapacity = capacity.aboveThan(WordFactory.unsigned(Integer.MAX_VALUE))
                ? Integer.MAX_VALUE
                : (int) capacity.rawValue();
        int messageBytes = Math.min(message.length, writableCapacity - 1);
        for (int index = 0; index < messageBytes; index++) {
            buffer.write(index, message[index]);
        }
        buffer.write(messageBytes, (byte) 0);
        return NativeStatus.SUCCESS;
    }

    private static byte[] copyInput(
            CCharPointer data,
            UnsignedWord length,
            String inputName) {
        if (length.aboveThan(WordFactory.unsigned(Integer.MAX_VALUE))) {
            throw new IllegalArgumentException(inputName + " input is too large");
        }
        int size = (int) length.rawValue();
        if (size > 0 && data.isNull()) {
            throw new IllegalArgumentException(inputName + " input pointer must not be null");
        }

        byte[] result = new byte[size];
        for (int index = 0; index < size; index++) {
            result[index] = data.read(index);
        }
        return result;
    }

    private static void writeOutput(byte[] data, NativeBufferPointer output) {
        if (data.length == 0) {
            return;
        }

        CCharPointer nativeData = UnmanagedMemory.malloc(data.length);
        if (nativeData.isNull()) {
            throw new OutOfMemoryError("could not allocate native output buffer");
        }
        for (int index = 0; index < data.length; index++) {
            nativeData.write(index, data[index]);
        }
        output.setData(nativeData);
        output.setLength(WordFactory.unsigned(data.length));
    }

    private static void clearOutput(NativeBufferPointer output) {
        output.setData(WordFactory.nullPointer());
        output.setLength(WordFactory.zero());
    }
}
