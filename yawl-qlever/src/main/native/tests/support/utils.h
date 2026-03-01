/**
 * @file utils.h
 * @brief Utility functions for testing
 */

#pragma once

#include <string>

namespace qlever {
namespace test {

/**
 * @brief Generate a random string
 * @param length Length of the string to generate
 * @return Random string
 */
std::string generate_random_string(size_t length);

/**
 * @brief Generate a test document
 * @param id Document ID
 * @return Test document string
 */
std::string generate_test_document(size_t id);

/**
 * @brief Get current time in seconds
 * @return Current time as double (seconds since epoch)
 */
double get_current_time_seconds();

/**
 * @brief Format duration in human-readable format
 * @param seconds Duration in seconds
 * @return Formatted string (e.g., "1h 30m 45s")
 */
std::string format_duration(double seconds);

} // namespace test
} // namespace qlever