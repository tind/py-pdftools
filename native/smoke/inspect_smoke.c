#include "pdftools.h"

#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#define PDF_CAPACITY 4096

static int append_bytes(
    uint8_t *buffer,
    size_t capacity,
    size_t *length,
    const void *value,
    size_t value_length
) {
    if (value_length > capacity - *length) {
        return 0;
    }
    memcpy(buffer + *length, value, value_length);
    *length += value_length;
    return 1;
}

static int append_format(
    uint8_t *buffer,
    size_t capacity,
    size_t *length,
    const char *format,
    ...
) {
    va_list arguments;
    va_start(arguments, format);
    int written = vsnprintf(
        (char *) buffer + *length,
        capacity - *length,
        format,
        arguments
    );
    va_end(arguments);
    if (written < 0 || (size_t) written >= capacity - *length) {
        return 0;
    }
    *length += (size_t) written;
    return 1;
}

static size_t build_pdf(uint8_t *buffer, size_t capacity) {
    static const uint8_t header[] = {
        '%', 'P', 'D', 'F', '-', '1', '.', '4', '\n',
        '%', 0xe2, 0xe3, 0xcf, 0xd3, '\n'
    };
    static const char *objects[] = {
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            "/CropBox [10 20 410 620] /Rotate 90 /Resources << >> "
            "/Contents 4 0 R >>",
        "<< /Length 0 >>\nstream\n\nendstream"
    };
    size_t length = 0;
    size_t offsets[sizeof(objects) / sizeof(objects[0])];
    size_t object_count = sizeof(objects) / sizeof(objects[0]);

    if (!append_bytes(buffer, capacity, &length, header, sizeof(header))) {
        return 0;
    }
    for (size_t index = 0; index < object_count; index++) {
        offsets[index] = length;
        if (!append_format(
                buffer,
                capacity,
                &length,
                "%zu 0 obj\n%s\nendobj\n",
                index + 1,
                objects[index])) {
            return 0;
        }
    }

    size_t xref_offset = length;
    if (!append_format(
            buffer,
            capacity,
            &length,
            "xref\n0 %zu\n0000000000 65535 f \n",
            object_count + 1)) {
        return 0;
    }
    for (size_t index = 0; index < object_count; index++) {
        if (!append_format(
                buffer,
                capacity,
                &length,
                "%010zu 00000 n \n",
                offsets[index])) {
            return 0;
        }
    }
    if (!append_format(
            buffer,
            capacity,
            &length,
            "trailer\n<< /Size %zu /Root 1 0 R >>\n"
                "startxref\n%zu\n%%%%EOF\n",
            object_count + 1,
            xref_offset)) {
        return 0;
    }
    return length;
}

static int contains_bytes(
    const void *data,
    size_t data_length,
    const char *expected
) {
    size_t expected_length = strlen(expected);
    if (expected_length > data_length) {
        return 0;
    }
    const uint8_t *bytes = data;
    for (size_t index = 0; index <= data_length - expected_length; index++) {
        if (memcmp(bytes + index, expected, expected_length) == 0) {
            return 1;
        }
    }
    return 0;
}

static void print_last_error(graal_isolatethread_t *thread) {
    char message[1024];
    size_t required_size = 0;
    if (pdftools_last_error(
            thread,
            message,
            sizeof(message),
            &required_size) == 0) {
        fprintf(stderr, "native error: %s\n", message);
    }
}

int main(void) {
    graal_isolate_t *isolate = NULL;
    graal_isolatethread_t *thread = NULL;
    if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
        fprintf(stderr, "could not create GraalVM isolate\n");
        return 1;
    }
    if (pdftools_abi_version(thread) != 1) {
        fprintf(stderr, "unexpected ABI version\n");
        return 2;
    }

    uint8_t pdf[PDF_CAPACITY];
    size_t pdf_length = build_pdf(pdf, sizeof(pdf));
    if (pdf_length == 0) {
        fprintf(stderr, "could not build smoke-test PDF\n");
        return 3;
    }

    pdftools_buffer_t output = {0};
    pdftools_status_t status = pdftools_inspect_pdf(
        thread,
        pdf,
        pdf_length,
        &output
    );
    if (status != 0) {
        print_last_error(thread);
        return 4;
    }
    int valid_result =
        output.data != NULL &&
        contains_bytes(output.data, output.length, "\"pageCount\":1") &&
        contains_bytes(output.data, output.length, "\"widthPoints\":600.0") &&
        contains_bytes(output.data, output.length, "\"rotation\":90");
    pdftools_free_buffer(thread, output.data);
    if (!valid_result) {
        fprintf(stderr, "inspection result did not contain expected geometry\n");
        return 5;
    }

    static const uint8_t invalid_pdf[] = "not a PDF";
    output.data = NULL;
    output.length = 0;
    status = pdftools_inspect_pdf(
        thread,
        invalid_pdf,
        sizeof(invalid_pdf) - 1,
        &output
    );
    if (status != 1 || output.data != NULL || output.length != 0) {
        fprintf(stderr, "malformed PDF did not return invalid-PDF status\n");
        return 6;
    }

    static const uint8_t ocr_request[] =
        "{\"schemaVersion\":1,\"options\":{"
        "\"allowPartialDocument\":false,"
        "\"minimumConfidence\":null,"
        "\"debugVisibleText\":false},"
        "\"pages\":[{\"pageIndex\":0,\"orientation\":0,\"lines\":[{"
        "\"text\":\"Native OCR\",\"confidence\":99.0,\"bounds\":{"
        "\"left\":0.1,\"top\":0.2,\"width\":0.5,\"height\":0.08}}]}]}";
    output.data = NULL;
    output.length = 0;
    status = pdftools_add_ocr_text_layer(
        thread,
        pdf,
        pdf_length,
        ocr_request,
        sizeof(ocr_request) - 1,
        &output
    );
    if (status != 0 || output.data == NULL || output.length <= pdf_length) {
        print_last_error(thread);
        fprintf(stderr, "OCR transformation did not return a PDF\n");
        return 7;
    }
    void *transformed_pdf = output.data;
    size_t transformed_length = output.length;

    output.data = NULL;
    output.length = 0;
    status = pdftools_inspect_pdf(
        thread,
        transformed_pdf,
        transformed_length,
        &output
    );
    int transformed_is_valid =
        status == 0 &&
        output.data != NULL &&
        contains_bytes(output.data, output.length, "\"pageCount\":1");
    pdftools_free_buffer(thread, output.data);
    pdftools_free_buffer(thread, transformed_pdf);
    if (!transformed_is_valid) {
        print_last_error(thread);
        fprintf(stderr, "transformed output could not be inspected\n");
        return 8;
    }

    static const uint8_t invalid_request[] = "{}";
    output.data = NULL;
    output.length = 0;
    status = pdftools_add_ocr_text_layer(
        thread,
        pdf,
        pdf_length,
        invalid_request,
        sizeof(invalid_request) - 1,
        &output
    );
    if (status != 2 || output.data != NULL || output.length != 0) {
        fprintf(stderr, "malformed OCR request did not return invalid-data status\n");
        return 9;
    }

    if (graal_tear_down_isolate(thread) != 0) {
        fprintf(stderr, "could not tear down GraalVM isolate\n");
        return 10;
    }
    puts("native ABI smoke test passed");
    return 0;
}
