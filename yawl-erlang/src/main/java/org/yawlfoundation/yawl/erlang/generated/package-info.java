/**
 * Hand-written Panama FFM bindings for {@code libei.so} — simulates jextract output
 * from OTP 28.3.1 {@code ei.h}. Gracefully degrades when the library is absent.
 *
 * <p>Set {@code -Derlang.library.path=/path/to/libei.so} to enable native operations.
 * Run {@code bash scripts/build-erlang.sh} to discover the path for OTP 28.3.1.</p>
 *
 * @see org.yawlfoundation.yawl.erlang.generated.ei_h
 */
package org.yawlfoundation.yawl.erlang.generated;
