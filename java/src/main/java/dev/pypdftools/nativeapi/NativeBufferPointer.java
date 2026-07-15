package dev.pypdftools.nativeapi;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

/** Raw view of the two-field {@code pdftools_buffer_t} C structure. */
@RawStructure
public interface NativeBufferPointer extends PointerBase {
    @RawField
    CCharPointer getData();

    @RawField
    void setData(CCharPointer data);

    @RawField
    UnsignedWord getLength();

    @RawField
    void setLength(UnsignedWord length);
}
