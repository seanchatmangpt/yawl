/**
 * @file utils.cpp
 * @brief Utility functions for testing
 */

#include "utils.h"
#include <chrono>
#include <random>
#include <sstream>
#include <iomanip>

namespace qlever {
namespace test {

std::string generate_random_string(size_t length) {
    const std::string chars =
        "abcdefghijklmnopqrstuvwxyz"
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        "0123456789"
        "!@#$%^&*()_+-=[]{}|;:,.<>?";

    std::random_device rd;
    std::mt19937 generator(rd());
    std::uniform_int_distribution<> distribution(0, chars.size() - 1);

    std::string result;
    result.reserve(length);

    for (size_t i = 0; i < length; ++i) {
        result += chars[distribution(generator)];
    }

    return result;
}

std::string generate_test_document(size_t id) {
    std::stringstream ss;
    ss << "Test document " << id << ": ";

    // Generate some realistic test content
    const std::vector<std::string> templates = {
        "This document contains information about topic {id} and various related subjects.",
        "Document {id} discusses key concepts in the field of computer science.",
        "The contents of document {id} include important data points and statistics.",
        "Document {id} provides insights into modern software development practices.",
        "This file contains test data for document {id} with multiple paragraphs."
    };

    size_t template_idx = id % templates.size();
    std::string content = templates[template_idx];

    // Replace {id} with actual ID
    size_t pos = content.find("{id}");
    if (pos != std::string::npos) {
        content.replace(pos, 4, std::to_string(id));
    }

    // Add some random text
    ss << content << " " << generate_random_string(50);

    return ss.str();
}

double get_current_time_seconds() {
    auto now = std::chrono::high_resolution_clock::now();
    auto duration = now.time_since_epoch();
    return std::chrono::duration<double>(duration).count();
}

std::string format_duration(double seconds) {
    auto total_ms = static_cast<int64_t>(seconds * 1000);

    auto hours = total_ms / 3600000;
    total_ms %= 3600000;

    auto minutes = total_ms / 60000;
    total_ms %= 60000;

    auto seconds_part = total_ms / 1000;
    auto ms = total_ms % 1000;

    std::stringstream ss;
    if (hours > 0) {
        ss << hours << "h ";
    }
    if (minutes > 0) {
        ss << minutes << "m ";
    }
    if (seconds_part > 0) {
        ss << seconds_part << "s ";
    }
    if (ms > 0 || (hours == 0 && minutes == 0 && seconds_part == 0)) {
        ss << ms << "ms";
    }

    return ss.str();
}

} // namespace test
} // namespace qlever