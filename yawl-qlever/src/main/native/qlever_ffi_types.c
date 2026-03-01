/**
 * @file qlever_ffi_types.c
 * @brief Implementation of FFI type helper functions
 */

#include "qlever_ffi_types.h"
#include <string.h>

/**
 * @brief Media type to string mapping
 */
static const struct {
    QleverMediaType type;
    const char* string;
} media_type_mappings[] = {
    {QLEVER_MEDIA_TYPE_UNKNOWN, "unknown"},
    {QLEVER_MEDIA_TYPE_JSON, "application/json"},
    {QLEVER_MEDIA_TYPE_XML, "application/xml"},
    {QLEVER_MEDIA_TYPE_TEXT, "text/plain"},
    {QLEVER_MEDIA_TYPE_BINARY, "application/octet-stream"},
    {QLEVER_MEDIA_TYPE_HTML, "text/html"},
    {QLEVER_MEDIA_TYPE_CSV, "text/csv"},
    {QLEVER_MEDIA_TYPE_TSV, "text/tab-separated-values"},
    {QLEVER_MEDIA_TYPE_QLEARQL, "application/x-ql-qlearql"},
    {QLEVER_MEDIA_TYPE_SPARQL_JSON, "application/sparql-results+json"},
    {QLEVER_MEDIA_TYPE_SPARQL_XML, "application/sparql-results+xml"},
    {QLEVER_MEDIA_TYPE_RDF_XML, "application/rdf+xml"},
    {QLEVER_MEDIA_TYPE_RDF_TURTLE, "text/turtle"},
    {QLEVER_MEDIA_TYPE_RDF_NTRIPLES, "application/n-triples"}
};

/**
 * @brief Convert media type to string
 *
 * @param media_type The media type to convert
 * @return const char* String representation of the media type
 */
const char* qlever_media_type_to_string(QleverMediaType media_type) {
    for (size_t i = 0; i < sizeof(media_type_mappings) / sizeof(media_type_mappings[0]); i++) {
        if (media_type_mappings[i].type == media_type) {
            return media_type_mappings[i].string;
        }
    }
    return "unknown";
}

/**
 * @brief Convert string to media type
 *
 * @param str String representation of media type
 * @return QleverMediaType Corresponding media type
 */
QleverMediaType qlever_media_type_from_string(const char* str) {
    if (!str) return QLEVER_MEDIA_TYPE_UNKNOWN;

    for (size_t i = 0; i < sizeof(media_type_mappings) / sizeof(media_type_mappings[0]); i++) {
        if (strcmp(media_type_mappings[i].string, str) == 0) {
            return media_type_mappings[i].type;
        }
    }
    return QLEVER_MEDIA_TYPE_UNKNOWN;
}