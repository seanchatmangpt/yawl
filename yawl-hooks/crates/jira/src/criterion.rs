use std::path::Path;

use crate::ticket::TicketFile;
use delta::types::Delta;

/// Check whether a write to `file_path` satisfies any acceptance criteria
/// in the active ticket. Returns the list of newly-satisfied criteria.
pub fn check_satisfaction(
    ticket: &mut TicketFile,
    file_path: &str,
    new_content: &str,
    deltas: &[Delta],
) -> Vec<String> {
    let mut newly_satisfied = Vec::new();

    let criteria: Vec<String> = ticket.ticket.acceptance_criteria.keys().cloned().collect();

    for ac in criteria {
        if ticket.ticket.acceptance_criteria.get(&ac) == Some(&true) {
            // Already satisfied
            continue;
        }

        if criterion_satisfied(&ac, file_path, new_content, deltas) {
            ticket.satisfy_criterion(&ac);
            newly_satisfied.push(ac);
        }
    }

    newly_satisfied
}

fn criterion_satisfied(ac: &str, file_path: &str, new_content: &str, deltas: &[Delta]) -> bool {
    let ac_lower = ac.to_lowercase();

    // 1. Criterion mentions a test name → check if file write contains that test
    if ac_lower.contains("test:") || ac_lower.contains("test_") {
        let test_name = extract_test_name(ac);
        if !test_name.is_empty() && new_content.contains(&test_name) {
            return true;
        }
    }

    // 2. Criterion mentions a function/method name → check deltas show it added/modified
    for delta in deltas {
        if let Delta::Declaration { name, change, .. } = delta {
            if ac_lower.contains(&name.to_lowercase()) {
                match change {
                    delta::types::Change::Added | delta::types::Change::Modified { .. } => {
                        return true;
                    }
                    delta::types::Change::Removed => {}
                }
            }
        }
    }

    // 3. Criterion text contains "¬X" pattern → check that pattern X is absent
    if ac.contains('¬') || ac.contains("not ") {
        let pattern = extract_negated_pattern(ac);
        if !pattern.is_empty() && !new_content.to_lowercase().contains(&pattern.to_lowercase()) {
            // Pattern is absent — criterion might be satisfied
            // But only if the file is relevant to the criterion
            if is_file_relevant(ac, file_path) {
                return true;
            }
        }
    }

    // 4. Criterion mentions a file path → check if that file is being written
    if is_file_relevant(ac, file_path) {
        // If the file is the right one, check if the content contains the expected artifact
        let keyword = extract_keyword(ac);
        if !keyword.is_empty() && new_content.to_lowercase().contains(&keyword.to_lowercase()) {
            return true;
        }
    }

    false
}

fn extract_test_name(ac: &str) -> String {
    // "test: some_test_name" → "some_test_name"
    if let Some(pos) = ac.find("test:") {
        return ac[pos + 5..].split_whitespace().next().unwrap_or("").to_string();
    }
    // "test_xxx" → extract from ac
    for word in ac.split_whitespace() {
        if word.starts_with("test_") {
            return word.to_string();
        }
    }
    String::new()
}

fn extract_negated_pattern(ac: &str) -> String {
    // "¬mock" → "mock"
    if let Some(pos) = ac.find('¬') {
        let after: String = ac[pos + '¬'.len_utf8()..]
            .chars()
            .take_while(|c| c.is_alphanumeric() || *c == '_')
            .collect();
        return after;
    }
    String::new()
}

fn extract_keyword(ac: &str) -> String {
    // Extract the most specific identifier-like token from the criterion
    let words: Vec<&str> = ac.split_whitespace().collect();
    // Prefer longer words that look like identifiers
    words
        .iter()
        .filter(|w| w.len() > 5 && w.chars().all(|c| c.is_alphanumeric() || c == '_' || c == '-'))
        .max_by_key(|w| w.len())
        .map(|s| s.to_string())
        .unwrap_or_default()
}

fn is_file_relevant(ac: &str, file_path: &str) -> bool {
    let file_name = Path::new(file_path)
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("");

    ac.contains(file_path) || ac.contains(file_name)
}
