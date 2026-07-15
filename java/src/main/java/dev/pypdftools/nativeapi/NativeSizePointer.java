package dev.pypdftools.nativeapi;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

/** Raw view of a pointer to one native {@code size_t} value. */
@RawStructure
public interface NativeSizePointer extends PointerBase {
    @RawField
    UnsignedWord getValue();

    @RawField
    void setValue(UnsignedWord value);
}
