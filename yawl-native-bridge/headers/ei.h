/*
 * Erlang Interface (ei.h) for jextract generation
 * Subset of the erl_interface library for Unix domain socket connections
 */

#ifndef EI_H
#define EI_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Constants
#define ERL_MSG 1
#define ERL_TICK 2
#define ERL_ERROR -1
#define EI_CNODE_SIZE 80
#define ERLANG_MSG_SIZE (4*4 + 2*4 + 8)
#define EI_X_BUFF_SIZE (1024 * 1024)

// Enum constants for Erlang term types
typedef enum {
    ERL_SMALL_INTEGER = 97,
    ERL_INTEGER = 98,
    ERL_ATOM = 100,
    ERL_REFERENCE = 101,
    ERL_PORT = 102,
    ERL_PID = 103,
    ERL_SMALL_TUPLE = 104,
    ERL_LARGE_TUPLE = 106,
    ERL_NIL = 106,
    ERL_STRING = 107,
    ERL_LIST = 108,
    ERL_BINARY = 109,
    ERL_BIT_BINARY = 110,
    ERL_FLOAT = 70,
    ERL_NEW_FLOAT = 70
} ei_x_type_t;

// ei_cnode_t structure
typedef struct {
    uint16_t creation;
    uint8_t hidden;
    uint16_t alive;
    uint8_t* port;
    uint8_t* hostent;
    uint8_t* distribution;
    uint8_t* status;
} ei_cnode_t;

// ei_x_buff_t structure
typedef struct {
    uint32_t index;
    uint32_t size;
    uint8_t* buff;
} ei_x_buff_t;

// Function declarations with jextract annotations

/**
 * Initialize Erlang connection node
 * @param cnode Pointer to ei_cnode_t structure
 * @param nodename Node name
 * @param cookie Connection cookie
 * @param creation Creation number
 * @return 0 on success, -1 on failure
 */
int ei_connect_init(ei_cnode_t* cnode, const char* nodename, const char* cookie, short creation);

/**
 * Connect to an Erlang node
 * @param cnode Pointer to initialized ei_cnode_t
 * @param nodename Target node name
 * @return Connection descriptor on success, -1 on failure
 */
int ei_connect(ei_cnode_t* cnode, const char* nodename);

/**
 * Connect to Erlang node by host and port
 * @param cnode Pointer to initialized ei_cnode_t
 * @param host Hostname or IP
 * @param port Port number
 * @return Connection descriptor on success, -1 on failure
 */
int ei_connect_host_port(ei_cnode_t* cnode, const char* host, int port);

/**
 * Create new ei_x_buff
 * @param size Buffer size
 * @return Pointer to new ei_x_buff on success, NULL on failure
 */
ei_x_buff_t* ei_x_new(int size);

/**
 * Free ei_x_buff
 * @param x Pointer to ei_x_buff
 */
void ei_x_free(ei_x_buff_t* x);

/**
 * Initialize ei_x_buff
 * @param x Pointer to ei_x_buff
 * @param size Buffer size
 * @return 0 on success, -1 on failure
 */
int ei_x_new_with_size(ei_x_buff_t* x, int size);

/**
 * Reset ei_x_buff
 * @param x Pointer to ei_x_buff
 */
void ei_x_clear(ei_x_buff_t* x);

/**
 * Encode Erlang atom
 * @param x Pointer to ei_x_buff
 * @param atom Atom string
 * @return 0 on success, -1 on failure
 */
int ei_x_encode_atom(ei_x_buff_t* x, const char* atom);

/**
 * Encode Erlang long integer
 * @param x Pointer to ei_x_buff
 * @param l Long integer value
 * @return 0 on success, -1 on failure
 */
int ei_x_encode_long(ei_x_buff_t* x, long l);

/**
 * Encode Erlang integer
 * @param x Pointer to ei_x_buff
 * @param i Integer value
 * @return 0 on success, -1 on failure
 */
int ei_x_encode_int(ei_x_buff_t* x, int i);

/**
 * Encode Erlang string
 * @param x Pointer to ei_x_buff
 * @param s String to encode
 * @return 0 on success, -1 on failure
 */
int ei_x_encode_string(ei_x_buff_t* x, const char* s);

/**
 * Encode Erlang binary
 * @param x Pointer to ei_x_buff
 * @param p Binary data
 * @param len Binary length
 * @return 0 on success, -1 on failure
 */
