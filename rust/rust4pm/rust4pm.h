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
    const char *object_id;
    const char *object_type;
} OcelObjectC;

typedef struct {
    const OcelObjectC *objects;
    size_t             count;
    char              *error;
} OcelObjectsResult;

typedef struct {
    char *json;
    char *error;
} DfgResultC;

typedef struct {
    double fitness;
    double precision;
    char  *error;
} ConformanceResultC;

/* Core log operations */
ParseResult        rust4pm_parse_ocel2_json(const char *json, size_t json_len);
size_t             rust4pm_log_event_count(OcelLogHandle handle);
OcelEventsResult   rust4pm_log_get_events(OcelLogHandle handle);
size_t             rust4pm_log_object_count(OcelLogHandle handle);
OcelObjectsResult  rust4pm_log_get_objects(OcelLogHandle handle);

/* Mining algorithms */
DfgResultC         rust4pm_discover_dfg(OcelLogHandle handle);
ConformanceResultC rust4pm_check_conformance(OcelLogHandle handle, const char *petri_net_pnml, size_t pnml_len);

/* Memory management */
void rust4pm_log_free(OcelLogHandle handle);
void rust4pm_events_free(OcelEventsResult result);
void rust4pm_objects_free(OcelObjectsResult result);
void rust4pm_dfg_free(DfgResultC result);
void rust4pm_error_free(char *error);

/*
 * Correct-by-construction sizeof probes.
 * Java Layer 1 calls these at class-init and asserts layout sizes match.
 * Any divergence between hand-written StructLayouts and actual Rust struct
 * sizes fails at JVM startup with an AssertionError, not silently at runtime.
 */
size_t rust4pm_sizeof_ocel_log_handle(void);
size_t rust4pm_sizeof_parse_result(void);
size_t rust4pm_sizeof_ocel_event_c(void);
size_t rust4pm_sizeof_ocel_events_result(void);
size_t rust4pm_sizeof_ocel_object_c(void);
size_t rust4pm_sizeof_ocel_objects_result(void);
size_t rust4pm_sizeof_dfg_result_c(void);
size_t rust4pm_sizeof_conformance_result_c(void);

#ifdef __cplusplus
}
#endif

#endif /* RUST4PM_H */
