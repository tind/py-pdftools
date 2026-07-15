#ifndef PY_PDFTOOLS_H
#define PY_PDFTOOLS_H

#include <stddef.h>
#include <stdint.h>

#include "graal_isolate.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef uint32_t pdftools_status_t;

typedef struct {
    void *data;
    size_t length;
} pdftools_buffer_t;

uint32_t pdftools_abi_version(graal_isolatethread_t *thread);

pdftools_status_t pdftools_inspect_pdf(
    graal_isolatethread_t *thread,
    const uint8_t *pdf_data,
    size_t pdf_length,
    pdftools_buffer_t *out_result
);

pdftools_status_t pdftools_add_ocr_text_layer(
    graal_isolatethread_t *thread,
    const uint8_t *pdf_data,
    size_t pdf_length,
    const uint8_t *request_data,
    size_t request_length,
    pdftools_buffer_t *out_pdf
);

void pdftools_free_buffer(
    graal_isolatethread_t *thread,
    void *buffer
);

pdftools_status_t pdftools_last_error(
    graal_isolatethread_t *thread,
    char *buffer,
    size_t capacity,
    size_t *required_size
);

#ifdef __cplusplus
}
#endif

#endif
