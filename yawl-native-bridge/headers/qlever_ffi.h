/*
 * QLever FFI Interface for jextract generation
 * C++ interface for embedding QLever SPARQL engine
 */

#ifndef QLEVER_FFI_H
#define QLEVER_FFI_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// QLever result handle
typedef struct qlever_result_t qlever_result_t;

// Status codes
#define QLEVER_SUCCESS 0
#define QLEVER_PARSE_ERROR 1
#define QLEVER_SEMANTIC_ERROR 2
#define QLEVER_RUNTIME_ERROR 3

// Engine lifecycle
qlever_result_t* qlever_engine_create(void);
void qlever_engine_destroy(qlever_result_t* engine);

// Query operations
qlever_result_t* qlever_ask(qlever_result_t* engine, const char* sparql);
qlever_result_t* qlever_select(qlever_result_t* engine, const char* sparql);
qlever_result_t* qlever_construct(qlever_result_t* engine, const char* sparql);
qlever_result_t* qlever_update(qlever_result_t* engine, const char* turtle);

// Result access
const char* qlever_result_get_data(qlever_result_t* result);
void qlover_result_free(qlever_result_t* result);

// Status extraction
int qlever_result_get_code(qlever_result_t* result);
const char* qlever_result_get_message(qlever_result_t* result);

// Size constants
#define QLEVER_ENGINE_SIZE 184
#define QLEVER_RESULT_SIZE 64
#define QLEVER_STATUS_SIZE (sizeof(int) + sizeof(char*))

#ifdef __cplusplus
}
#endif

#endif // QLEVER_FFI_H