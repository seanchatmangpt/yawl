/*
 * rust4pm.h — C ABI for the rust4pm process mining library
 *
 * Hand-written to match lib.rs exports.
 * With cbindgen: cbindgen --crate rust4pm --output rust4pm.h
 * With jextract: see scripts/jextract-generate.sh
 */
#ifndef RUST4PM_H
#define RUST4PM_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct { void *ptr; } OcelLogHandle;

typedef struct {
    OcelLogHandle handle;
    char *error;
} ParseResult;

typedef struct {
    const char *event_id;
    const char *event_type;
    int64_t     timestamp_ms;
    size_t      attr_count;
} OcelEventC;

typedef struct {
    const OcelEventC *events;
    size_t            count;
    char             *error;
} OcelEventsResult;

typedef struct {
    char *json;
    char *error;
} DfgResultC;

typedef struct {
    double fitness;
    double precision;
    char  *error;
} ConformanceResultC;

ParseResult        rust4pm_parse_ocel2_json(const char *json, size_t json_len);
size_t             rust4pm_log_event_count(OcelLogHandle handle);
OcelEventsResult   rust4pm_log_get_events(OcelLogHandle handle);
DfgResultC         rust4pm_discover_dfg(OcelLogHandle handle);
ConformanceResultC rust4pm_check_conformance(OcelLogHandle handle, const char *petri_net_pnml, size_t pnml_len);
void               rust4pm_log_free(OcelLogHandle handle);
void               rust4pm_events_free(OcelEventsResult result);
void               rust4pm_dfg_free(DfgResultC result);
void               rust4pm_error_free(char *error);

#ifdef __cplusplus
}
#endif

#endif /* RUST4PM_H */