int ei_x_encode_binary(ei_x_buff_t* x, const void* p, size_t len);

/**
 * Encode Erlang list
 * @param x Pointer to ei_x_buff
 * @param list Array of Erlang terms
 * @param len List length
 * @return 0 on success, -1 on failure
 */
int ei_x_encode_list(ei_x_buff_t* x, const char** list, int len);

/**
 * Start tuple encoding
 * @param x Pointer to ei_x_buff
 * @param arity Tuple arity
 * @return 0 on success, -1 on failure
 */
int ei_x_encode_tuple_header(ei_x_buff_t* x, int arity);

/**
 * End tuple encoding
 * @param x Pointer to ei_x_buff
 * @return 0 on success, -1 on failure
 */
int ei_x_encode_tuple_end(ei_x_buff_t* x);

/**
 * Execute RPC call
 * @param cnode Pointer to ei_cnode_t
 * @param fd Connection descriptor
 * @param mod Module name
 * @param fun Function name
 * @param arg Argument string
 * @param arg_len Argument length
 * @param result Pointer to result buffer
 * @return 0 on success, -1 on failure
 */
int ei_rpc(ei_cnode_t* cnode, int fd, const char* mod, const char* fun,
           const char* arg, int arg_len, ei_x_buff_t* result);

/**
 * Execute RPC call with timeout
 * @param cnode Pointer to ei_cnode_t
 * @param fd Connection descriptor
 * @param mod Module name
 * @param fun Function name
 * @param arg Argument string
 * @param arg_len Argument length
 * @param result Pointer to result buffer
 * @param timeout_ms Timeout in milliseconds
 * @return 0 on success, -1 on failure
 */
int ei_rpc_timeout(ei_cnode_t* cnode, int fd, const char* mod, const char* fun,
                   const char* arg, int arg_len, ei_x_buff_t* result, int timeout_ms);

/**
 * Parse Erlang term from buffer
 * @param x Pointer to ei_x_buff
 * @param term_type Pointer to store term type
 * @param index Pointer to current index in buffer
 * @return 0 on success, -1 on failure
 */
int ei_decode_ei_term(ei_x_buff_t* x, int* term_type, int* index);

/**
 * Decode Erlang atom
 * @param x Pointer to ei_x_buff
 * @param term_type Term type (should be ERL_ATOM)
 * @param index Pointer to current index
 * @param atom Pointer to store atom string
 * @return 0 on success, -1 on failure
 */
int ei_decode_atom(ei_x_buff_t* x, int* term_type, int* index, char* atom);

/**
 * Decode Erlang integer
 * @param x Pointer to ei_x_buff
 * @param term_type Term type
 * @param index Pointer to current index
 * @param ip Pointer to store integer value
 * @return 0 on success, -1 on failure
 */
int ei_decode_int(ei_x_buff_t* x, int* term_type, int* index, int* ip);

/**
 * Decode Erlang long
 * @param x Pointer to ei_x_buff
 * @param term_type Term type
 * @param index Pointer to current index
 * @param lp Pointer to store long value
 * @return 0 on success, -1 on failure
 */
int ei_decode_long(ei_x_buff_t* x, int* term_type, int* index, long* lp);

/**
 * Decode Erlang string
 * @param x Pointer to ei_x_buff
 * @param term_type Term type (should be ERL_STRING)
 * @param index Pointer to current index
 * @param s Pointer to store string
 * @param len Pointer to store string length
 * @return 0 on success, -1 on failure
 */
int ei_decode_string(ei_x_buff_t* x, int* term_type, int* index, char* s, int* len);

/**
 * Decode Erlang binary
 * @param x Pointer to ei_x_buff
 * @param term_type Term type (should be ERL_BINARY)
 * @param index Pointer to current index
 * @param bin Pointer to store binary data
 * @param len Pointer to store binary length
 * @return 0 on success, -1 on failure
 */
int ei_decode_binary(ei_x_buff_t* x, int* term_type, int* index, char** bin, int* len);

/**
 * Check if buffer has more terms
 * @param x Pointer to ei_x_buff
 * @param index Current index
 * @return 1 if more terms, 0 if no more
 */
int ei_more(ei_x_buff_t* x, int* index);

/**
 * Close connection
 * @param fd Connection descriptor
 */
void ei_close_connection(int fd);

#ifdef __cplusplus
}
#endif

#endif /* EI_H */